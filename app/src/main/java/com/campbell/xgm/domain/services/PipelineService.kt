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
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
    }

    // State Tracking
    private var isDndRestored = true
    private var isAppsRestored = true
    private var isNetworkBoosted = false
    private var isBatteryOptimized = false
    private var isCpuTuned = false
    private var originalDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL
    private var suspendedPackagesList = emptyArray<String>()
    private var originalWifiVerbosity = -1
    
    private var pendingTargetPackage: String? = null

    // Background Coroutines
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var freezeJob: Job? = null
    private var cooldownJob: Job? = null
    private val FREEZE_INTERVAL_MS = 5000L
    private val COOLDOWN_CHECK_INTERVAL_MS = 10000L
    private val COOLDOWN_THRESHOLD_CELSIUS = 42.0

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
        "com.android.bluetooth",
        "com.android.nfc",
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
        "com.android.connectivity.resources"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_GAME_MODE") {
            stopAllOptimizations()
            restoreSystemState()
            stopSelf()
            return START_NOT_STICKY
        }

        val targetPackage = intent?.getStringExtra("TARGET_PACKAGE")
        if (targetPackage != null) {
            startForegroundServiceNotification()
            executeAppPipeline(targetPackage)
        } else {
            Log.e("PipelineService", "No target package provided")
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
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
            .setContentTitle("CampbellXGM Engine Active")
            .setContentText("Game mode is isolating the system. Tap to restore.")
            .setSmallIcon(R.drawable.logo)
            .addAction(R.drawable.logo, "Stop Game Mode", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun executeAppPipeline(targetPackage: String) {
        val prefs = getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
        val isAggressiveFreezingEnabled = prefs.getBoolean("aggressive_freezing", true)
        val isDndEnabled = prefs.getBoolean("dnd_mode", false)
        val isNetworkBoostEnabled = prefs.getBoolean("network_boost", true)
        val isBatteryProfileEnabled = prefs.getBoolean("battery_profile", true)
        val isCpuTunerEnabled = prefs.getBoolean("cpu_tuner", false)
        val isCooldownEnabled = prefs.getBoolean("cooldown_mode", true)
        val isStorageCleanerEnabled = prefs.getBoolean("storage_cleaner", true)

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, CampbellAdminReceiver::class.java)
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val isDeviceAdmin = dpm.isAdminActive(adminComponent)

        // Storage Cleaner - Clear cache before launching
        if (isStorageCleanerEnabled) {
            clearGameCache(targetPackage)
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

        // Freezing
        if (isAggressiveFreezingEnabled) {
            serviceScope.launch {
                val appsToFreeze = withContext(Dispatchers.IO) { getFreezableApps(targetPackage) }

                if (isDeviceOwner) {
                    try {
                        suspendedPackagesList = dpm.setPackagesSuspended(adminComponent, appsToFreeze.toTypedArray(), true)
                        isAppsRestored = false
                    } catch (e: Exception) {
                        Log.e("PipelineService", "Failed to suspend apps: ${e.message}")
                    }
                } else if (SafetyInterceptor.isRunning()) {
                    Log.i("PipelineService", "Triggering Accessibility Ghost Finger")
                    val safetyService = SafetyInterceptor.instance
                    safetyService?.forceStopApps(appsToFreeze.toList()) {
                        Log.i("PipelineService", "Accessibility Force Stop complete. Launching game.")
                        launchGame(targetPackage)
                    }
                    return@launch
                } else if (isDeviceAdmin) {
                    killBackgroundProcesses(appsToFreeze)
                    startPeriodicFreezing(appsToFreeze)
                }

                // Cooldown monitoring
                if (isCooldownEnabled) {
                    startCooldownMonitoring()
                }

                launchGame(targetPackage)
            }
        } else {
            // Cooldown monitoring
            if (isCooldownEnabled) {
                startCooldownMonitoring()
            }
            launchGame(targetPackage)
        }
    }

    private fun enableNetworkBoost() {
        try {
            // Disable WiFi verbose logging
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            originalWifiVerbosity = Settings.Global.getInt(contentResolver, "wifi_verbose_logging", 0)
            Settings.Global.putInt(contentResolver, "wifi_verbose_logging", 1)

            // Disable background data for all apps except system
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Set network scoring to prefer low latency
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null) {
                        Log.i("PipelineService", "Network boost active - WiFi connected")
                    }
                }
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
            isNetworkBoosted = false
            Log.i("PipelineService", "Network boost disabled")
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable network boost: ${e.message}")
        }
    }

    private fun enableBatteryProfile() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // Check battery saver status
            if (powerManager.isPowerSaveMode) {
                Log.i("PipelineService", "Battery saver is active - user should disable for best performance")
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
            // Set CPU governor to performance
            val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
            val cpuFile = File(cpuPath)
            if (cpuFile.exists() && cpuFile.canWrite()) {
                cpuFile.writeText("performance")
                isCpuTuned = true
                Log.i("PipelineService", "CPU tuner enabled - governor set to performance")
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
                cpuFile.writeText("schedutil")
                Log.i("PipelineService", "CPU tuner disabled - governor restored to schedutil")
            }
            isCpuTuned = false
        } catch (e: Exception) {
            Log.e("PipelineService", "Failed to disable CPU tuner: ${e.message}")
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

    private fun checkTemperature() {
        try {
            val intent = applicationContext.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            
            if (temp >= COOLDOWN_THRESHOLD_CELSIUS) {
                Log.w("PipelineService", "Device overheating: ${temp}°C")
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                if (!dpm.isDeviceOwnerApp(packageName)) {
                    Log.w("PipelineService", "CPU throttling requires Device Owner permission")
                    return
                }
                // Reduce CPU frequency (Device Owner only)
                val cpuPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"
                val cpuFile = File(cpuPath)
                if (cpuFile.exists() && cpuFile.canWrite()) {
                    val maxFreq = cpuFile.readText().trim().toLongOrNull()
                    if (maxFreq != null) {
                        val reducedFreq = (maxFreq * 0.7).toLong()
                        cpuFile.writeText(reducedFreq.toString())
                        Log.i("PipelineService", "CPU throttled to $reducedFreq")
                    }
                } else {
                    Log.w("PipelineService", "CPU frequency file not writable")
                }
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
        if (prefs.getBoolean("ping_stabilizer", false)) {
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
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val installedApps = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.distinct()

        return installedApps.filter {
            it != packageName &&
            it != targetPackage &&
            !SYSTEM_PACKAGES.contains(it) &&
            !it.startsWith("com.android.providers.") &&
            !it.startsWith("com.android.server.")
        }
    }

    private fun killBackgroundProcesses(apps: List<String>) {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        apps.forEach {
            try {
                am.killBackgroundProcesses(it)
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
    }

    private fun restoreSystemState() {
        // Restore DND
        if (!isDndRestored) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(originalDndFilter)
            }
            isDndRestored = true
        }

        // Restore Network
        disableNetworkBoost()

        // Restore Battery
        disableBatteryProfile()

        // Restore CPU
        disableCpuTuner()

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

        Log.i("PipelineService", "Stopping Ping Stabilizer")
        val vpnIntent = Intent(this, PingStabilizerVpnService::class.java).apply {
            action = PingStabilizerVpnService.ACTION_STOP_VPN
        }
        try {
            startService(vpnIntent)
        } catch (_: Exception) {
            // VPN service may not be running
        }
    }

    private fun clearGameCache(targetPackage: String) {
        serviceScope.launch {
            try {
                var totalCleared = 0L

                // Clear our app's cache
                val appCacheDir = cacheDir
                totalCleared += getDirSize(appCacheDir)
                deleteDir(appCacheDir)

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
                Log.i("PipelineService", "Storage cleaner cleared ${String.format("%.2f", clearedMB)} MB")
            } catch (e: Exception) {
                Log.e("PipelineService", "Failed to clear cache: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        stopAllOptimizations()
        restoreSystemState()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
