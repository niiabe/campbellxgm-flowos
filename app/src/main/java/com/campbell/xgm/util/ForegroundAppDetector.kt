package com.campbell.xgm.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

object ForegroundAppDetector {
    fun getForegroundPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 10_000, now)
            var lastForeground: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground = event.packageName
                }
            }
            lastForeground
        } catch (_: Exception) {
            null
        }
    }

    fun isForeground(context: Context, targetPackage: String): Boolean {
        val foreground = getForegroundPackage(context)
        return foreground == targetPackage || foreground == null
    }
}
