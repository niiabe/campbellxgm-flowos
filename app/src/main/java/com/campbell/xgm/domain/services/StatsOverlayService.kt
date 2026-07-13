package com.campbell.xgm.domain.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.campbell.xgm.R
import java.io.File
import java.io.RandomAccessFile

class StatsOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateStats()
            handler.postDelayed(this, 1500)
        }
    }

    companion object {
        fun isRunning(): Boolean {
            return _isRunning
        }
        @Volatile private var _isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
        startForegroundNotification()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_STATS_OVERLAY") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        _isRunning = false
        handler.removeCallbacks(updateRunnable)
        removeOverlay()
        super.onDestroy()
    }

    private fun createOverlay() {
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xCC111111.toInt())
        }

        val params = WindowManager.LayoutParams(
            220,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        try {
            windowManager?.addView(overlayView, params)
            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
            isRunning = false
        } catch (_: Exception) {}
    }

    private fun updateStats() {
        val ramText = getRamUsage()
        val cpuText = getCpuUsage()
        val batteryText = getBatteryInfo()

        overlayView?.let { view ->
            view.removeAllViews()
            view.addView(createTextView("[SYS] System Stats", 0xFF00E5FF.toInt(), 13f, true))
            view.addView(createTextView(ramText, 0xFFFFFFFF.toInt(), 11f, false))
            view.addView(createTextView(cpuText, 0xFFFFFFFF.toInt(), 11f, false))
            view.addView(createTextView(batteryText, 0xFFFFFFFF.toInt(), 11f, false))
        }
    }

    private fun createTextView(text: String, color: Int, sizeSp: Float, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            if (bold) {
                paint.isFakeBoldText = true
            }
        }
    }

    private fun getRamUsage(): String {
        return try {
            val mi = android.app.ActivityManager.MemoryInfo()
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getMemoryInfo(mi)
            val totalMB = mi.totalMem / (1024 * 1024)
            val availMB = mi.availMem / (1024 * 1024)
            val usedMB = totalMB - availMB
            val pct = (usedMB * 100 / totalMB)
            "RAM: ${usedMB}MB / ${totalMB}MB (${pct}%)"
        } catch (_: Exception) { "RAM: N/A" }
    }

    private var prevCpuTotal: Long = -1L
    private var prevCpuIdle: Long = -1L

    private fun getCpuUsage(): String {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split("\\s+".toRegex())
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts[5].toLong()
            val total = user + nice + system + idle + iowait
            val idleAll = idle + iowait

            val pct = if (prevCpuTotal < 0) {
                0
            } else {
                val totalDiff = (total - prevCpuTotal).toDouble()
                val idleDiff = (idleAll - prevCpuIdle).toDouble()
                val usedDiff = totalDiff - idleDiff
                (usedDiff * 100 / totalDiff).toInt().coerceIn(0, 100)
            }
            prevCpuTotal = total
            prevCpuIdle = idleAll
            "CPU: ${pct}% used"
        } catch (_: Exception) { "CPU: N/A" }
    }

    private fun getBatteryInfo(): String {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = level * 100 / scale
            val temp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            "Battery: ${pct}% (${temp}°C)"
        } catch (_: Exception) { "Battery: N/A" }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("stats_overlay", "System Stats", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, StatsOverlayService::class.java).apply {
            action = "STOP_STATS_OVERLAY"
        }
        val pi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "stats_overlay")
            .setContentTitle("System Stats Overlay")
            .setContentText("Showing RAM, CPU, and Battery stats")
            .setSmallIcon(R.drawable.logo)
            .addAction(R.drawable.logo, "Stop", pi)
            .setOngoing(true)
            .build()

        startForeground(2, notification)
    }
}
