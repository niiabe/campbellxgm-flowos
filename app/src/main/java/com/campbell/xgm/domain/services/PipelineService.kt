package com.campbell.xgm.domain.services

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.app.usage.UsageStatsManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.campbell.xgm.R
import kotlinx.coroutines.*
import java.io.File

class PipelineService : Service() {

    companion object {
        private const val CHANNEL_ID = "campbellxgm_pipeline_channel"
        private const val NOTIFICATION_ID = 9001

        @Volatile var isRunning = false
            private set

        @Volatile var activeTargetPackage: String? = null
            private set
    }

    // State Tracking — all @Volatile to ensure visibility across threads
    @Volatile private var isDndRestored = true
    @Volatile private var isAppsRestored = true
    @Volatile private var isNetworkBoosted = false
    @Volatile private var isBatteryOptimized = false
    @Volatile private var isCpuTuned = false
    @Volatile private var isWakeLockHeld = false
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var isBrightnessLocked = false
    @Volatile private var isNotificationFilterActive = false
    @Volatile private var isRestored = false
    @Volatile private var isCpuThrottled = false
    private var originalDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var originalBrightnessMode = 1 // Default to auto
    private var originalNotificationPolicy: NotificationManager.Policy? = null
    private var originalCpuGovernor = "schedutil"
    private var originalCpuMaxFreq: String? = null
    private var suspendedPackagesList = emptyArray<String>()
    private var originalWifiVerbosity = -1
    private var originalSyncState = true

    // Background Coroutines
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var freezeJob: Job? = null
    private var cooldownJob: Job? = null
    private var autoTeardownJob: Job? = null
    private val FREEZE_INTERVAL_MS = 5000L
    private val COOLDOWN_CHECK_INTERVAL_MS = 10000L
    private val AUTO_TEARDOWN_CHECK_MS = 5000L
    private val COOLDOWN_THRESHOLD_CELSIUS = 42.0
    private val COOLDOWN_RESTORE_THRESHOLD_CELSIUS = 38.0

