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
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.campbell.xgm.R
import com.campbell.xgm.util.ForegroundTracker
import com.campbell.xgm.util.TeardownPolicy
import kotlinx.coroutines.*

class PipelineService : Service() {

    companion object {
        private const val CHANNEL_ID = "campbellxgm_pipeline_channel"
        private const val NOTIFICATION_ID = 9001
        /** Set by the auto-start monitor: the game is already on screen, so don't relaunch it. */
        const val EXTRA_GAME_ALREADY_OPEN = "GAME_ALREADY_OPEN"

        @Volatile var isRunning = false
            private set

        @Volatile var activeTargetPackage: String? = null
            private set

        fun restorePersistedState(context: android.content.Context) {
            val prefs = context.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
            val dndFilter = prefs.getInt("orig_dnd_filter", -1)
            if (dndFilter != -1) {
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(dndFilter)
                    }
                } catch (_: Exception) {}
                prefs.edit().remove("orig_dnd_filter").apply()
            }

            val brightnessMode = prefs.getInt("orig_brightness_mode", -1)
            if (brightnessMode != -1) {
                try {
                    android.provider.Settings.System.putInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                        brightnessMode
                    )
                } catch (_: Exception) {}
                prefs.edit().remove("orig_brightness_mode").apply()
            }

            // Background sync was turned off by Network Boost; turn it back on after a crash.
            if (prefs.contains("orig_sync_state")) {
                try {
                    android.content.ContentResolver.setMasterSyncAutomatically(prefs.getBoolean("orig_sync_state", true))
                } catch (_: Exception) {}
            }
            // Older versions turned on Wi-Fi verbose logging; undo it if it was left on.
            val wifiVerbosity = prefs.getInt("orig_wifi_verbosity", -1)
            if (wifiVerbosity != -1) {
                try {
                    Settings.Global.putInt(context.contentResolver, "wifi_verbose_logging", wifiVerbosity)
                } catch (_: Exception) {}
            }

            clearPersistedState(context)
        }

        private fun clearPersistedState(context: Context) {
            context.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE).edit()
                .remove("orig_dnd_filter")
                .remove("orig_brightness_mode")
                .remove("orig_cpu_governor") // written by older versions
                .remove("orig_cpu_max_freq") // written by older versions
                .remove("orig_wifi_verbosity")
                .remove("orig_sync_state")
                .apply()
        }

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
    }

    // State Tracking — all @Volatile to ensure visibility across threads
    @Volatile private var isDndRestored = true
    @Volatile private var isAppsRestored = true
    @Volatile private var isNetworkBoosted = false
    @Volatile private var isWakeLockHeld = false
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var isBrightnessLocked = false
    @Volatile private var isNotificationFilterActive = false
    @Volatile private var isRestored = false
    private var originalDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var originalBrightnessMode = 1 // Default to auto
    private var originalNotificationPolicy: NotificationManager.Policy? = null
    private var suspendedPackagesList = emptyArray<String>()
    private var originalSyncState = true
    private var isSyncDisabled = false
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    // Background Coroutines
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var freezeJob: Job? = null
    private var autoTeardownJob: Job? = null
    // killBackgroundProcesses only hits cached processes; looping over every app every 5s
    // stole CPU from the game itself. 30s is enough to catch apps that respawn.
    private val FREEZE_INTERVAL_MS = 30_000L
    private val AUTO_TEARDOWN_CHECK_MS = 5000L

    override fun onCreate() {
        super.onCreate()
        restorePersistedState(this)
    }

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

        val targetPackage = intent?.getStringExtra("TARGET_PACKAGE")
        val gameAlreadyOpen = intent?.getBooleanExtra(EXTRA_GAME_ALREADY_OPEN, false) ?: false

        // A second launch while game mode is active: undo the previous session first so its
        // jobs and saved "original" settings are not overwritten.
        if (targetPackage != null && isRunning && !isRestored) {
            stopAllOptimizations()
            restoreSystemState()
        }

        val gameName = if (targetPackage != null) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString() }
            catch (_: Exception) { targetPackage }
        } else "Game Mode"
        startForegroundServiceNotification(gameName)

        if (targetPackage != null) {
            isRunning = true
            isRestored = false
            activeTargetPackage = targetPackage
            executeAppPipeline(targetPackage, gameAlreadyOpen)
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

    private fun executeAppPipeline(targetPackage: String, gameAlreadyOpen: Boolean) {
        val prefs = getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)

        // Check for per-game profile first, fall back to global settings.
        // Parse the JSON once and reuse it for every lookup below.
        val profileJson = getSharedPreferences("game_profiles", MODE_PRIVATE).getString(targetPackage, null)
        val profileObj = try { profileJson?.let { org.json.JSONObject(it) } } catch (_: Exception) { null }
        fun profileBool(key: String, globalKey: String, default: Boolean): Boolean {
            if (profileObj != null) {
                try { return profileObj.optBoolean(key, prefs.getBoolean(globalKey, default)) }
                catch (_: Exception) {}
            }
            return prefs.getBoolean(globalKey, default)
        }

        val isAggressiveFreezingEnabled = profileBool("aggressiveFreezing", "aggressive_freezing", true)
        val isDndEnabled = profileBool("dndMode", "dnd_mode", false)
        val isNetworkBoostEnabled = profileBool("networkBoost", "network_boost", true)
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

        serviceScope.launch {
            coroutineScope {
                launch {
                    if (isFpsOverlayEnabled && Settings.canDrawOverlays(this@PipelineService)) {
                        startService(Intent(this@PipelineService, FpsOverlayService::class.java))
                    }
                }
                launch {
                    if (isStatsOverlayEnabled && Settings.canDrawOverlays(this@PipelineService)) {
                        startForegroundService(Intent(this@PipelineService, StatsOverlayService::class.java))
                    }
                }
                launch {
                    if (isKeepScreenAwakeEnabled) enableKeepScreenAwake()
                }
                launch {
                    if (isAutoBrightnessLockEnabled) enableAutoBrightnessLock()
                }
                launch {
                    if (isNotificationFilterEnabled) enableNotificationFilter()
                }
                launch {
                    if (isDndEnabled) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            originalDndFilter = notificationManager.currentInterruptionFilter
                            getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE).edit()
                                .putInt("orig_dnd_filter", originalDndFilter).apply()
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                            isDndRestored = false
                        } else {
                            Log.w("PipelineService", "DND enabled but policy access not granted!")
                        }
                    }
                }
                launch {
                    if (isNetworkBoostEnabled) enableNetworkBoost()
                }
            }

            if (isAggressiveFreezingEnabled) {
                val rawAppsToFreeze = withContext(Dispatchers.IO) { getFreezableApps(targetPackage) }
                // Never freeze apps that are actively playing media (music/streaming) — otherwise
                // game mode would kill the user's audio. Re-checked inside startPeriodicFreezing too.
                val mediaPackages = com.campbell.xgm.util.MediaUtils.getActiveMediaPackages(this@PipelineService)
                val appsToFreeze = rawAppsToFreeze.filter { it !in mediaPackages }
                Log.i("PipelineService", "Found ${rawAppsToFreeze.size} apps to freeze (${appsToFreeze.size} after excluding ${mediaPackages.size} media)")

                killBackgroundProcesses(appsToFreeze)

                if (isDeviceOwner) {
                    try {
                        suspendedPackagesList = dpm.setPackagesSuspended(adminComponent, appsToFreeze.toTypedArray(), true)
                        isAppsRestored = false
                        Log.i("PipelineService", "Suspended ${suspendedPackagesList.size} packages via Device Owner")
                    } catch (e: Exception) {
                        Log.e("PipelineService", "Failed to suspend apps: ${e.message}")
                    }
                }

                // Layer 3: Accessibility — Greenify-style force-stop via automated UI.
                // Gated behind an explicit opt-in pref (default OFF): this opens each app's
                // Settings page and hijacks the screen, so it must never run unless enabled.
                val ghostFingerEnabled = prefs.getBoolean("accessibility_force_stop", false)
                if (ghostFingerEnabled) {
                    Log.i("PipelineService", "Triggering Accessibility Ghost Finger for thorough force-stop")
                    val speedBoostManager = SpeedBoostManager(this@PipelineService)
                    val appsToKill = speedBoostManager.getAppsToKill().filter { appsToFreeze.contains(it) }
                    
                    val intent = Intent(SafetyInterceptor.ACTION_FORCE_STOP).apply {
                        setPackage(packageName)
                        putStringArrayListExtra(SafetyInterceptor.EXTRA_PACKAGES, ArrayList(appsToKill))
                    }
                    sendBroadcast(intent)
                    speedBoostManager.markAsClosed(appsToKill)
                }

                startPeriodicFreezing(appsToFreeze)
            }

            startAutoTeardownMonitoring(prefs.getBoolean("accessibility_force_stop", false))

            launchGame(targetPackage, gameAlreadyOpen)
        }
    }

    private fun enableNetworkBoost() {
        // 1) Wi-Fi low-latency mode: asks the Wi-Fi chip to stop power-saving, which
        //    reduces ping spikes. (The old code turned on Wi-Fi *debug logging*, which
        //    adds overhead and needed an ADB-only permission.)
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiLock = wifiManager.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "CampbellXGM:LowLatency"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.i("PipelineService", "Wi-Fi low-latency lock acquired")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to acquire Wi-Fi low-latency lock: ${e.message}")
        }

        // 2) Pause background account sync so other apps don't download during play.
        try {
            originalSyncState = android.content.ContentResolver.getMasterSyncAutomatically()
            getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("orig_sync_state", originalSyncState).apply()
            android.content.ContentResolver.setMasterSyncAutomatically(false)
            isSyncDisabled = true
            Log.i("PipelineService", "Background sync paused")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to pause background sync: ${e.message}")
        }

        isNetworkBoosted = true
    }

    private fun disableNetworkBoost() {
        if (!isNetworkBoosted) return
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to release Wi-Fi lock: ${e.message}")
        }
        wifiLock = null

        if (isSyncDisabled) {
            try {
                android.content.ContentResolver.setMasterSyncAutomatically(originalSyncState)
            } catch (e: Exception) {
                Log.e("PipelineService", "Failed to restore background sync: ${e.message}")
            }
            isSyncDisabled = false
        }

        isNetworkBoosted = false
        Log.i("PipelineService", "Network boost disabled")
    }

    @Suppress("DEPRECATION")
    private fun enableKeepScreenAwake() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // BRIGHT, not DIM: a DIM lock lets the screen go dark mid-game.
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CampbellXGM:GameMode"
            )
            // Safety cap so a missed teardown can't keep the screen on forever.
            wakeLock?.acquire(6 * 60 * 60 * 1000L)
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
                1
            )
            getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE).edit()
                .putInt("orig_brightness_mode", originalBrightnessMode).apply()
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

    private fun startAutoTeardownMonitoring(ghostFingerEnabled: Boolean) {
        val target = activeTargetPackage ?: return
        // Things that pop up over a game without the user leaving it.
        val ignored = mutableSetOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.play.games",
        )
        currentInputMethodPackage()?.let { ignored += it }
        // Ghost Finger opens Settings pages on purpose; don't mistake that for leaving.
        if (ghostFingerEnabled) ignored += "com.android.settings"

        val tracker = ForegroundTracker(this)
        val policy = TeardownPolicy(target, ignored)
        val startedAt = System.currentTimeMillis()

        autoTeardownJob = serviceScope.launch {
            while (isActive) {
                delay(AUTO_TEARDOWN_CHECK_MS)
                val foreground = tracker.poll()
                if (policy.onSample(foreground, System.currentTimeMillis() - startedAt)) {
                    Log.i("PipelineService", "User left game ($target -> $foreground) - auto-restoring system state")
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



    private fun launchGame(targetPackage: String, gameAlreadyOpen: Boolean) {
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

        if (gameAlreadyOpen) {
            Log.i("PipelineService", "Game already on screen (auto-start) - not relaunching $targetPackage")
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            // Same flags a home screen uses: resumes a running game instead of CLEAR_TOP,
            // which could wipe the game's open screens back to its splash/menu.
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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
        // Freezing (and especially Device Owner suspension) of these breaks the phone:
        // no home screen, no keyboard, can't answer calls or read SMS codes.
        val essentialApps = getEssentialPackages()

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
            !essentialApps.contains(pkg) &&
            !pkg.startsWith("com.android.providers.") &&
            !pkg.startsWith("com.android.server.") &&
            !pkg.startsWith("system") &&
            !pkg.startsWith("android") &&
            !pkg.contains(":") // Filter out process names with colons (sub-processes)
        }
    }

    private fun currentInputMethodPackage(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
    } catch (_: Exception) { null }

    /** Default home screen, keyboard, phone and SMS apps on THIS device (not a hardcoded list). */
    private fun getEssentialPackages(): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(home, 0).forEach { result += it.activityInfo.packageName }
        } catch (_: Exception) {}
        currentInputMethodPackage()?.let { result += it }
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            telecom.defaultDialerPackage?.let { result += it }
        } catch (_: Exception) {}
        try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this)?.let { result += it }
        } catch (_: Exception) {}
        return result
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
                // Re-check media each cycle so a media app that starts mid-game is protected,
                // and one that stops playing can be frozen again.
                val mediaPackages = com.campbell.xgm.util.MediaUtils.getActiveMediaPackages(this@PipelineService)
                killBackgroundProcesses(apps.filter { it !in mediaPackages })
                delay(FREEZE_INTERVAL_MS)
            }
        }
    }

    private fun stopAllOptimizations() {
        // Stop periodic freezing
        freezeJob?.cancel()
        freezeJob = null
        
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

        // Everything is back to normal; forget the saved originals so a later
        // crash-recovery doesn't "restore" stale values over the user's current settings.
        clearPersistedState(this)

        isRestored = true
        isRunning = false
        Log.i("PipelineService", "System state restored")
        com.campbell.xgm.ui.widgets.GameModeWidgetProvider.updateAllWidgets(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
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
