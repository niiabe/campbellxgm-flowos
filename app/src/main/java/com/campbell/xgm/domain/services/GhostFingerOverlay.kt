package com.campbell.xgm.domain.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class GhostFingerOverlay(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var speedometerView: GhostFingerSpeedometerView? = null
    private var progressView: TextView? = null
    private var appNameView: TextView? = null

    fun showOverlay(totalApps: Int) {
        if (overlayView != null) return
        
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // FlowOS Aesthetic Layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212")) // Dark background
            setPadding(64, 64, 64, 64)
            isClickable = true
            isFocusable = true
            
            // Cyan border
            val border = GradientDrawable()
            border.setColor(Color.parseColor("#121212"))
            border.setStroke(8, Color.parseColor("#00E5FF"))
            border.cornerRadius = 24f
            background = border
        }

        speedometerView = GhostFingerSpeedometerView(context).apply {
            val params = LinearLayout.LayoutParams(600, 600)
            layoutParams = params
        }

        progressView = TextView(context).apply {
            text = "0 / $totalApps"
            setTextColor(Color.WHITE)
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 16)
        }
        
        appNameView = TextView(context).apply {
            text = "Cleaning RAM..."
            setTextColor(Color.parseColor("#00E5FF")) // Cyan text
            textSize = 24f
            gravity = Gravity.CENTER
        }
        
        val stopButton = Button(context).apply {
            text = "SKIP"
            val borderDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                setStroke(6, Color.parseColor("#FF5252")) // Red border
                cornerRadius = 16f
            }
            background = borderDrawable
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 24f
            setPadding(48, 16, 48, 16)
            setOnClickListener {
                SafetyInterceptor.stopForceStop()
                hideOverlay()
            }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 80, 0, 0)
        }

        layout.addView(speedometerView)
        layout.addView(progressView)
        layout.addView(appNameView)
        layout.addView(stopButton, buttonParams)
        
        overlayView = layout

        val layoutFlag = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        layout.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateProgress(current: Int, total: Int, appName: String) {
        val pct = if (total > 0) (current.toFloat() / total.toFloat()) * 100f else 0f
        speedometerView?.setProgress(pct)
        progressView?.text = "$current / $total"
        appNameView?.text = "Closing $appName..."
    }

    fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            speedometerView = null
            progressView = null
            appNameView = null
        }
    }
}
