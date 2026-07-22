package com.campbell.xgm.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campbell.xgm.data.local.GameTarget
import com.campbell.xgm.domain.services.SafetyInterceptor
import com.campbell.xgm.domain.services.SpeedBoostManager
import kotlinx.coroutines.Dispatchers
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val appName: String
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("saved_games_prefs", Context.MODE_PRIVATE)
    private val speedBoostManager = SpeedBoostManager(application)

    private val _allowedGames = MutableStateFlow<List<GameTarget>>(emptyList())
    val allowedGames: StateFlow<List<GameTarget>> = _allowedGames

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    private val _runningAppsCount = MutableStateFlow(0)
    val runningAppsCount: StateFlow<Int> = _runningAppsCount

    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting

    private val _boostResult = MutableStateFlow<String?>(null)
    val boostResult: StateFlow<String?> = _boostResult

    init {
        loadSavedGames()
        refreshRunningCount()
    }

    fun refreshRunningCount() {
        viewModelScope.launch(Dispatchers.IO) {
            _runningAppsCount.value = speedBoostManager.getRunningAppsCount()
        }
    }

    fun startBoost() {
        if (_isBoosting.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isBoosting.value = true
            _boostResult.value = null

            val appsToKill = speedBoostManager.getAppsToKill()
            if (appsToKill.isEmpty()) {
                _boostResult.value = "No background apps to kill"
                _isBoosting.value = false
                refreshRunningCount()
                return@launch
            }

            val accessibilityEnabled = com.campbell.xgm.util.PermissionUtils.isAccessibilityServiceEnabled(getApplication())
            val ghostFingerEnabled = getApplication<Application>()
                .getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
                .getBoolean("accessibility_force_stop", false)

            if (ghostFingerEnabled && accessibilityEnabled && SafetyInterceptor.instance != null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                SafetyInterceptor.startForceStop(appsToKill) {
                    latch.countDown()
                }
                speedBoostManager.markAsClosed(appsToKill)
                latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                _boostResult.value = "Force-stopped ${appsToKill.size} apps"
            } else {
                var killed = 0
                for (pkg in appsToKill) {
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                        val exited = process.waitFor(3, java.util.concurrent.TimeUnit.MILLISECONDS)
                        if (exited && process.exitValue() == 0) killed++
                    } catch (_: Exception) {}
                }
                speedBoostManager.markAsClosed(appsToKill)
                _boostResult.value = "Killed $killed of ${appsToKill.size} apps"
            }

            _isBoosting.value = false
            refreshRunningCount()
        }
    }

    fun clearBoostResult() {
        _boostResult.value = null
    }

    private fun loadSavedGames() {
        viewModelScope.launch(Dispatchers.IO) {
            val games = sharedPrefs.all.mapNotNull { entry ->
                val packageName = entry.key
                val gameName = entry.value as? String
                if (gameName != null) {
                    GameTarget(packageName = packageName, gameName = gameName)
                } else {
                    null
                }
            }.sortedBy { it.gameName }
            _allowedGames.value = games
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            val apps = resolveInfos.mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                val name = info.loadLabel(packageManager).toString()
                if (packageName == getApplication<Application>().packageName) return@mapNotNull null
                if (isSystemPackage(packageName)) return@mapNotNull null
                AppInfo(packageName, name)
            }.distinctBy { it.packageName }.sortedBy { it.appName }

            _installedApps.value = apps
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.google.android.") ||
                packageName == "com.android.vending" ||
                packageName == "com.google.android.gms"
    }

    fun addGame(app: AppInfo) {
        sharedPrefs.edit().putString(app.packageName, app.appName).apply()
        loadSavedGames()
    }

    fun removeGame(packageName: String) {
        sharedPrefs.edit { remove(packageName) }
        try {
            if (com.campbell.xgm.domain.services.PipelineService.isRunning &&
                com.campbell.xgm.domain.services.PipelineService.activeTargetPackage == packageName
            ) {
                val context = getApplication<Application>()
                val intent = Intent(context, com.campbell.xgm.domain.services.PipelineService::class.java)
                intent.action = "STOP_GAME_MODE"
                context.startService(intent)
            }
        } catch (_: Exception) {}
        loadSavedGames()
    }
}
