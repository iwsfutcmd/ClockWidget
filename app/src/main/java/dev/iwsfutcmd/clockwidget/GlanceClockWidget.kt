package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import java.util.Date

class GlanceClockWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            ClockContent(appWidgetId)
        }
    }

    @Composable
    private fun ClockContent(appWidgetId: Int) {
        val context = LocalContext.current
        val size = LocalSize.current
        val dm = context.resources.displayMetrics
        val widthPx = (size.width.value * dm.density).toInt().coerceAtLeast(1)
        val heightPx = (size.height.value * dm.density).toInt().coerceAtLeast(1)

        val prefs = ClockPrefs(context, appWidgetId)
        val text = try {
            SimpleDateFormat(prefs.pattern, ULocale.forLanguageTag(prefs.localeTag)).format(Date())
        } catch (_: Exception) { "\u2014" }

        val bitmap = ComposeClockRenderer.renderToBitmap(
            context, text, widthPx, heightPx,
            prefs.backgroundColor, prefs.textColor,
            prefs.fontFamily, prefs.textStyle,
            prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
            prefs.strokeWidth, prefs.strokeColor
        )

        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Clock",
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}
