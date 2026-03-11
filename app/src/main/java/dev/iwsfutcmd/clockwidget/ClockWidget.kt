package dev.iwsfutcmd.clockwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.content.res.ColorStateList
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.TextAppearanceSpan
import android.util.TypedValue
import android.widget.RemoteViews
import java.util.Date

class ClockWidget : AppWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            for (id in ids) updateOne(context, mgr, id)
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val prefs = ClockPrefs(context, widgetId)
            val sdf = SimpleDateFormat(prefs.pattern, ULocale.forLanguageTag(prefs.localeTag))
            val text = sdf.format(Date())

            if (prefs.useComposeRendering || prefs.useBitmapRendering) {
                val options = mgr.getAppWidgetOptions(widgetId)
                val dm = context.resources.displayMetrics
                val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val widthDp  = options.getInt(if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH  else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,  200)
                val heightDp = options.getInt(if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100)
                val widthPx  = (widthDp  * dm.density).toInt().coerceAtLeast(1)
                val heightPx = (heightDp * dm.density).toInt().coerceAtLeast(1)
                val bitmap = if (prefs.useComposeRendering) {
                    ComposeClockRenderer.renderToBitmap(
                        context, text, widthPx, heightPx,
                        prefs.textSizeSp, prefs.backgroundColor, prefs.textColor,
                        prefs.fontFamily, prefs.textStyle,
                        prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
                        prefs.strokeWidth, prefs.strokeColor
                    )
                } else {
                    renderToBitmap(context, text, prefs, widthPx, heightPx)
                }
                val views = RemoteViews(context.packageName, R.layout.widget_clock_bitmap)
                views.setImageViewBitmap(R.id.clock_bitmap, bitmap)
                mgr.updateAppWidget(widgetId, views)
            } else {
                val views = RemoteViews(context.packageName, R.layout.widget_clock)
                val sizePx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, prefs.textSizeSp,
                    context.resources.displayMetrics
                ).toInt()
                val spannable = SpannableString(text).apply {
                    setSpan(
                        TextAppearanceSpan(
                            prefs.fontFamily,
                            prefs.textStyle,
                            sizePx,
                            ColorStateList.valueOf(prefs.textColor),
                            null
                        ),
                        0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                views.setTextViewText(R.id.clock_text, spannable)
                views.setInt(R.id.clock_text, "setBackgroundColor", prefs.backgroundColor)
                mgr.updateAppWidget(widgetId, views)
            }
        }

        fun renderBitmap(
            context: Context, text: String, width: Int, height: Int,
            textSizeSp: Float, bgColor: Int, textColor: Int,
            fontFamily: String, textStyle: Int,
            shadowRadius: Float = 0f, shadowDx: Float = 0f,
            shadowDy: Float = 0f, shadowColor: Int = 0,
            strokeWidth: Float = 0f, strokeColor: Int = 0
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(bgColor)

            val dm = context.resources.displayMetrics
            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, dm)
                color = textColor
                typeface = Typeface.create(fontFamily, textStyle)
                if (shadowRadius > 0f) {
                    setShadowLayer(shadowRadius * dm.density, shadowDx * dm.density, shadowDy * dm.density, shadowColor)
                }
            }

            val fillLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()

            val strokeLayout = if (strokeWidth > 0f) {
                val sp = TextPaint(paint).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth * dm.density
                    strokeJoin = Paint.Join.ROUND
                    color = strokeColor
                    clearShadowLayer()
                }
                StaticLayout.Builder.obtain(text, 0, text.length, sp, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
            } else null

            val yOffset = ((height - fillLayout.height) / 2f).coerceAtLeast(0f)
            canvas.save()
            canvas.translate(0f, yOffset)
            strokeLayout?.draw(canvas)
            fillLayout.draw(canvas)
            canvas.restore()

            return bitmap
        }

        private fun renderToBitmap(context: Context, text: String, prefs: ClockPrefs, width: Int, height: Int): Bitmap =
            renderBitmap(
                context, text, width, height,
                prefs.textSizeSp, prefs.backgroundColor, prefs.textColor,
                prefs.fontFamily, prefs.textStyle,
                prefs.shadowRadius, prefs.shadowDx, prefs.shadowDy, prefs.shadowColor,
                prefs.strokeWidth, prefs.strokeColor
            )

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
