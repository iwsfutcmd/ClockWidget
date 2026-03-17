package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily.Resolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.googlefonts.Font as GoogleFontResource
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private const val TAG = "ComposeClockRenderer"

object ComposeClockRenderer {

    // Cached so that async Google Font loads persist between widget update ticks.
    // A fresh resolver every render would discard completed downloads.
    private var cachedFontResolver: Resolver? = null

    private fun getFontResolver(context: Context): Resolver =
        cachedFontResolver ?: run {
            val handler = CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Google Fonts load error: $throwable")
            }
            createFontFamilyResolver(context.applicationContext, handler)
                .also { cachedFontResolver = it }
        }

    // Try app package first (non-transitive R, resources merged at runtime),
    // then the library's own package (namespaced resources mode).
    private fun getCertResId(context: Context): Int =
        context.resources.getIdentifier(
            "com_google_android_gms_fonts_certs", "array", context.packageName
        ).takeIf { it != 0 } ?: context.resources.getIdentifier(
            "com_google_android_gms_fonts_certs", "array",
            "androidx.compose.ui.text.googlefonts"
        )

    private var cachedProvider: GoogleFont.Provider? = null

    private fun getProvider(context: Context): GoogleFont.Provider? {
        cachedProvider?.let { return it }
        val certResId = getCertResId(context)
        if (certResId == 0) {
            Log.e(TAG, "com_google_android_gms_fonts_certs not found — Google Fonts unavailable")
            return null
        }
        return GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = certResId
        ).also { cachedProvider = it }
    }

    private val systemFamilies = mapOf(
        "sans-serif" to FontFamily.SansSerif,
        "serif"      to FontFamily.Serif,
        "monospace"  to FontFamily.Monospace,
        "cursive"    to FontFamily.Cursive
    )

    private fun fontFamilyFor(context: Context, name: String): FontFamily {
        systemFamilies[name]?.let { return it }
        val provider = getProvider(context) ?: return FontFamily.Default
        return FontFamily(GoogleFontResource(GoogleFont(name), provider))
    }

    private fun findOptimalFontSize(
        textMeasurer: TextMeasurer,
        text: String,
        baseStyle: TextStyle,
        maxWidth: Float,
        maxHeight: Float
    ): Float {
        var lo = 1f
        var hi = 2000f
        while (hi - lo > 0.5f) {
            val mid = (lo + hi) / 2f
            val style = baseStyle.copy(fontSize = mid.sp)
            val result = textMeasurer.measure(
                text, style, softWrap = false, overflow = TextOverflow.Clip,
                constraints = Constraints()
            )
            if (result.size.width <= maxWidth && result.size.height <= maxHeight) {
                lo = mid
            } else {
                hi = mid
            }
        }
        return lo
    }

    fun renderToBitmap(
        context: Context, text: String, width: Int, height: Int,
        bgColor: Int, textColor: Int,
        fontFamily: String, textStyle: Int,
        shadowRadius: Float = 0f, shadowDx: Float = 0f,
        shadowDy: Float = 0f, shadowColor: Int = 0,
        strokeWidth: Float = 0f, strokeColor: Int = 0
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val androidCanvas = AndroidCanvas(bitmap)

        val dm = context.resources.displayMetrics
        val density = Density(context)
        val textMeasurer = TextMeasurer(getFontResolver(context), density, LayoutDirection.Ltr)

        val isBold   = textStyle and android.graphics.Typeface.BOLD   != 0
        val isItalic = textStyle and android.graphics.Typeface.ITALIC != 0

        val shadow = if (shadowRadius > 0f) Shadow(
            color      = Color(shadowColor),
            offset     = Offset(shadowDx * dm.density, shadowDy * dm.density),
            blurRadius = shadowRadius * dm.density
        ) else null

        val strokePx = strokeWidth * dm.density
        val availableWidth = (width - strokePx).coerceAtLeast(1f)
        val availableHeight = (height - strokePx).coerceAtLeast(1f)

        val baseStyle = TextStyle(
            color      = Color(textColor),
            fontFamily = fontFamilyFor(context, fontFamily),
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle  = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            textAlign  = TextAlign.Center,
            shadow     = shadow
        )

        val optimalSp = findOptimalFontSize(
            textMeasurer, text, baseStyle, availableWidth, availableHeight
        )
        val sizedStyle = baseStyle.copy(fontSize = optimalSp.sp)

        val constraints = Constraints.fixedWidth(width)
        val fillLayout = textMeasurer.measure(
            text, sizedStyle, softWrap = false,
            overflow = TextOverflow.Clip, constraints = constraints
        )

        val yOffset = ((height - fillLayout.size.height) / 2f).coerceAtLeast(0f)
        val topLeft = Offset(0f, yOffset)

        CanvasDrawScope().draw(
            density, LayoutDirection.Ltr,
            Canvas(androidCanvas),
            Size(width.toFloat(), height.toFloat())
        ) {
            drawRect(Color(bgColor))
            if (strokeWidth > 0f) {
                val strokeLayout = textMeasurer.measure(
                    text, sizedStyle, softWrap = false,
                    overflow = TextOverflow.Clip, constraints = constraints
                )
                drawText(
                    strokeLayout,
                    color     = Color(strokeColor),
                    drawStyle = Stroke(strokePx),
                    shadow    = Shadow(Color.Transparent, Offset.Zero, 0f),
                    topLeft   = topLeft
                )
            }
            drawText(fillLayout, color = Color(textColor), drawStyle = Fill, topLeft = topLeft)
        }

        return bitmap
    }
}
