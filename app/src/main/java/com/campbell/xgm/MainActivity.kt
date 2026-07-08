package com.campbell.xgm

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.campbell.xgm.theme.campbellxgmTheme
import com.campbell.xgm.util.PermissionUtils

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
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

    val startDestination = if (hasAdmin && hasDnd && hasNotifications && hasWriteSettings && hasAccessibility) {
        "dashboard"
    } else {
        "permissions"
    }

    enableEdgeToEdge()
    setContent {
      campbellxgmTheme { 
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
              com.campbell.xgm.ui.AppNavigation(startDestination = startDestination) 
          } 
      }
    }
  }
}
