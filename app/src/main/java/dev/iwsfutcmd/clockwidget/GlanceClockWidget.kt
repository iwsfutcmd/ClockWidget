package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.util.Date

class GlanceClockWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

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

        // Read Glance state — the tick key changes on each service update,
        // forcing recomposition
        val state = currentState<Preferences>()
        @Suppress("UNUSED_VARIABLE")
        val tick = state[TICK_KEY] ?: 0L

        val prefs = ClockPrefs(context, appWidgetId)
        val text = try {
            SimpleDateFormat(prefs.pattern, ULocale.forLanguageTag(prefs.localeTag)).format(Date())
        } catch (_: Exception) { "\u2014" }

        val dm = context.resources.displayMetrics
        val widthPx = (size.width.value * dm.density).toInt().coerceAtLeast(1)
        val heightPx = (size.height.value * dm.density).toInt().coerceAtLeast(1)

        val bitmap = ComposeClockRenderer.renderToBitmap(
            context, text, widthPx, heightPx,
            prefs.backgroundColor, prefs.textColor,
            prefs.fontFamily, prefs.textStyle,
            prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
            prefs.strokeWidth, prefs.strokeColor,
            textDirection = prefs.textDirection,
            paddingFraction = prefs.padding
        )

        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Clock",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    }

    companion object {
        val TICK_KEY = longPreferencesKey("tick")

        suspend fun tickAll(context: Context) {
            val widget = GlanceClockWidget()
            val manager = GlanceAppWidgetManager(context)
            val now = System.currentTimeMillis()
            for (glanceId in manager.getGlanceIds(GlanceClockWidget::class.java)) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply { this[TICK_KEY] = now }
                }
                widget.update(context, glanceId)
            }
        }
    }
}
