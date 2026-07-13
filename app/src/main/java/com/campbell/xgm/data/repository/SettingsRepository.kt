package com.campbell.xgm.data.repository

import android.content.Context
import androidx.core.content.edit
import com.campbell.xgm.ui.screens.DnsProvider

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun setBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun setString(key: String, value: String) = prefs.edit { putString(key, value) }

    fun getExcludedApps(): Set<String> {
        val raw = prefs.getString("excluded_apps", "") ?: ""
        return if (raw.isEmpty()) emptySet() else raw.split(",").toSet()
    }

    fun setExcludedApps(apps: Set<String>) {
        prefs.edit { putString("excluded_apps", apps.joinToString(",")) }
    }

    fun getDnsProvider(): DnsProvider {
        val name = prefs.getString("dns_provider", DnsProvider.SYSTEM_DEFAULT.name)
        return try {
            DnsProvider.valueOf(name ?: DnsProvider.SYSTEM_DEFAULT.name)
        } catch (_: Exception) {
            DnsProvider.SYSTEM_DEFAULT
        }
    }

    fun setDnsProvider(provider: DnsProvider) {
        prefs.edit { putString("dns_provider", provider.name) }
    }
}