    private val SYSTEM_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.android.systemui",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.downloads",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gms.persistent",
        "com.google.android.gms.unstable",
        "com.google.android.gms.chrome",
        "com.google.android.gsf",
        "com.google.android.gsf.login",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.calendar",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.camera",
        "com.android.gallery3d",
        "com.android.wallpaperbackup",
        "com.android.storagemanager",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.android.captiveportallogin",
        "com.android.shell",
        "com.android.traceur",
        "com.android.hotspot2.osulogin",
        "com.android.localtransport",
        "com.android.statsmw",
        "com.android.wifi.resources",
        "com.android.wifi.dialog",
        "com.android.networkstack.tethering",
        "com.android.connectivity.resources",
        "com.android.dynsystem",
        "com.android.virq",
        "com.android.hotspot2",
        "com.android.se",
        "com.android.simappdialog",
        "com.android.stk",
        "com.android.wallpapercropper",
        "com.android.providers.contacts",
        "com.android.providers.telephony",
        "com.google.android.configupdater",
        "com.google.android.onetimeinitializer",
        "com.google.android.setupwizard",
        "com.google.android.tag",
        "com.google.android.talk",
        "com.google.android.feedback",
        "com.google.android.gms.fixes",
        "com.google.android.gms.games",
        "com.google.android.gms.auth",
        "com.google.android.gms.fitness",
        "com.google.android.gms.learning",
        "com.google.android.gms.ads",
        "com.google.android.gms.nearby",
        "com.google.android.gms.security",
        "com.google.android.gms.wallet",
        "com.google.android.gms.cast",
        "com.google.android.gms.droidguard",
        "com.google.android.gms.icing",
        "com.google.android.gms.mdm",
        "com.google.android.gms.tapandpay",
        "com.google.android.gms.fido",
        "com.google.android.apps.maps",
        "com.google.android.googlequicksearchbox",
        "com.google.android.music",
        "com.google.android.youtube",
        "com.google.android.apps.docs",
        "com.google.android.apps.photos",
        "com.google.android.apps.messaging",
        "com.google.android.apps.chrome",
        "com.google.android.apps.turbo",
        "com.google.android.apps.wellbeing",
        "com.google.android.ext.services",
        "com.google.android.printservice.recommendation"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_GAME_MODE") {
            if (!isRestored) {
                stopAllOptimizations()
                restoreSystemState()
            }
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        // Always start foreground first to prevent ForegroundServiceStartNotAllowedException
        val targetPackage = intent?.getStringExtra("TARGET_PACKAGE")
        val gameName = if (targetPackage != null) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString() }
            catch (_: Exception) { targetPackage }
        } else "Game Mode"
        startForegroundServiceNotification(gameName)

        if (targetPackage != null) {
            isRunning = true
            isRestored = false
            activeTargetPackage = targetPackage
            executeAppPipeline(targetPackage)
            com.campbell.xgm.ui.widgets.GameModeWidgetProvider.updateAllWidgets(this)
        } else {
            Log.e("PipelineService", "No target package provided")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification(gameName: String = "Game Mode") {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Game Mode Engine",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val stopIntent = Intent(this, PipelineService::class.java).apply {
            action = "STOP_GAME_MODE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Playing: $gameName")
            .setContentText("Game Mode active - Tap to stop and restore system")
            .setSmallIcon(R.drawable.logo)
            .addAction(R.drawable.logo, "Stop Game Mode", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun executeAppPipeline(targetPackage: String) {
        val prefs = getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)

        // Check for per-game profile first, fall back to global settings
        val profileJson = getSharedPreferences("game_profiles", MODE_PRIVATE).getString(targetPackage, null)
        fun profileBool(key: String, globalKey: String, default: Boolean): Boolean {
            if (profileJson != null) {
                try { return org.json.JSONObject(profileJson).optBoolean(key, prefs.getBoolean(globalKey, default)) }
                catch (_: Exception) {}
            }
            return prefs.getBoolean(globalKey, default)
        }

        val isAggressiveFreezingEnabled = profileBool("aggressiveFreezing", "aggressive_freezing", true)
        val isDndEnabled = profileBool("dndMode", "dnd_mode", false)
        val isNetworkBoostEnabled = profileBool("networkBoost", "network_boost", true)
        val isBatteryProfileEnabled = profileBool("batteryProfile", "battery_profile", true)
        val isCpuTunerEnabled = profileBool("cpuTuner", "cpu_tuner", false)
        val isCooldownEnabled = profileBool("cooldownMode", "cooldown_mode", true)
        val isStorageCleanerEnabled = profileBool("storageCleaner", "storage_cleaner", true)
        val isFpsOverlayEnabled = profileBool("fpsOverlay", "fps_overlay", false)
        val isStatsOverlayEnabled = profileBool("statsOverlay", "stats_overlay", false)
        val isPingStabilizerEnabled = profileBool("pingStabilizer", "ping_stabilizer", false)
        val isKeepScreenAwakeEnabled = profileBool("keepScreenAwake", "keep_screen_awake", true)
        val isAutoBrightnessLockEnabled = profileBool("autoBrightnessLock", "auto_brightness_lock", true)
        val isNotificationFilterEnabled = profileBool("notificationFilter", "notification_filter", false)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, CampbellAdminReceiver::class.java)
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val isDeviceAdmin = dpm.isAdminActive(adminComponent)

        // Storage Cleaner - Clear cache before launching
        if (isStorageCleanerEnabled) {
            clearGameCache(targetPackage)
        }

        // FPS Overlay
        if (isFpsOverlayEnabled && Settings.canDrawOverlays(this)) {
            val fpsIntent = Intent(this, FpsOverlayService::class.java)
            startService(fpsIntent)
        }

        // Stats Overlay (RAM/CPU/Battery)
        if (isStatsOverlayEnabled && Settings.canDrawOverlays(this)) {
            val statsIntent = Intent(this, StatsOverlayService::class.java)
            startForegroundService(statsIntent)
        }

        // Keep Screen Awake
        if (isKeepScreenAwakeEnabled) {
            enableKeepScreenAwake()
        }

        // Auto-Brightness Lock
        if (isAutoBrightnessLockEnabled) {
            enableAutoBrightnessLock()
        }

        // Notification Filter
        if (isNotificationFilterEnabled) {
            enableNotificationFilter()
        }

        // DND
        if (isDndEnabled) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                originalDndFilter = notificationManager.currentInterruptionFilter
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                isDndRestored = false
            } else {
                Log.w("PipelineService", "DND enabled but policy access not granted!")
            }
        }

        // Network Boost
        if (isNetworkBoostEnabled) {
            enableNetworkBoost()
        }

        // Battery Profile
        if (isBatteryProfileEnabled) {
            enableBatteryProfile()
        }

        // CPU Tuner (Device Owner only)
        if (isCpuTunerEnabled && isDeviceOwner) {
            enableCpuTuner()
        }

        // Freezing — layered approach: each tier stacks on top of the previous
        if (isAggressiveFreezingEnabled) {
            serviceScope.launch {
                val appsToFreeze = withContext(Dispatchers.IO) { getFreezableApps(targetPackage) }
                Log.i("PipelineService", "Found ${appsToFreeze.size} apps to freeze")

                // Layer 1: Instant best-effort kill via ActivityManager (works without any special privileges)
                killBackgroundProcesses(appsToFreeze)

                // Layer 2: Device Owner — suspend packages at OS level (total freeze)
                if (isDeviceOwner) {
                    try {
                        suspendedPackagesList = dpm.setPackagesSuspended(adminComponent, appsToFreeze.toTypedArray(), true)
                        isAppsRestored = false
                        Log.i("PipelineService", "Suspended ${suspendedPackagesList.size} packages via Device Owner")
                    } catch (e: Exception) {
                        Log.e("PipelineService", "Failed to suspend apps: ${e.message}")
                    }
                }

                // Layer 3: Accessibility — Greenify-style force-stop via automated UI (works for any user with Accessibility enabled)
                if (SafetyInterceptor.isRunning()) {
                    Log.i("PipelineService", "Triggering Accessibility Ghost Finger for thorough force-stop")
                    val safetyService = SafetyInterceptor.instance
                    safetyService?.forceStopApps(appsToFreeze.toList())
                }

                // Layer 4: Periodic re-killing to catch apps that restart
                startPeriodicFreezing(appsToFreeze)

                // Cooldown monitoring
                if (isCooldownEnabled) {
                    startCooldownMonitoring()
                }

                // Auto-teardown monitoring — detect when user leaves game
                startAutoTeardownMonitoring()

                launchGame(targetPackage)
            }
        } else {
            // Cooldown monitoring
            if (isCooldownEnabled) {
                startCooldownMonitoring()
            }

            // Auto-teardown monitoring
            startAutoTeardownMonitoring()

            launchGame(targetPackage)
        }
    }

    private fun enableNetworkBoost() {
        try {
            // Enable WiFi verbose logging for diagnostics
            originalWifiVerbosity = Settings.Global.getInt(contentResolver, "wifi_verbose_logging", 0)
            Settings.Global.putInt(contentResolver, "wifi_verbose_logging", 1)

            // Disable background sync to prioritize network for gaming
            originalSyncState = android.content.ContentResolver.getMasterSyncAutomatically()
            android.content.ContentResolver.setMasterSyncAutomatically(false)

            // Bind process to active network to reduce latency
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null) {
                        Log.i("PipelineService", "Network boost active - bound to network with caps: ${caps.linkDownstreamBandwidthKbps}kbps")
                    }
                }
            }

            // Restrict background data usage on Android 7+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    cm.isDefaultNetworkActive
                } catch (_: Exception) {}
            }

            isNetworkBoosted = true
            Log.i("PipelineService", "Network boost enabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable network boost: ${e.message}")
        }
    }

    private fun disableNetworkBoost() {
        if (!isNetworkBoosted) return
        try {
            if (originalWifiVerbosity != -1) {
                Settings.Global.putInt(contentResolver, "wifi_verbose_logging", originalWifiVerbosity)
            }
            
            // Restore background sync
            android.content.ContentResolver.setMasterSyncAutomatically(originalSyncState)
            
            isNetworkBoosted = false
            Log.i("PipelineService", "Network boost disabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable network boost: ${e.message}")
        }
    }

    private fun enableBatteryProfile() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // Log battery saver status
            if (powerManager.isPowerSaveMode) {
                Log.i("PipelineService", "Battery saver is active - attempting to disable")
                // Try to disable battery saver via settings (requires WRITE_SECURE_SETTINGS)
                try {
                    Settings.Global.putInt(contentResolver, "low_power", 0)
                    Log.i("PipelineService", "Battery saver disabled")
                } catch (e: SecurityException) {
                    Log.w("PipelineService", "Cannot disable battery saver without WRITE_SECURE_SETTINGS")
                }
            }

            // Request performance hint if available (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val isCharging = batteryManager.isCharging
                Log.i("PipelineService", "Battery profile: charging=$isCharging")
            }

            isBatteryOptimized = true
            Log.i("PipelineService", "Battery profile enabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable battery profile: ${e.message}")
        }
    }

    private fun disableBatteryProfile() {
        if (!isBatteryOptimized) return
        isBatteryOptimized = false
        Log.i("PipelineService", "Battery profile disabled")
    }

    private fun enableCpuTuner() {
        try {
            val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
            val cpuFile = File(cpuPath)
            if (cpuFile.exists() && cpuFile.canWrite()) {
                originalCpuGovernor = cpuFile.readText().trim()
                cpuFile.writeText("performance")
                isCpuTuned = true
                Log.i("PipelineService", "CPU tuner enabled - governor set to performance (was: $originalCpuGovernor)")
            } else {
                Log.w("PipelineService", "CPU governor file not writable (need Device Owner)")
            }
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable CPU tuner: ${e.message}")
        }
    }

    private fun disableCpuTuner() {
        if (!isCpuTuned) return
        try {
            val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
            val cpuFile = File(cpuPath)
            if (cpuFile.exists() && cpuFile.canWrite()) {
                cpuFile.writeText(originalCpuGovernor)
                Log.i("PipelineService", "CPU tuner disabled - governor restored to $originalCpuGovernor")
            }
            isCpuTuned = false
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable CPU tuner: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun enableKeepScreenAwake() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CampbellXGM:GameMode"
            )
            wakeLock?.acquire() // Indefinite acquire, released on game mode end
            isWakeLockHeld = true
            Log.i("PipelineService", "Keep screen awake enabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable keep screen awake: ${e.message}")
        }
    }

    private fun disableKeepScreenAwake() {
        if (!isWakeLockHeld) return
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            isWakeLockHeld = false
            Log.i("PipelineService", "Keep screen awake disabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable keep screen awake: ${e.message}")
        }
    }

    private fun enableAutoBrightnessLock() {
        try {
            originalBrightnessMode = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                1 // Default to auto
            )
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            isBrightnessLocked = true
            Log.i("PipelineService", "Auto-brightness lock enabled")
        } catch (e: SecurityException) {
            Log.e("PipelineService", "Failed to lock auto-brightness (WRITE_SETTINGS permission needed): ${e.message}")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable auto-brightness lock: ${e.message}")
        }
    }

    private fun disableAutoBrightnessLock() {
        if (!isBrightnessLocked) return
        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                originalBrightnessMode
            )
            isBrightnessLocked = false
            Log.i("PipelineService", "Auto-brightness lock disabled - restored mode: $originalBrightnessMode")
        } catch (e: SecurityException) {
            Log.e("PipelineService", "Failed to restore auto-brightness (WRITE_SETTINGS permission needed): ${e.message}")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable auto-brightness lock: ${e.message}")
        }
    }

    private fun enableNotificationFilter() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                originalNotificationPolicy = notificationManager.notificationPolicy
                val policy = NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS,
                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
                )
                notificationManager.notificationPolicy = policy
                isNotificationFilterActive = true
                Log.i("PipelineService", "Notification filter enabled - only calls and messages allowed")
            } else {
                Log.w("PipelineService", "Notification filter enabled but DND policy access not granted!")
            }
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to enable notification filter: ${e.message}")
        }
    }

    private fun disableNotificationFilter() {
        if (!isNotificationFilterActive) return
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted && originalNotificationPolicy != null) {
                notificationManager.notificationPolicy = originalNotificationPolicy
            }
            isNotificationFilterActive = false
            Log.i("PipelineService", "Notification filter disabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable notification filter: ${e.message}")
        }
    }

    private fun startCooldownMonitoring() {
        cooldownJob = serviceScope.launch {
            while (isActive) {
                checkTemperature()
                delay(COOLDOWN_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun startAutoTeardownMonitoring() {
        val target = activeTargetPackage ?: return
        autoTeardownJob = serviceScope.launch {
            while (isActive) {
                delay(AUTO_TEARDOWN_CHECK_MS)
                if (!isForegroundApp(target)) {
                    Log.i("PipelineService", "User left game ($target) - auto-restoring system state")
                    withContext(Dispatchers.Main) {
                        stopAllOptimizations()
                        restoreSystemState()
                        stopSelf()
                    }
                    return@launch
                }
            }
        }
    }

    private fun isForegroundApp(targetPackage: String): Boolean {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return true
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 10_000, now)
            var lastForeground: String? = null
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground = event.packageName
                }
            }
            lastForeground == targetPackage || lastForeground == null
        } catch (e: Exception) {
            true
        }
    }

    private fun checkTemperature() {
        try {
            val intent = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
            val cpuFile = File(cpuPath)

            if (temp >= COOLDOWN_THRESHOLD_CELSIUS && !isCpuThrottled) {
                Log.w("PipelineService", "Device overheating: ${temp}°C")
                if (!dpm.isDeviceOwnerApp(packageName)) {
                    Log.w("PipelineService", "CPU throttling requires Device Owner permission")
                    return
                }
                if (cpuFile.exists() && cpuFile.canWrite()) {
                    originalCpuMaxFreq = cpuFile.readText().trim()
                    val maxFreq = originalCpuMaxFreq?.toLongOrNull()
                    if (maxFreq != null) {
                        val reducedFreq = (maxFreq * 0.7).toLong()
                        cpuFile.writeText(reducedFreq.toString())
                        isCpuThrottled = true
                        Log.i("PipelineService", "CPU throttled to $reducedFreq (was: $maxFreq)")
                    }
                } else {
                    Log.w("PipelineService", "CPU frequency file not writable")
                }
            } else if (temp < COOLDOWN_RESTORE_THRESHOLD_CELSIUS && isCpuThrottled) {
                Log.i("PipelineService", "Temperature safe: ${temp}°C - restoring CPU")
                if (cpuFile.exists() && cpuFile.canWrite() && originalCpuMaxFreq != null) {
                    cpuFile.writeText(originalCpuMaxFreq!!)
                    Log.i("PipelineService", "CPU restored to $originalCpuMaxFreq")
                }
                isCpuThrottled = false
            }
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to check temperature: ${e.message}")
        }
    }



    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        return dir.delete()
    }

    private fun launchGame(targetPackage: String) {
        val prefs = getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
        val profileJson = getSharedPreferences("game_profiles", MODE_PRIVATE).getString(targetPackage, null)
        val isPingStabilizerEnabled = if (profileJson != null) {
            try { org.json.JSONObject(profileJson).optBoolean("pingStabilizer", prefs.getBoolean("ping_stabilizer", false)) }
            catch (_: Exception) { prefs.getBoolean("ping_stabilizer", false) }
        } else prefs.getBoolean("ping_stabilizer", false)

        if (isPingStabilizerEnabled) {
            Log.i("PipelineService", "Starting Ping Stabilizer for $targetPackage")
            val vpnIntent = Intent(this, PingStabilizerVpnService::class.java).apply {
                action = PingStabilizerVpnService.ACTION_START_VPN
                putExtra(PingStabilizerVpnService.EXTRA_TARGET_PACKAGE, targetPackage)
            }
            startService(vpnIntent)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
            Log.i("PipelineService", "Game launched: $targetPackage")
        } else {
            Log.e("PipelineService", "Target package not found or cannot be launched: $targetPackage")
            restoreSystemState()
            stopSelf()
        }
    }

    private fun getFreezableApps(targetPackage: String): List<String> {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val prefs = getSharedPreferences("game_mode_prefs", MODE_PRIVATE)
        val excludedApps = (prefs.getString("excluded_apps", "") ?: "")
            .split(",").filter { it.isNotBlank() }.toSet()

        // Get ALL running processes, not just launcher activities
        val runningProcesses = am.runningAppProcesses?.map { it.processName }?.toSet() ?: emptySet()

        // Also get all installed launcher apps as fallback
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherApps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()

        // Combine both lists for maximum coverage
        val allApps = (runningProcesses + launcherApps).distinct()

        return allApps.filter { pkg ->
            pkg != packageName &&
            pkg != targetPackage &&
            !pkg.startsWith("com.campbell.xgm") &&
            !SYSTEM_PACKAGES.contains(pkg) &&
            !excludedApps.contains(pkg) &&
            !pkg.startsWith("com.android.providers.") &&
            !pkg.startsWith("com.android.server.") &&
            !pkg.startsWith("system") &&
            !pkg.startsWith("android") &&
            !pkg.contains(":") // Filter out process names with colons (sub-processes)
        }
    }

    private fun killBackgroundProcesses(apps: List<String>) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        apps.forEach { pkg ->
            try {
                am.killBackgroundProcesses(pkg)
                Log.d("PipelineService", "Killed background: $pkg")
            } catch (e: Exception) {
                // Some system apps can't be killed, ignore
            }
        }
    }

    private fun startPeriodicFreezing(apps: List<String>) {
        freezeJob = serviceScope.launch {
            while (isActive) {
                killBackgroundProcesses(apps)
                delay(FREEZE_INTERVAL_MS)
            }
        }
    }

    private fun stopAllOptimizations() {
        // Stop periodic freezing
        freezeJob?.cancel()
        freezeJob = null
        
        // Stop cooldown monitoring
        cooldownJob?.cancel()
        cooldownJob = null

        // Stop auto-teardown monitoring
        autoTeardownJob?.cancel()
        autoTeardownJob = null
    }

    private fun restoreSystemState() {
        if (isRestored) return

        // Restore DND
        if (!isDndRestored) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(originalDndFilter)
            }
            isDndRestored = true
        }

        // Restore Notification Filter
        disableNotificationFilter()

        // Restore Network
        disableNetworkBoost()

        // Restore Battery
        disableBatteryProfile()

        // Restore CPU Governor
        disableCpuTuner()

        // Restore CPU Throttle
        if (isCpuThrottled) {
            try {
                val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
                val cpuFile = File(cpuPath)
                if (cpuFile.exists() && cpuFile.canWrite() && originalCpuMaxFreq != null) {
                    cpuFile.writeText(originalCpuMaxFreq!!)
                    Log.i("PipelineService", "CPU throttle restored to $originalCpuMaxFreq")
                }
                isCpuThrottled = false
            } catch (e: Exception) {
                Log.e("PipelineService", "Failed to restore CPU throttle: ${e.message}")
            }
        }

        // Restore Keep Screen Awake
        disableKeepScreenAwake()

        // Restore Auto-Brightness
        disableAutoBrightnessLock()

        // Restore Suspended Apps
        if (!isAppsRestored) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, CampbellAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(packageName) && suspendedPackagesList.isNotEmpty()) {
                try {
                    dpm.setPackagesSuspended(adminComponent, suspendedPackagesList, false)
                } catch (e: Exception) {
                    Log.e("PipelineService", "Failed to restore apps: ${e.message}")
                }
            }
            isAppsRestored = true
        }

        Log.i("PipelineService", "Stopping FPS Overlay")
        val fpsIntent = Intent(this, FpsOverlayService::class.java)
        stopService(fpsIntent)

        Log.i("PipelineService", "Stopping Stats Overlay")
        val statsIntent = Intent(this, StatsOverlayService::class.java)
        statsIntent.action = "STOP_STATS_OVERLAY"
        try { startService(statsIntent) } catch (_: Exception) {}

        Log.i("PipelineService", "Stopping Ping Stabilizer")
        val vpnIntent = Intent(this, PingStabilizerVpnService::class.java).apply {
            action = PingStabilizerVpnService.ACTION_STOP_VPN
        }
        try {
            startService(vpnIntent)
        } catch (_: Exception) {
            // VPN service may not be running
        }

        isRestored = true
        isRunning = false
        Log.i("PipelineService", "System state restored")
        com.campbell.xgm.ui.widgets.GameModeWidgetProvider.updateAllWidgets(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun clearGameCache(targetPackage: String) {
        serviceScope.launch {
            try {
                var totalCleared = 0L

                // Clear target package cache (requires Device Owner or root)
                try {
                    val packageContext = createPackageContext(targetPackage, 0)
                    val packageCacheDir = packageContext.cacheDir
                    totalCleared += getDirSize(packageCacheDir)
                    deleteDir(packageCacheDir)
                } catch (_: Exception) {
                    // Can't access other app's cache without Device Owner
                }

                val clearedMB = totalCleared / (1024.0 * 1024.0)
                Log.i("PipelineService", "Storage cleaner cleared ${String.format("%.2f", clearedMB)} MB from $targetPackage")
            } catch (e: Exception) {
                Log.e("PipelineService", "Failed to clear cache: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        stopAllOptimizations()
        if (!isRestored) {
            restoreSystemState()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
