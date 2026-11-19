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
    private var aimView: AimView? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        aimView = AimView()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(aimView, params)

        Toast.makeText(
            this,
            "Pool Vision Activated",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        aimView?.let {
            windowManager.removeView(it)
        }
        aimView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    inner class AimView : View(this@AimOverlayService) {

        private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 214, 176)
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

        private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
        }

        private var cue = PointF(180f, 600f)
        private var target = PointF(600f, 400f)

        private var selected = 0

        private val closeArea = RectF(30f, 70f, 210f, 135f)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x990A121D.toInt()
            }

            canvas.drawRoundRect(
                closeArea, 18f, 18f, background
            )

            canvas.drawText(
                "CLOSE", 65f, 113f, textPaint
            )

            val dx = target.x - cue.x
            val dy = target.y - cue.y
            val distance = hypot(dx, dy).coerceAtLeast(1f)

            canvas.drawLine(
                cue.x, cue.y,
                target.x, target.y,
                green
            )

            val endX = target.x + dx / distance * 220f
            val endY = target.y + dy / distance * 220f

            canvas.drawLine(
                target.x, target.y,
                endX, endY,
                gold
            )

            canvas.drawCircle(cue.x, cue.y, 23f, white)
            canvas.drawCircle(target.x, target.y, 23f, gold)

            canvas.drawText(
                "CUE", cue.x + 30f, cue.y - 20f, textPaint
            )

            canvas.drawText(
                "TARGET", target.x + 30f,
                target.y - 20f, textPaint
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    if (closeArea.contains(event.x, event.y)) {
                        this@AimOverlayService.stopSelf()
                        return true
                    }

                    val cueDistance = hypot(
                        event.x - cue.x,
                        event.y - cue.y
                    )

                    val targetDistance = hypot(
                        event.x - target.x,
                        event.y - target.y
                    )

                    selected = if (cueDistance < targetDistance) 1 else 2
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    when (selected) {
                        1 -> cue = PointF(event.x, event.y)
                        2 -> target = PointF(event.x, event.y)
                    }

                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    selected = 0
                    return true
                }
            }

            return true
        }
    }
}
