package com.campbell.xgm

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.campbell.xgm.theme.CampbellxgmTheme
import com.campbell.xgm.util.PermissionUtils

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val prefs = getSharedPreferences("game_mode_prefs", android.content.Context.MODE_PRIVATE)
    val permState = PermissionUtils.checkAllPermissions(this)
    val showOnboarding = !prefs.getBoolean("onboarding_complete", false)

    val startDestination = when {
        showOnboarding -> "onboarding"
        permState.allGranted -> "dashboard"
        else -> "permissions"
    }

    // Auto-start game launch monitor if enabled
    val autoStartEnabled = prefs.getBoolean("auto_start_game_mode", false)
    if (autoStartEnabled && permState.hasUsageStats) {
        val monitorIntent = android.content.Intent(this, com.campbell.xgm.domain.services.GameLaunchMonitorService::class.java)
        startForegroundService(monitorIntent)
    }

    enableEdgeToEdge()
    setContent {
      var darkModeValue by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

      DisposableEffect(Unit) {
          val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
              if (key == "dark_mode") {
                  darkModeValue = prefs.getBoolean("dark_mode", true)
              }
          }
          prefs.registerOnSharedPreferenceChangeListener(listener)
          onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
      }

      CampbellxgmTheme(darkTheme = darkModeValue) { 
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
              com.campbell.xgm.ui.AppNavigation(startDestination = startDestination)
          } 
      }
    }
  }
}
