package com.campbell.xgm.ui.screens

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.campbell.xgm.domain.services.CampbellAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.DecimalFormat

enum class DnsProvider(val hostname: String, val displayName: String) {
    SYSTEM_DEFAULT("", "System Default"),
    GOOGLE("dns.google", "Google DNS"),
    CLOUDFLARE("1dot1dot1dot1.cloudflare-dns.com", "Cloudflare DNS"),
    OPENDNS("dns.opendns.com", "OpenDNS"),
    QUAD9("dns.quad9.net", "Quad9"),
    ADGUARD("dns.adguard.com", "AdGuard DNS")
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
    private val dpm = application.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(application, CampbellAdminReceiver::class.java)

    // Existing Toggles
    private val _isAggressiveFreezingEnabled = MutableStateFlow(sharedPreferences.getBoolean("aggressive_freezing", true))
    val isAggressiveFreezingEnabled: StateFlow<Boolean> = _isAggressiveFreezingEnabled

    private val _isDndEnabled = MutableStateFlow(sharedPreferences.getBoolean("dnd_mode", false))
    val isDndEnabled: StateFlow<Boolean> = _isDndEnabled

    private val _isPingStabilizerEnabled = MutableStateFlow(sharedPreferences.getBoolean("ping_stabilizer", false))
    val isPingStabilizerEnabled: StateFlow<Boolean> = _isPingStabilizerEnabled

    private val _isKeepScreenAwakeEnabled = MutableStateFlow(sharedPreferences.getBoolean("keep_screen_awake", true))
    val isKeepScreenAwakeEnabled: StateFlow<Boolean> = _isKeepScreenAwakeEnabled

    private val _isAutoBrightnessLockEnabled = MutableStateFlow(sharedPreferences.getBoolean("auto_brightness_lock", true))
    val isAutoBrightnessLockEnabled: StateFlow<Boolean> = _isAutoBrightnessLockEnabled

    // New Toggles
    private val _isFpsOverlayEnabled = MutableStateFlow(sharedPreferences.getBoolean("fps_overlay", false))
    val isFpsOverlayEnabled: StateFlow<Boolean> = _isFpsOverlayEnabled

    private val _isNetworkBoostEnabled = MutableStateFlow(sharedPreferences.getBoolean("network_boost", true))
    val isNetworkBoostEnabled: StateFlow<Boolean> = _isNetworkBoostEnabled

    private val _isBatteryProfileEnabled = MutableStateFlow(sharedPreferences.getBoolean("battery_profile", true))
    val isBatteryProfileEnabled: StateFlow<Boolean> = _isBatteryProfileEnabled

    private val _isAutoStartEnabled = MutableStateFlow(sharedPreferences.getBoolean("auto_start_game_mode", false))
    val isAutoStartEnabled: StateFlow<Boolean> = _isAutoStartEnabled

    private val _isCpuTunerEnabled = MutableStateFlow(sharedPreferences.getBoolean("cpu_tuner", false))
    val isCpuTunerEnabled: StateFlow<Boolean> = _isCpuTunerEnabled

    private val _isNotificationFilterEnabled = MutableStateFlow(sharedPreferences.getBoolean("notification_filter", false))
    val isNotificationFilterEnabled: StateFlow<Boolean> = _isNotificationFilterEnabled

    private val _isCooldownEnabled = MutableStateFlow(sharedPreferences.getBoolean("cooldown_mode", true))
    val isCooldownEnabled: StateFlow<Boolean> = _isCooldownEnabled

    private val _isStorageCleanerEnabled = MutableStateFlow(sharedPreferences.getBoolean("storage_cleaner", true))
    val isStorageCleanerEnabled: StateFlow<Boolean> = _isStorageCleanerEnabled

    // DNS Provider
    private val _selectedDns = MutableStateFlow(getCurrentDns())
    val selectedDns: StateFlow<DnsProvider> = _selectedDns

    // Device Owner State
    private val _isDeviceOwner = MutableStateFlow(dpm.isDeviceOwnerApp(application.packageName))
    val isDeviceOwner: StateFlow<Boolean> = _isDeviceOwner

    // Standard Admin State
    private val _isDeviceAdmin = MutableStateFlow(dpm.isAdminActive(adminComponent))
    val isDeviceAdmin: StateFlow<Boolean> = _isDeviceAdmin

