package com.campbell.xgm.domain.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.campbell.xgm.R
import kotlinx.coroutines.*

class GameLaunchMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "campbellxgm_monitor_channel"
        private const val NOTIFICATION_ID = 9003
        private const val CHECK_INTERVAL_MS = 3000L
        private const val PREFS_NAME = "saved_games_prefs"
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var monitorJob: Job? = null
    private var lastForegroundApp: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_MONITOR") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        // Only start monitoring if not already monitoring
        if (monitorJob?.isActive != true) {
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Game Launch Monitor",
            NotificationManager.IMPORTANCE_MIN
        )
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Game Monitor Active")
            .setContentText("Watching for game launches...")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        monitorJob = serviceScope.launch {
            while (isActive) {
                // Reload saved games each cycle to pick up additions/removals
                val savedGames = getSavedGamePackages()
                if (savedGames.isEmpty()) {
                    Log.d("GameLaunchMonitor", "No saved games yet, waiting...")
                    delay(CHECK_INTERVAL_MS * 5)
                    continue
                }

                delay(CHECK_INTERVAL_MS)
                val foregroundApp = getForegroundApp() ?: continue

                if (foregroundApp != lastForegroundApp) {
                    lastForegroundApp = foregroundApp
                    if (savedGames.contains(foregroundApp)) {
                        if (!isPipelineServiceRunning()) {
                            Log.i("GameLaunchMonitor", "Game detected: $foregroundApp - starting PipelineService")
                            val pipelineIntent = Intent(this@GameLaunchMonitorService, PipelineService::class.java).apply {
                                putExtra("TARGET_PACKAGE", foregroundApp)
                            }
                            startForegroundService(pipelineIntent)
                        }
                    }
                }
            }
        }
    }

    private fun getForegroundApp(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 5_000, now)
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
        } catch (e: Exception) {
            null
        }
    }

    private fun getSavedGamePackages(): Set<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys
    }

    private fun isPipelineServiceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        val services = am.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == PipelineService::class.java.name }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
