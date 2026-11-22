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
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
        box.orientation = LinearLayout.VERTICAL
        val bgd = GradientDrawable()
        bgd.setColor(Color.argb(235, 24, 24, 32))
        bgd.cornerRadius = 20 * dp
        bgd.setStroke((1 * dp).toInt(), Color.argb(120, 255, 255, 255))
        box.background = bgd
        box.setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())

        fun chip(text: String): TextView {
            val t = TextView(this)
            t.text = text
            t.setTextColor(Color.WHITE)
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            return t
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

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
            cv.mode = 0
            cv.editing = editing
            cp.flags = canvasFlags(editing)
            manager.updateViewLayout(cv, cp)
        }

        val cueBtn = chip("⚪")
        cueBtn.setOnClickListener {
            if (!editing) editBtn.performClick()
            cv.mode = 1
        }

        val ballBtn = chip("＋")
        ballBtn.setOnClickListener {
            if (!editing) editBtn.performClick()
            cv.mode = 2
        }

        val remBtn = chip("－")
        remBtn.setOnClickListener { cv.removeBall() }

        val resetBtn = chip("↺")
        resetBtn.setOnClickListener { cv.resetDefaults() }

        val closeBtn = chip("✕")
        closeBtn.setOnClickListener { stopSelf() }

        row.addView(handle)
        row.addView(editBtn)
        row.addView(cueBtn)
        row.addView(ballBtn)
        row.addView(remBtn)
        row.addView(resetBtn)
        row.addView(closeBtn)

        val powerRow = LinearLayout(this)
        powerRow.orientation = LinearLayout.HORIZONTAL
        powerRow.gravity = Gravity.CENTER_VERTICAL
        val powerLabel = TextView(this)
        powerLabel.setTextColor(Color.WHITE)
        powerLabel.textSize = 13f
        powerLabel.setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        powerLabel.text = "قوة " + cv.power + "%"
        val sb = SeekBar(this)
        sb.max = 90
        sb.progress = cv.power - 10
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                cv.power = p + 10
                powerLabel.text = "قوة " + (p + 10) + "%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        powerRow.addView(powerLabel)
        powerRow.addView(sb, LinearLayout.LayoutParams((190 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))

        box.addView(row)
        box.addView(powerRow)
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
    val hit: Int,
    val left: Float
)

class AimCanvasView(ctx: Context) : View(ctx) {

    var editing = true
        set(v) {
            field = v
            invalidate()
        }

    // 0 = عادي، 1 = المس مكان الكرة البيضاء، 2 = المس مكان كرة هدف
    var mode = 0
        set(v) {
            field = v
            invalidate()
        }

    private val prefs = ctx.getSharedPreferences("pv", Context.MODE_PRIVATE)
    private val dp = ctx.resources.displayMetrics.density

    var power = prefs.getInt("power", 100)
        set(v) {
            field = v
            prefs.edit().putInt("power", v).apply()
            invalidate()
        }

    private val cue = PointF()
    private val aimPt = PointF()
    private var ang = -0.5f
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

    private fun updateAim() {
        val h = 80 * dp
        aimPt.set(cue.x + cos(ang) * h, cue.y + sin(ang) * h)
    }

    private fun allPts(): List<PointF> {
        val l = ArrayList<PointF>()
        l.add(aimPt)
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
        balls.clear()
        if (w > h) {
            tl.set(w * 0.18f, h * 0.21f)
            br.set(w * 0.82f, h * 0.94f)
            cue.set(w * 0.35f, h * 0.55f)
            ang = -0.45f
            balls.add(PointF(w * 0.60f, h * 0.45f))
        } else {
            tl.set(w * 0.12f, h * 0.25f)
            br.set(w * 0.88f, h * 0.75f)
            cue.set(w * 0.35f, h * 0.60f)
            ang = -0.9f
            balls.add(PointF(w * 0.60f, h * 0.40f))
        }
        updateAim()
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
        val fixed = arrayOf(cue, tl, br)
        val names = arrayOf("cue", "tl", "br")
        for (i in fixed.indices) {
            e.putFloat(names[i] + t + ".x", fixed[i].x)
            e.putFloat(names[i] + t + ".y", fixed[i].y)
        }
        e.putFloat("ang" + t, ang)
        e.putInt("n" + t, balls.size)
        for (i in balls.indices) {
            e.putFloat("b" + i + t + ".x", balls[i].x)
            e.putFloat("b" + i + t + ".y", balls[i].y)
        }
        e.apply()
    }

