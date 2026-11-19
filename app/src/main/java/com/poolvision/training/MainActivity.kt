package com.poolvision.training

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.rgb(15, 17, 23)
    private val card = Color.rgb(30, 34, 46)
    private val green = Color.rgb(0, 190, 105)
    private val red = Color.rgb(200, 60, 60)
    private val blue = Color.rgb(50, 110, 220)
    private val dp get() = resources.displayMetrics.density

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var status: TextView

    private fun rounded(color: Int, r: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = r * dp
        return g
    }

    private fun label(text: String, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
        return t
    }

    private fun bigButton(text: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.textSize = 16f
        b.background = rounded(color, 14f)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (54 * dp).toInt()
        )
        lp.topMargin = (10 * dp).toInt()
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun addSlider(parent: LinearLayout, title: String, key: String, lo: Int, hi: Int, def: Int) {
        val value = prefs.getInt(key, def)
        val tv = label("$title: $value", 15f, Color.WHITE)
        val sb = SeekBar(this)
        sb.max = hi - lo
        sb.progress = value - lo
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + lo
                prefs.edit().putInt(key, v).apply()
                tv.text = "$title: $v"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(tv)
        parent.addView(sb)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pv", Context.MODE_PRIVATE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = (20 * dp).toInt()
        root.setPadding(pad, (56 * dp).toInt(), pad, pad)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.addView(root)

        root.addView(label("Pool Vision", 28f, Color.WHITE))
        root.addView(label("أداة تدريب لتصويب 8 Ball Pool", 14f, Color.GRAY))

        status = label("", 15f, Color.WHITE)
        root.addView(status)

        root.addView(bigButton("1) منح إذن الظهور فوق التطبيقات", blue) {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(i)
        })

        root.addView(bigButton("2) ▶ تشغيل الأوفرلاي", green) {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                Toast.makeText(this@MainActivity, "امنح الإذن أولاً", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this@MainActivity, AimOverlayService::class.java))
                moveTaskToBack(true)
            }
        })

        root.addView(bigButton("■ إيقاف الأوفرلاي", red) {
            stopService(Intent(this@MainActivity, AimOverlayService::class.java))
        })

        root.addView(label("الإعدادات", 20f, Color.WHITE))
        addSlider(root, "الشفافية", "alpha", 40, 255, 200)
        addSlider(root, "حجم الكرة", "radius", 8, 30, 14)
        addSlider(root, "سُمك الخط", "width", 1, 8, 3)
        addSlider(root, "عدد الارتدادات", "bounces", 0, 5, 3)

        root.addView(label("لون الخط", 15f, Color.WHITE))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val colors = intArrayOf(
            Color.rgb(0, 230, 118), Color.rgb(0, 200, 255), Color.rgb(255, 235, 59),
            Color.rgb(255, 82, 82), Color.WHITE
        )
        for (col in colors) {
            val v = View(this)
            v.background = rounded(col, 12f)
            val lp = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f)
            lp.setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            v.layoutParams = lp
            v.setOnClickListener {
                prefs.edit().putInt("color", col).apply()
                Toast.makeText(this@MainActivity, "تم تغيير اللون", Toast.LENGTH_SHORT).show()
            }
            row.addView(v)
        }
        root.addView(row)

        val help = label(
            "طريقة الاستخدام:\n" +
                "• افتح اللعبة، ستظهر لوحة صغيرة قابلة للسحب (≡).\n" +
                "• في وضع التعديل: حرّك الكرة البيضاء والهدف ونقطة التوجيه وركني الطاولة.\n" +
                "• اضغط ✓ تثبيت ليمرّ اللمس للعبة وتضرب.\n" +
                "• ↺ لإعادة الوضع الافتراضي، ✕ للإغلاق.",
            14f, Color.LTGRAY
        )
        help.setPadding(pad / 2, pad, pad / 2, pad)
        help.background = rounded(card, 14f)
        val hlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        hlp.topMargin = (20 * dp).toInt()
        help.layoutParams = hlp
        root.addView(help)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        status.text = if (ok) "✅ إذن الظهور فوق التطبيقات: مفعّل" else "❌ إذن الظهور فوق التطبيقات: غير مفعّل"
    }
}
