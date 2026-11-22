package com.campbell.xgm.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class GameProfile(
    val aggressiveFreezing: Boolean = true,
    val dndMode: Boolean = false,
    val pingStabilizer: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val autoBrightnessLock: Boolean = true,
    val fpsOverlay: Boolean = false,
    val statsOverlay: Boolean = false,
    val networkBoost: Boolean = true,
    val notificationFilter: Boolean = false
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("aggressiveFreezing", aggressiveFreezing)
            put("dndMode", dndMode)
            put("pingStabilizer", pingStabilizer)
            put("keepScreenAwake", keepScreenAwake)
            put("autoBrightnessLock", autoBrightnessLock)
            put("fpsOverlay", fpsOverlay)
            put("statsOverlay", statsOverlay)
            put("networkBoost", networkBoost)
            put("notificationFilter", notificationFilter)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): GameProfile {
            return try {
                val obj = JSONObject(json)
                GameProfile(
                    aggressiveFreezing = obj.optBoolean("aggressiveFreezing", true),
                    dndMode = obj.optBoolean("dndMode", false),
                    pingStabilizer = obj.optBoolean("pingStabilizer", false),
                    keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
                    autoBrightnessLock = obj.optBoolean("autoBrightnessLock", true),
                    fpsOverlay = obj.optBoolean("fpsOverlay", false),
                    statsOverlay = obj.optBoolean("statsOverlay", false),
                    networkBoost = obj.optBoolean("networkBoost", true),
                    notificationFilter = obj.optBoolean("notificationFilter", false)
                )
            } catch (_: Exception) {
                GameProfile()
            }
        }

        fun getForPackage(context: Context, packageName: String): GameProfile {
            val prefs = context.getSharedPreferences("game_profiles", Context.MODE_PRIVATE)
            val json = prefs.getString(packageName, null) ?: return GameProfile()
            return fromJson(json)
        }

        fun saveForPackage(context: Context, packageName: String, profile: GameProfile) {
            val prefs = context.getSharedPreferences("game_profiles", Context.MODE_PRIVATE)
            prefs.edit().putString(packageName, profile.toJson()).apply()
        }

        fun hasProfile(context: Context, packageName: String): Boolean {
            val prefs = context.getSharedPreferences("game_profiles", Context.MODE_PRIVATE)
            return prefs.contains(packageName)
        }
    }
}
