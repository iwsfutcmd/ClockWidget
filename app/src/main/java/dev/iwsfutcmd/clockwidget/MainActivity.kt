package dev.iwsfutcmd.clockwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mgr = AppWidgetManager.getInstance(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val instructions = TextView(this).apply {
            text = "Long-press your home screen and select Widgets to add Clock Widget."
            textSize = 16f
        }
        layout.addView(instructions)

        if (mgr.isRequestPinAppWidgetSupported) {
            val btn = Button(this).apply {
                text = "Add to Home Screen"
                setOnClickListener {
                    val provider = ComponentName(this@MainActivity, ClockWidget::class.java)
                    if (mgr.requestPinAppWidget(provider, null, null)) {
                        // Finish so the launcher comes to the foreground and can show its dialog
                        finish()
                    }
                }
            }
            layout.addView(btn)
            instructions.apply {
                text = "Tap the button below to add Clock Widget to your home screen."
            }
        }

        setContentView(layout)
    }
}
