package com.campbell.xgm.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.campbell.xgm.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _hasUsageStats = MutableStateFlow(false)
    val hasUsageStats: StateFlow<Boolean> = _hasUsageStats.asStateFlow()

    private val _hasNotificationAccess = MutableStateFlow(false)
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    fun checkPermissions() {
        val app = getApplication<Application>()
        val state = PermissionUtils.checkAllPermissions(app)
        _hasAdmin.value = state.hasAdmin
        _hasDnd.value = state.hasDnd
        _hasNotifications.value = state.hasNotifications
        _hasWriteSettings.value = state.hasWriteSettings
        _hasAccessibility.value = state.hasAccessibility
        _hasUsageStats.value = state.hasUsageStats
        _hasNotificationAccess.value = PermissionUtils.isNotificationListenerEnabled(app)
    }
}
