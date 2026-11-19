package com.poolvision.training

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.*
import android.widget.Toast
import kotlin.math.hypot

class AimOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: AimView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = AimView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(overlay, params)
        Toast.makeText(this, "Pool Vision overlay active. Drag the handles; tap CLOSE to exit.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        overlay?.let { windowManager.removeView(it) }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class AimView : View(this@AimOverlayService) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 214, 176); strokeWidth = 5f; style = Paint.Style.STROKE
        }
        private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 209, 102); strokeWidth = 4f; style = Paint.Style.STROKE
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.FILL
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 34f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private var cue = PointF(180f, 600f)
        private var target = PointF(600f, 400f)
        private var active = 0
        private val radius = 25f
        private val closeRect = RectF(30f, 70f, 210f, 135f)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Light translucent labels; the rest of the view stays transparent.
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x990A121D.toInt() }
            canvas.drawRoundRect(closeRect, 18f, 18f, bg)
            canvas.drawText("CLOSE", 72f, 113f, labelPaint)

            canvas.drawLine(cue.x, cue.y, target.x, target.y, linePaint)
            val dx = target.x - cue.x
            val dy = target.y - cue.y
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val endX = target.x + dx / len * 220f
            val endY = target.y + dy / len * 220f
            canvas.drawLine(target.x, target.y, endX, endY, targetPaint)
            canvas.drawCircle(cue.x, cue.y, radius, handlePaint)
            canvas.drawCircle(target.x, target.y, radius, targetPaint)
            val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
            canvas.drawCircle(cue.x, cue.y, 9f, dark)
            canvas.drawText("CUE", cue.x + 32f, cue.y - 18f, labelPaint)
            canvas.drawText("TARGET", target.x + 32f, target.y - 18f, labelPaint)
        }

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (closeRect.contains(x, y)) {
                        stopSelf()
                        return true
                    }
                    active = if (hypot(x - cue.x, y - cue.y) < hypot(x - target.x, y - target.y)) 1 else 2
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active == 1) { cue = PointF(x, y); invalidate() }
                    if (active == 2) { target = PointF(x, y); invalidate() }
                    return true
                }
                MotionEvent.ACTION_UP -> { active = 0; return true }
            }
            return true
        }
    }
}
