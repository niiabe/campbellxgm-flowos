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

/**
 * Remembers the last app that came to the foreground across polls. The stateless
 * [ForegroundAppDetector.getForegroundPackage] only sees the last 10 seconds, so during a long
 * gaming session (no new events) it returns null and cannot tell what is on screen.
 */
class ForegroundTracker(private val context: Context) {
    private var lastQueryTime = System.currentTimeMillis() - 10_000
    var current: String? = null
        private set

    /** Returns the latest known foreground package, or null if Usage Access is not granted. */
    fun poll(): String? {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return current
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(lastQueryTime, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    current = event.packageName
                }
            }
            lastQueryTime = now
        } catch (_: Exception) {
        }
        return current
    }
}

/**
 * Decides when the user has really left the game. Pure logic so it can be unit tested.
 *
 * - Before the game has been seen on screen, waits up to [graceMs] (slow cold starts).
 * - Overlays such as the system UI, keyboard or Play Games sign-in ([ignored]) never count as leaving.
 * - Another app must be seen [requiredMisses] polls in a row before ending game mode.
 * - Unknown foreground (no Usage Access) never ends game mode.
 */
class TeardownPolicy(
    private val target: String,
    private val ignored: Set<String>,
    private val graceMs: Long = 60_000,
    private val requiredMisses: Int = 2,
) {
    private var seenTarget = false
    private var misses = 0

    /** @return true when game mode should be ended. */
    fun onSample(foreground: String?, elapsedMs: Long): Boolean {
        if (foreground == null) return false
        if (foreground == target) {
            seenTarget = true
            misses = 0
            return false
        }
        if (!seenTarget) return elapsedMs >= graceMs
        if (foreground in ignored) return false
        misses++
        return misses >= requiredMisses
    }
}
