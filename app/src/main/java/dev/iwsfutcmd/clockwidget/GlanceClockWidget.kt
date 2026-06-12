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

        val dm = context.resources.displayMetrics
        val widthPx = (size.width.value * dm.density).toInt().coerceAtLeast(1)
        val heightPx = (size.height.value * dm.density).toInt().coerceAtLeast(1)

        val bitmap = if (prefs.clockType == "analog") {
            AnalogClockRenderer.renderToBitmap(
                context, java.time.LocalTime.now(), widthPx, heightPx,
                prefs.backgroundColor, prefs.textColor,
                prefs.localeTag,
                prefs.fontFamily, prefs.textStyle,
                prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
                prefs.strokeWidth, prefs.strokeColor,
                prefs.handStrokeWidth, prefs.handStrokeColor,
                numeralFontSize = prefs.numeralFontSize,
                showRing = prefs.showRing,
                textDirection = prefs.textDirection,
                handLength = prefs.handLength,
                numeralRotation = prefs.numeralRotation
            )
        } else {
            val text = try {
                SimpleDateFormat(prefs.pattern, ULocale.forLanguageTag(prefs.localeTag)).format(Date())
            } catch (_: Exception) { "\u2014" }

            // Recompute font size if widget dimensions changed
            val fontSize = if (prefs.fontSize > 0f &&
                prefs.fontSizeWidth == widthPx && prefs.fontSizeHeight == heightPx
            ) {
                prefs.fontSize
            } else {
                val adjusted = ComposeClockRenderer.computeAdjustedFontSize(
                    context, text, widthPx, heightPx,
                    prefs.fontFamily, prefs.textStyle,
                    prefs.strokeWidth, prefs.textDirection, prefs.padding,
                    prefs.letterSpacing, prefs.lineHeight
                )
                prefs.fontSize = adjusted
                prefs.fontSizeWidth = widthPx
                prefs.fontSizeHeight = heightPx
                adjusted
            }

            ComposeClockRenderer.renderToBitmap(
                context, text, widthPx, heightPx,
                prefs.backgroundColor, prefs.textColor,
                prefs.fontFamily, prefs.textStyle,
                prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
                prefs.strokeWidth, prefs.strokeColor,
                fontSize = fontSize,
                textDirection = prefs.textDirection,
                paddingFraction = prefs.padding,
                letterSpacing = prefs.letterSpacing,
                lineHeight = prefs.lineHeight
            )
        }

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
