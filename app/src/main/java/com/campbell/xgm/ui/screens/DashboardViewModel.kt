package com.campbell.xgm.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campbell.xgm.data.local.GameTarget
import kotlinx.coroutines.Dispatchers
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("saved_games_prefs", Context.MODE_PRIVATE)

    private val _allowedGames = MutableStateFlow<List<GameTarget>>(emptyList())
    val allowedGames: StateFlow<List<GameTarget>> = _allowedGames

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    init {
        loadSavedGames()
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
            
            val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
            val apps = resolveInfos.mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                val name = info.loadLabel(packageManager).toString()
                // Skip our own app and known system apps
                if (packageName == getApplication<Application>().packageName) return@mapNotNull null
                if (isSystemPackage(packageName)) return@mapNotNull null
                val icon = try { info.loadIcon(packageManager) } catch (_: Exception) { null }
                AppInfo(packageName, name, icon)
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
        // Stop PipelineService if it's running for this game
        try {
            val context = getApplication<Application>()
            val intent = android.content.Intent(context, com.campbell.xgm.domain.services.PipelineService::class.java)
            intent.action = "STOP_GAME_MODE"
            context.startService(intent)
        } catch (_: Exception) {}
        loadSavedGames()
    }
}
