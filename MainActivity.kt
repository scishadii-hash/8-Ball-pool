package com.poolvision.training

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(10, 18, 29))
        }
        val title = TextView(this).apply {
            text = "POOL VISION"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "TRAINING OVERLAY  •  VERSION 1.0"
            textSize = 13f
            setTextColor(Color.rgb(56, 214, 176))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 28)
        }
        val info = TextView(this).apply {
            text = "A manual practice overlay. Move the two handles to set the cue-ball direction and target line. It does not read or modify the game."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        val permission = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        val start = Button(this).apply {
            text = "Start training overlay"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, AimOverlayService::class.java))
                } else {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
        }
        val stop = Button(this).apply {
            text = "Stop overlay"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AimOverlayService::class.java))
            }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(info)
        root.addView(permission)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }
}
