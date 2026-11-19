
package com.poolvision.training

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
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
            text = "TRAINING MODE  •  VERSION 1.0"
            textSize = 14f
            setTextColor(Color.rgb(56, 214, 176))
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 35)
        }

        val start = Button(this).apply {
            text = "START TRAINING"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(
                        Intent(this@MainActivity, AimOverlayService::class.java)
                    )
                } else {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        }

        val permission = Button(this).apply {
            text = "OVERLAY PERMISSION"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(start)
        layout.addView(permission)

        setContentView(layout)
    }
}
