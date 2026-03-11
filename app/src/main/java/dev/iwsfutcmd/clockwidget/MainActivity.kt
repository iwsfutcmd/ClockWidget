package dev.iwsfutcmd.clockwidget

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "Long-press your home screen and select Widgets to add Clock Widget."
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }
}
