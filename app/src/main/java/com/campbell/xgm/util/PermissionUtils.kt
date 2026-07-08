package com.campbell.xgm.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object PermissionUtils {
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
}
