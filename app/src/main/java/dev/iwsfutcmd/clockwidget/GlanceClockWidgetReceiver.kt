package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class GlanceClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceClockWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopService(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) ClockPrefs(context, id).clear()
    }

    companion object {
        fun startService(context: Context) {
            context.startForegroundService(Intent(context, ClockUpdateService::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, ClockUpdateService::class.java))
        }
    }
}
