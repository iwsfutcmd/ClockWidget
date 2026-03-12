package dev.iwsfutcmd.clockwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.widget.RemoteViews
import java.util.Date

class ClockWidget : AppWidgetProvider() {

    companion object {
        private var cachedSdf: SimpleDateFormat? = null
        private var cachedSdfKey: Pair<String, String>? = null

        private fun getFormatter(pattern: String, localeTag: String): SimpleDateFormat {
            val key = pattern to localeTag
            cachedSdf?.let { if (cachedSdfKey == key) return it }
            return SimpleDateFormat(pattern, ULocale.forLanguageTag(localeTag))
                .also { cachedSdf = it; cachedSdfKey = key }
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            for (id in ids) updateOne(context, mgr, id)
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = ClockPrefs(context, widgetId)
            val text = getFormatter(prefs.pattern, prefs.localeTag).format(Date())

            val options = mgr.getAppWidgetOptions(widgetId)
            val dm = context.resources.displayMetrics
            val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp  = options.getInt(if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH  else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,  200)
            val heightDp = options.getInt(if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
            val widthPx  = (widthDp  * dm.density).toInt().coerceAtLeast(1)
            val heightPx = (heightDp * dm.density).toInt().coerceAtLeast(1)

            val bitmap = ComposeClockRenderer.renderToBitmap(
                context, text, widthPx, heightPx,
                prefs.textSizeSp, prefs.backgroundColor, prefs.textColor,
                prefs.fontFamily, prefs.textStyle,
                prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
                prefs.strokeWidth, prefs.strokeColor
            )
            val views = RemoteViews(context.packageName, R.layout.widget_clock_bitmap)
            views.setImageViewBitmap(R.id.clock_bitmap, bitmap)
            mgr.updateAppWidget(widgetId, views)
        }

        fun startService(context: Context) {
            context.startForegroundService(Intent(context, ClockUpdateService::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, ClockUpdateService::class.java))
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
        startService(context)
    }

    override fun onEnabled(context: Context) = startService(context)

    override fun onDisabled(context: Context) = stopService(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) ClockPrefs(context, id).clear()
    }
}
