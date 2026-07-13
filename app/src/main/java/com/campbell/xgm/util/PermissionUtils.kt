package com.campbell.xgm.util

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.campbell.xgm.domain.services.CampbellAdminReceiver

object PermissionUtils {
    data class PermissionState(
        val hasAdmin: Boolean,
        val hasDnd: Boolean,
        val hasNotifications: Boolean,
        val hasWriteSettings: Boolean,
        val hasAccessibility: Boolean,
        val hasUsageStats: Boolean
    ) {
        val allGranted: Boolean get() = hasAdmin && hasDnd && hasNotifications && hasWriteSettings && hasAccessibility && hasUsageStats
    }

    fun checkAllPermissions(context: Context): PermissionState {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, CampbellAdminReceiver::class.java)
        val hasAdmin = dpm.isAdminActive(adminComponent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasDnd = notificationManager.isNotificationPolicyAccessGranted

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasWriteSettings = Settings.System.canWrite(context)
        val hasAccessibility = isAccessibilityServiceEnabled(context)
        val hasUsageStats = isUsageStatsGranted(context)

        return PermissionState(
            hasAdmin = hasAdmin,
            hasDnd = hasDnd,
            hasNotifications = hasNotifications,
            hasWriteSettings = hasWriteSettings,
            hasAccessibility = hasAccessibility,
            hasUsageStats = hasUsageStats
        )
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/com.campbell.xgm.domain.services.SafetyInterceptor"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(service, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun isDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, CampbellAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isUsageStatsGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun isUsageStatsAvailable(context: Context): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            stats != null && stats.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            val component = ComponentName(context, com.campbell.xgm.domain.services.MediaSessionListenerService::class.java)
            enabled?.split(":")?.any { it.equals(component.flattenToString(), ignoreCase = true) } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