    private fun load(): Boolean {
        val t = tag()
        if (!prefs.contains("ang" + t)) return false
        val fixed = arrayOf(cue, tl, br)
        val names = arrayOf("cue", "tl", "br")
        for (i in fixed.indices) {
            val x = prefs.getFloat(names[i] + t + ".x", -1f)
            val y = prefs.getFloat(names[i] + t + ".y", -1f)
            if (x < 0f || y < 0f || x > width || y > height) return false
            fixed[i].set(x, y)
        }
        ang = prefs.getFloat("ang" + t, -0.5f)
        val n = prefs.getInt("n" + t, 0)
        if (n < 1) return false
        balls.clear()
        for (i in 0 until n) {
            val x = prefs.getFloat("b" + i + t + ".x", -1f)
            val y = prefs.getFloat("b" + i + t + ".y", -1f)
            if (x < 0f || y < 0f || x > width || y > height) return false
            balls.add(PointF(x, y))
        }
        updateAim()
        return true
    }

    private fun pick(x: Float, y: Float): PointF? {
        var best: PointF? = null
        var bd = 50 * dp
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
                var p = pick(e.x, e.y)
                if (p == null && mode == 1) {
                    cue.set(e.x, e.y)
                    updateAim()
                    mode = 0
                    p = cue
                } else if (p == null && mode == 2) {
                    if (balls.size < 15) {
                        val nb = PointF(e.x, e.y)
                        balls.add(nb)
                        p = nb
                    }
                }
                active = p
                invalidate()
                return p != null
            }
            MotionEvent.ACTION_MOVE -> {
                val a = active
                if (a != null) {
                    if (a === aimPt) {
                        ang = atan2(e.y - cue.y, e.x - cue.x)
                        updateAim()
                    } else {
                        a.set(e.x, e.y)
                        if (a === cue) updateAim()
                    }
                    invalidate()
                }
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
        maxBounces: Int, bounds: RectF, tgts: List<PointF>, hitR: Float, budget0: Float
    ): TraceResult {
        val out = arrayListOf(PointF(sx, sy))
        var px = sx
        var py = sy
        var dx = dirX
        var dy = dirY
        var budget = budget0
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

            val tHit = if (bestI >= 0) bestT else tWall
            if (budget <= tHit) {
                out.add(PointF(px + dx * budget, py + dy * budget))
                return TraceResult(out, null, dx, dy, -1, 0f)
            }
            if (bestI >= 0) {
                val g = PointF(px + dx * bestT, py + dy * bestT)
                out.add(g)
                return TraceResult(out, g, dx, dy, bestI, budget - bestT)
            }

            budget -= tWall
            px += dx * tWall
            py += dy * tWall
            out.add(PointF(px, py))
            if (vertical) dx = -dx else dy = -dy
        }
        return TraceResult(out, null, dx, dy, -1, budget)
    }

    private fun poly(c: Canvas, p: List<PointF>, paint: Paint) {
        for (i in 0 until p.size - 1) {
            c.drawLine(p[i].x, p[i].y, p[i + 1].x, p[i + 1].y, paint)
        }
    }

    private fun arrowHead(c: Canvas, p: List<PointF>, paint: Paint) {
        if (p.size < 2) return
        val a = p[p.size - 2]
        val b = p[p.size - 1]
        if (hypot(b.x - a.x, b.y - a.y) < 4f) return
        val base = atan2(b.y - a.y, b.x - a.x) + Math.PI.toFloat()
        val s = 14 * dp
        c.drawLine(b.x, b.y, b.x + cos(base + 0.5f) * s, b.y + sin(base + 0.5f) * s, paint)
        c.drawLine(b.x, b.y, b.x + cos(base - 0.5f) * s, b.y + sin(base - 0.5f) * s, paint)
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

            val ux = cos(ang)
            val uy = sin(ang)
            val maxLen = hypot(table.width(), table.height()) * 2f
            val budget = maxLen * power / 100f

            linePaint.strokeWidth = lw
            linePaint.color = color
            linePaint.alpha = alpha
            val res = trace(cc.x, cc.y, ux, uy, maxB, bounds, bc, 2 * r, budget)
            poly(c, res.pts, linePaint)
            if (res.hit < 0) arrowHead(c, res.pts, linePaint)

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
                    val tr = trace(tc.x, tc.y, nx, ny, maxB, bounds, emptyList(), 0f, res.left * 0.8f)
                    linePaint.color = Color.rgb(255, 170, 0)
                    linePaint.alpha = alpha
                    poly(c, tr.pts, linePaint)
                    arrowHead(c, tr.pts, linePaint)

                    val dot = res.dx * nx + res.dy * ny
                    var tx = res.dx - dot * nx
                    var ty = res.dy - dot * ny
                    val tlen = hypot(tx, ty)
                    if (tlen > 0.05f) {
                        tx /= tlen
                        ty /= tlen
                        val sl = min(res.left * 0.3f, 120 * dp)
                        linePaint.color = Color.WHITE
                        linePaint.alpha = alpha / 2
                        c.drawLine(ghost.x, ghost.y, ghost.x + tx * sl, ghost.y + ty * sl, linePaint)
                   
