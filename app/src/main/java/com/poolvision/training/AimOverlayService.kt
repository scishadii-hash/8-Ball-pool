package com.poolvision.training

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val EPS = 1e-6f

class AimOverlayService : Service() {

    private var wm: WindowManager? = null
    private var canvasView: AimCanvasView? = null
    private var panelView: View? = null
    private var editing = true

    override fun onBind(intent: Intent?): IBinder? = null

    private fun canvasFlags(edit: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!edit) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (canvasView != null) return START_NOT_STICKY
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager

        val cv = AimCanvasView(this)
        val cp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            canvasFlags(true),
            PixelFormat.TRANSLUCENT
        )
        cp.gravity = Gravity.TOP or Gravity.START
        cp.alpha = 0.8f
        manager.addView(cv, cp)
        canvasView = cv

        val pp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        pp.gravity = Gravity.TOP or Gravity.START
        pp.x = 24
        pp.y = 160
        val panel = buildPanel(manager, cv, cp, pp)
        manager.addView(panel, pp)
        panelView = panel
        return START_NOT_STICKY
    }

    private fun buildPanel(
        manager: WindowManager,
        cv: AimCanvasView,
        cp: WindowManager.LayoutParams,
        pp: WindowManager.LayoutParams
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        val bgd = GradientDrawable()
        bgd.setColor(Color.argb(235, 24, 24, 32))
        bgd.cornerRadius = 24 * dp
        bgd.setStroke((1 * dp).toInt(), Color.argb(120, 255, 255, 255))
        box.background = bgd
        box.setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())

        fun chip(text: String): TextView {
            val t = TextView(this)
            t.text = text
            t.setTextColor(Color.WHITE)
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            return t
        }

        val handle = chip("≡")
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = pp.x
                    startY = pp.y
                    downX = e.rawX
                    downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    pp.x = startX + (e.rawX - downX).toInt()
                    pp.y = startY + (e.rawY - downY).toInt()
                    manager.updateViewLayout(box, pp)
                }
            }
            true
        }

        val editBtn = chip("✓ تثبيت")
        editBtn.setOnClickListener {
            editing = !editing
            editBtn.text = if (editing) "✓ تثبيت" else "✎ تعديل"
            cv.editing = editing
            cp.flags = canvasFlags(editing)
            manager.updateViewLayout(cv, cp)
        }

        val addBtn = chip("＋كرة")
        addBtn.setOnClickListener { cv.addBall() }

        val remBtn = chip("－")
        remBtn.setOnClickListener { cv.removeBall() }

        val resetBtn = chip("↺")
        resetBtn.setOnClickListener { cv.resetDefaults() }

        val closeBtn = chip("✕")
        closeBtn.setOnClickListener { stopSelf() }

        box.addView(handle)
        box.addView(editBtn)
        box.addView(addBtn)
        box.addView(remBtn)
        box.addView(resetBtn)
        box.addView(closeBtn)
        return box
    }

    override fun onDestroy() {
        try {
            canvasView?.let { wm?.removeView(it) }
            panelView?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        canvasView = null
        panelView = null
        super.onDestroy()
    }
}

class TraceResult(
    val pts: ArrayList<PointF>,
    val ghost: PointF?,
    val dx: Float,
    val dy: Float,
    val hit: Int
)

class AimCanvasView(ctx: Context) : View(ctx) {

    var editing = true
        set(v) {
            field = v
            invalidate()
        }

    private val prefs = ctx.getSharedPreferences("pv", Context.MODE_PRIVATE)
    private val dp = ctx.resources.displayMetrics.density

