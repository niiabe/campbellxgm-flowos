package com.campbell.xgm.domain.services

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

class GhostFingerSpeedometerView(context: Context) : View(context) {
    private var progress = 0f // 0 to 100
    private var customTypeface: Typeface? = null
    
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 30f
        strokeCap = Paint.Cap.ROUND // Smooth modern look instead of square
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 90f
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private val bgArcColor = Color.parseColor("#1E1E24")
    private val fgArcColor = Color.parseColor("#00E5FF")

    fun setCustomTypeface(tf: Typeface?) {
        customTypeface = tf
        textPaint.typeface = tf
        invalidate()
    }

    fun setProgress(pct: Float) {
        progress = pct
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f - 40f
        
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        
        // Background Arc (Dark grey)
        arcPaint.color = bgArcColor
        canvas.drawArc(arcRect, 135f, 270f, false, arcPaint)
        
        // Foreground Arc (Cyan - FlowOS Aesthetic)
        arcPaint.color = fgArcColor
        val sweepAngle = (progress / 100f) * 270f
        canvas.drawArc(arcRect, 135f, sweepAngle, false, arcPaint)
        
        // Percentage text
        canvas.drawText("${progress.toInt()}%", cx, cy + 30f, textPaint)
    }
}
