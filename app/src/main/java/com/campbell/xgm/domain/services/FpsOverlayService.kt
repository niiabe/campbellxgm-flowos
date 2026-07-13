package com.campbell.xgm.domain.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.campbell.xgm.R
import com.campbell.xgm.util.ForegroundAppDetector
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

class FpsOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "campbellxgm_fps_channel"
        private const val NOTIFICATION_ID = 9002
        private const val FPS_POLL_INTERVAL_MS = 3000L
    }

    private var windowManager: WindowManager? = null
    private var fpsTextView: TextView? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var fpsPollJob: Job? = null
    // Per-package frame-count baseline so FPS delta is computed within a single process.
    private val frameBaselines = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        showFpsOverlay()
    }

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FPS Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FPS Overlay Active")
            .setContentText("Showing real-time frame rate.")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showFpsOverlay() {
        fpsTextView = TextView(this).apply {
            text = "FPS: --"
            setTextColor(Color.GREEN)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(16, 8, 16, 8)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 50
        }

        try {
            windowManager?.addView(fpsTextView, params)
            startFpsPolling()
        } catch (e: Exception) {
            Log.e("FpsOverlayService", "Failed to add overlay view: ${e.message}")
        }
    }

    private fun startFpsPolling() {
        fpsPollJob = serviceScope.launch {
            while (isActive) {
                delay(FPS_POLL_INTERVAL_MS)
                val fps = measureGameFps()
                withContext(Dispatchers.Main) {
                    fpsTextView?.text = if (fps >= 0) "FPS: $fps" else "FPS: --"
                }
            }
        }
    }

    private fun measureGameFps(): Int {
        val foregroundPkg = ForegroundAppDetector.getForegroundPackage(this) ?: return -1
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "gfxinfo", foregroundPkg))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var totalFrames = 0L
            var jankyFrames = 0L
            reader.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Total frames rendered:")) {
                        totalFrames = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                    }
                    if (trimmed.startsWith("Janky frames:")) {
                        val jankyPart = trimmed.substringAfter(":").trim()
                        jankyFrames = jankyPart.substringBefore("(").trim().toLongOrNull() ?: 0L
                    }
                }
            }
            process.waitFor()

            if (totalFrames > 0) {
                val prev = frameBaselines[foregroundPkg] ?: 0L
                val delta = (totalFrames - prev).coerceAtLeast(0)
                frameBaselines[foregroundPkg] = totalFrames
                // Return delta as approximate FPS (frames rendered in last second)
                delta.toInt().coerceIn(0, 240)
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.d("FpsOverlayService", "dumpsys gfxinfo unavailable: ${e.message}")
            -1
        }
    }

    override fun onDestroy() {
        fpsPollJob?.cancel()
        fpsPollJob = null
        try {
            fpsTextView?.let {
                windowManager?.removeView(it)
            }
        } catch (e: Exception) {
            Log.e("FpsOverlayService", "Failed to remove overlay: ${e.message}")
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