    private val cue = PointF()
    private val aim = PointF()
    private val tl = PointF()
    private val br = PointF()
    private val balls = ArrayList<PointF>()
    private var active: PointF? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 13 * dp
    }

    private fun tag() = if (width > height) "L" else "P"

    private fun allPts(): List<PointF> {
        val l = ArrayList<PointF>()
        l.add(aim)
        l.add(cue)
        l.add(tl)
        l.add(br)
        l.addAll(balls)
        return l
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!load()) resetDefaults()
    }

    fun resetDefaults() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w > h) {
            tl.set(w * 0.18f, h * 0.21f)
            br.set(w * 0.82f, h * 0.94f)
            cue.set(w * 0.35f, h * 0.45f)
            aim.set(w * 0.50f, h * 0.50f)
            balls.clear()
            balls.add(PointF(w * 0.60f, h * 0.55f))
        } else {
            tl.set(w * 0.12f, h * 0.25f)
            br.set(w * 0.88f, h * 0.75f)
            cue.set(w * 0.35f, h * 0.60f)
            aim.set(w * 0.55f, h * 0.45f)
            balls.clear()
            balls.add(PointF(w * 0.60f, h * 0.40f))
        }
        save()
        invalidate()
    }

    fun addBall() {
        if (balls.size >= 15) return
        balls.add(PointF(width * 0.5f, height * 0.5f + balls.size * 10 * dp))
        save()
        invalidate()
    }

    fun removeBall() {
        if (balls.size > 1) {
            balls.removeAt(balls.size - 1)
            save()
            invalidate()
        }
    }

    private fun save() {
        val e = prefs.edit()
        val t = tag()
        val fixed = arrayOf(aim, cue, tl, br)
        val names = arrayOf("aim", "cue", "tl", "br")
        for (i in fixed.indices) {
            e.putFloat(names[i] + t + ".x", fixed[i].x)
            e.putFloat(names[i] + t + ".y", fixed[i].y)
        }
        e.putInt("n" + t, balls.size)
        for (i in balls.indices) {
            e.putFloat("b" + i + t + ".x", balls[i].x)
            e.putFloat("b" + i + t + ".y", balls[i].y)
        }
        e.apply()
    }

    private fun load(): Boolean {
        val t = tag()
        if (!prefs.contains("n" + t)) return false
        val fixed = arrayOf(aim, cue, tl, br)
        val names = arrayOf("aim", "cue", "tl", "br")
        for (i in fixed.indices) {
            val x = prefs.getFloat(names[i] + t + ".x", -1f)
            val y = prefs.getFloat(names[i] + t + ".y", -1f)
            if (x < 0f || y < 0f || x > width || y > height) return false
            fixed[i].set(x, y)
        }
        val n = prefs.getInt("n" + t, 0)
        if (n < 1) return false
        balls.clear()
        for (i in 0 until n) {
            val x = prefs.getFloat("b" + i + t + ".x", -1f)
            val y = prefs.getFloat("b" + i + t + ".y", -1f)
            if (x < 0f || y < 0f || x > width || y > height) return false
            balls.add(PointF(x, y))
        }
        return true
    }

    private fun pick(x: Float, y: Float): PointF? {
        var best: PointF? = null
        var bd = 60 * dp
        for (p in allPts()) {
            val d = hypot(p.x - x, p.y - y)
            if (d < bd) {
                bd = d
                best = p
            }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editing) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = pick(e.x, e.y)
                return active != null
            }
            MotionEvent.ACTION_MOVE -> {
                active?.set(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (active != null) save()
                active = null
            }
        }
        return true
    }

    private fun trace(
        sx: Float, sy: Float, dirX: Float, dirY: Float,
        maxBounces: Int, bounds: RectF, tgts: List<PointF>, hitR: Float
    ): TraceResult {
        val out = arrayListOf(PointF(sx, sy))
        var px = sx
        var py = sy
        var dx = dirX
        var dy = dirY
        for (i in 0..maxBounces) {
            var tWall = Float.MAX_VALUE
            var vertical = false
            if (dx > EPS) {
                val t = (bounds.right - px) / dx
                if (t < tWall) { tWall = t; vertical = true }
            } else if (dx < -EPS) {
                val t = (bounds.left - px) / dx
                if (t < tWall) { tWall = t; vertical = true }
            }
            if (dy > EPS) {
                val t = (bounds.bottom - py) / dy
                if (t < tWall) { tWall = t; vertical = false }
            } else if (dy < -EPS) {
                val t = (bounds.top - py) / dy
                if (t < tWall) { tWall = t; vertical = false }
            }
            if (tWall == Float.MAX_VALUE) break
            if (tWall < 0f) tWall = 0f

            var bestT = Float.MAX_VALUE
            var bestI = -1
            for (k in tgts.indices) {
                val fx = px - tgts[k].x
                val fy = py - tgts[k].y
                val b = fx * dx + fy * dy
                val c = fx * fx + fy * fy - hitR * hitR
                val disc = b * b - c
                if (disc >= 0f) {
                    val t = -b - sqrt(disc)
                    if (t > 0f && t < tWall && t < bestT) {
                        bestT = t
                        bestI = k
                    }
                }
            }
            if (bestI >= 0) {
                val g = PointF(px + dx * bestT, py + dy * bestT)
                out.add(g)
                return TraceResult(out, g, dx, dy, bestI)
            }

            px += dx * tWall
            py += dy * tWall
            out.add(PointF(px, py))
            if (vertical) dx = -dx else dy = -dy
        }
        return TraceResult(out, null, dx, dy, -1)
    }

    private fun poly(c: Canvas, p: List<PointF>, paint: Paint) {
        for (i in 0 until p.size - 1) {
            c.drawLine(p[i].x, p[i].y, p[i + 1].x, p[i + 1].y, paint)
        }
    }

    override fun onDraw(c: Canvas) {
        val r = prefs.getInt("radius", 14) * dp
        val alpha = prefs.getInt("alpha", 200)
        val color = prefs.getInt("color", Color.rgb(0, 230, 118))
        val maxB = prefs.getInt("bounces", 3)
        val lw = prefs.getInt("width", 3) * dp

        val table = RectF(min(tl.x, br.x), min(tl.y, br.y), max(tl.x, br.x), max(tl.y, br.y))

        if (editing) {
            strokePaint.color = Color.argb(160, 120, 200, 255)
            strokePaint.strokeWidth = 2 * dp
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(14 * dp, 8 * dp), 0f)
            c.drawRect(table, strokePaint)
            strokePaint.pathEffect = null
        }

        val bounds = RectF(table.left + r, table.top + r, table.right - r, table.bottom - r)
        if (bounds.width() > 10f && bounds.height() > 10f) {
            val cc = PointF(
                cue.x.coerceIn(bounds.left, bounds.right),
                cue.y.coerceIn(bounds.top, bounds.bottom)
            )
            val bc = ArrayList<PointF>()
            for (b in balls) {
                bc.add(
                    PointF(
                        b.x.coerceIn(bounds.left, bounds.right),
                        b.y.coerceIn(bounds.top, bounds.bottom)
                    )
                )
            }

            fillPaint.color = Color.WHITE
            fillPaint.alpha = 70
            c.drawCircle(cc.x, cc.y, r, fillPaint)
            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 2 * dp
            c.drawCircle(cc.x, cc.y, r, strokePaint)

            for (b in bc) {
                fillPaint.color = Color.rgb(255, 170, 0)
                fillPaint.alpha = 70
                c.drawCircle(b.x, b.y, r, fillPaint)
                strokePaint.color = Color.rgb(255, 170, 0)
                c.drawCircle(b.x, b.y, r, strokePaint)
            }

            val vx = aim.x - cc.x
            val vy = aim.y - cc.y
            val len = hypot(vx, vy)
            if (len > 1f) {
                val ux = vx / len
                val uy = vy / len

                linePaint.strokeWidth = lw
                linePaint.color = color
                linePaint.alpha = alpha
                val res = trace(cc.x, cc.y, ux, uy, maxB, bounds, bc, 2 * r)
                poly(c, res.pts, linePaint)

                val ghost = res.ghost
                if (ghost != null && res.hit >= 0) {
                    val tc = bc[res.hit]
                    strokePaint.color = Color.WHITE
                    strokePaint.alpha = alpha
                    c.drawCircle(ghost.x, ghost.y, r, strokePaint)

                    var nx = tc.x - ghost.x
                    var ny = tc.y - ghost.y
                    val nl = hypot(nx, ny)
                    if (nl > EPS) {
                        nx /= nl
                        ny /= nl
                        val tr = trace(tc.x, tc.y, nx, ny, maxB, bounds, emptyList(), 0f)
                        linePaint.color = Color.rgb(255, 170, 0)
                        linePaint.alpha = alpha
                        poly(c, tr.pts, linePaint)

                        val dot = res.dx * nx + res.dy * ny
                        var tx = res.dx - dot * nx
                        var ty = res.dy - dot * ny
                        val tlen = hypot(tx, ty)
                        if (tlen > 0.05f) {
                            tx /= tlen
                            ty /= tlen
                            linePaint.color = Color.WHITE
                            linePaint.alpha = alpha / 2
                            c.drawLine(
                                ghost.x, ghost.y,
                                ghost.x + tx * 120 * dp, ghost.y + ty * 120 * dp, linePaint
                            )
                        }
                    }
                }
            }
        }

        if (editing) {
            fillPaint.color = Color.rgb(255, 82, 82)
            fillPaint.alpha = 255
            c.drawCircle(aim.x, aim.y, 12 * dp, fillPaint)
            c.drawText("توجيه", aim.x, aim.y - 18 * dp, textPaint)
            c.drawText("أبيض", cue.x, cue.y - 28 * dp, textPaint)
            for (i in balls.indices) {
                c.drawText("هدف " + (i + 1), balls[i].x, balls[i].y - 28 * dp, textPaint)
            }

            fillPaint.color = Color.rgb(0, 200, 255)
            fillPaint.alpha = 255
            for (p in arrayOf(tl, br)) {
                c.drawRect(p.x - 10 * dp, p.y - 10 * dp, p.x + 10 * dp, p.y + 10 * dp, fillPaint)
            }
            c.drawText("ركن الطاولة", tl.x, tl.y - 16 * dp, textPaint)
            c.drawText("ركن الطاولة", br.x, br.y + 28 * dp, textPaint)
            c.drawText("وضع التعديل: حرّك النقاط ثم اضغط ✓ تثبيت", width / 2f, 90 * dp, textPaint)
        }
    }
}
