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
import com.campbell.xgm.theme.campbellxgmTheme
import com.campbell.xgm.util.PermissionUtils

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val prefs = getSharedPreferences("game_mode_prefs", android.content.Context.MODE_PRIVATE)
    val hasAdmin = (getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager)
        .isAdminActive(android.content.ComponentName(this, com.campbell.xgm.domain.services.CampbellAdminReceiver::class.java))
    val hasDnd = (getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val hasWriteSettings = Settings.System.canWrite(this)
    val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)
    val hasUsageStats = try {
        val usm = getSystemService(android.content.Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm?.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        stats != null && stats.isNotEmpty()
    } catch (_: Exception) { false }

    val showOnboarding = !prefs.getBoolean("onboarding_complete", false)

    val startDestination = when {
        showOnboarding -> "onboarding"
        hasAdmin && hasDnd && hasNotifications && hasWriteSettings && hasAccessibility && hasUsageStats -> "dashboard"
        else -> "permissions"
    }

    // Auto-start game launch monitor if enabled
    val autoStartEnabled = prefs.getBoolean("auto_start_game_mode", false)
    if (autoStartEnabled && hasUsageStats) {
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

      campbellxgmTheme(darkTheme = darkModeValue) { 
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
              com.campbell.xgm.ui.AppNavigation(startDestination = startDestination) 
          } 
      }
    }
  }
}