    // Toggle functions
    fun toggleAggressiveFreezing(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("aggressive_freezing", enabled) }
        _isAggressiveFreezingEnabled.value = enabled
    }

    fun toggleDndMode(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("dnd_mode", enabled) }
        _isDndEnabled.value = enabled
    }

    fun togglePingStabilizer(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("ping_stabilizer", enabled) }
        _isPingStabilizerEnabled.value = enabled
    }

    fun toggleKeepScreenAwake(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("keep_screen_awake", enabled) }
        _isKeepScreenAwakeEnabled.value = enabled
    }

    fun toggleAutoBrightnessLock(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("auto_brightness_lock", enabled) }
        _isAutoBrightnessLockEnabled.value = enabled
    }

    fun toggleFpsOverlay(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("fps_overlay", enabled) }
        _isFpsOverlayEnabled.value = enabled
    }

    fun toggleNetworkBoost(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("network_boost", enabled) }
        _isNetworkBoostEnabled.value = enabled
    }

    fun toggleBatteryProfile(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("battery_profile", enabled) }
        _isBatteryProfileEnabled.value = enabled
    }

    fun toggleAutoStart(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("auto_start_game_mode", enabled) }
        _isAutoStartEnabled.value = enabled
    }

    fun toggleCpuTuner(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("cpu_tuner", enabled) }
        _isCpuTunerEnabled.value = enabled
    }

    fun toggleNotificationFilter(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("notification_filter", enabled) }
        _isNotificationFilterEnabled.value = enabled
    }

    fun toggleCooldown(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("cooldown_mode", enabled) }
        _isCooldownEnabled.value = enabled
    }

    fun toggleStorageCleaner(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("storage_cleaner", enabled) }
        _isStorageCleanerEnabled.value = enabled
    }

    suspend fun getCacheSize(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val cacheDir = context.cacheDir
            val appCacheSize = getDirSize(cacheDir)
            
            val packageContext = context.createPackageContext(packageName, 0)
            val packageCacheSize = getDirSize(packageContext.cacheDir)
            
            val totalSize = appCacheSize + packageCacheSize
            formatSize(totalSize)
        } catch (e: Exception) {
            "0 B"
        }
    }

    suspend fun clearCache(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val beforeSize = getCacheSize(packageName)
            
            // Clear app cache
            deleteDir(context.cacheDir)
            
            // Clear target package cache
            try {
                val packageContext = context.createPackageContext(packageName, 0)
                deleteDir(packageContext.cacheDir)
            } catch (_: Exception) {}
            
            // Clear temp files
            clearTempFiles()
            
            val afterSize = getCacheSize(packageName)
            Log.i("StorageCleaner", "Cleared cache: $beforeSize -> $afterSize")
            beforeSize
        } catch (e: Exception) {
            Log.e("StorageCleaner", "Failed to clear cache: ${e.message}")
            "0 B"
        }
    }

    private suspend fun getDirSize(dir: File?): Long = withContext(Dispatchers.IO) {
        if (dir == null || !dir.exists()) return@withContext 0L
        var size = 0L
        val files = dir.listFiles() ?: return@withContext 0L
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        size
    }

    private suspend fun deleteDir(dir: File?): Boolean = withContext(Dispatchers.IO) {
        if (dir == null || !dir.exists()) return@withContext false
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }

    private suspend fun clearTempFiles() = withContext(Dispatchers.IO) {
        try {
            // Only clear app-specific temp files, NOT system directories
            val tempDir = File(getApplication<Application>().cacheDir, "temp")
            if (tempDir.exists()) {
                deleteDir(tempDir)
            }
        } catch (_: Exception) {}
    }

    private fun formatSize(size: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            size >= 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))} GB"
            size >= 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))} MB"
            size >= 1024 -> "${df.format(size / 1024.0)} KB"
            else -> "$size B"
        }
    }

    fun setDnsProvider(provider: DnsProvider) {
        _selectedDns.value = provider
        sharedPreferences.edit { putString("dns_provider", provider.name) }
        applyDnsSettings(provider)
    }

    private fun getCurrentDns(): DnsProvider {
        val savedName = sharedPreferences.getString("dns_provider", DnsProvider.SYSTEM_DEFAULT.name)
        return try {
            DnsProvider.valueOf(savedName ?: DnsProvider.SYSTEM_DEFAULT.name)
        } catch (_: Exception) {
            DnsProvider.SYSTEM_DEFAULT
        }
    }

    private fun applyDnsSettings(provider: DnsProvider) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (dpm.isDeviceOwnerApp(getApplication<Application>().packageName)) {
                    if (provider == DnsProvider.SYSTEM_DEFAULT) {
                        dpm.setGlobalSetting(adminComponent, "private_dns_mode", "off")
                    } else {
                        dpm.setGlobalSetting(adminComponent, "private_dns_mode", "hostname")
                        dpm.setGlobalSetting(adminComponent, "private_dns_specifier", provider.hostname)
                    }
                } else {
                    val contentResolver = getApplication<Application>().contentResolver
                    if (provider == DnsProvider.SYSTEM_DEFAULT) {
                        Settings.Global.putString(contentResolver, "private_dns_mode", "off")
                    } else {
                        Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
                        Settings.Global.putString(contentResolver, "private_dns_specifier", provider.hostname)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsViewModel", "Failed to apply DNS settings (Requires Device Owner or WRITE_SECURE_SETTINGS via ADB): ${e.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    fun removeDeviceOwner() {
        if (dpm.isDeviceOwnerApp(getApplication<Application>().packageName)) {
            dpm.clearDeviceOwnerApp(getApplication<Application>().packageName)
            refreshAdminState()
        }
    }

    fun removeDeviceAdmin() {
        if (dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent)
            refreshAdminState()
        }
    }
    
    fun refreshAdminState() {
        _isDeviceOwner.value = dpm.isDeviceOwnerApp(getApplication<Application>().packageName)
        _isDeviceAdmin.value = dpm.isAdminActive(adminComponent)
    }
}
