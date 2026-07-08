package com.campbell.xgm.ui.screens

import android.app.Application
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.campbell.xgm.domain.services.CampbellAdminReceiver
import com.campbell.xgm.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _hasAdmin = MutableStateFlow(false)
    val hasAdmin: StateFlow<Boolean> = _hasAdmin.asStateFlow()

    private val _hasDnd = MutableStateFlow(false)
    val hasDnd: StateFlow<Boolean> = _hasDnd.asStateFlow()

    private val _hasNotifications = MutableStateFlow(false)
    val hasNotifications: StateFlow<Boolean> = _hasNotifications.asStateFlow()

    private val _hasWriteSettings = MutableStateFlow(false)
    val hasWriteSettings: StateFlow<Boolean> = _hasWriteSettings.asStateFlow()

    private val _hasAccessibility = MutableStateFlow(false)
    val hasAccessibility: StateFlow<Boolean> = _hasAccessibility.asStateFlow()

    fun checkPermissions() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, CampbellAdminReceiver::class.java)
        _hasAdmin.value = dpm.isAdminActive(adminComponent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        _hasDnd.value = notificationManager.isNotificationPolicyAccessGranted

        _hasNotifications.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _hasWriteSettings.value = Settings.System.canWrite(context)

        _hasAccessibility.value = PermissionUtils.isAccessibilityServiceEnabled(context)
    }
}
