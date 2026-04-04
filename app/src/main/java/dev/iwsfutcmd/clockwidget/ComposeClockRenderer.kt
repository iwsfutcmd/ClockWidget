package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as NativePaint
import android.os.Build
import android.text.TextPaint
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val TAG = "ComposeClockRenderer"

object ComposeClockRenderer {

    private const val DEBUG_BOUNDS = false

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

    private const val DEFAULT_PADDING_FRACTION = 0f

    private fun buildBaseStyle(
        context: Context, fontFamily: String, textStyle: Int, textColor: Int, shadow: Shadow?,
        letterSpacing: Float = 0f, lineHeight: Float = 0f
    ): TextStyle {
        val isBold   = textStyle and android.graphics.Typeface.BOLD   != 0
        val isItalic = textStyle and android.graphics.Typeface.ITALIC != 0
        return TextStyle(
            color        = Color(textColor),
            fontFamily   = fontFamilyFor(context, fontFamily),
            fontWeight   = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle    = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            textAlign    = TextAlign.Center,
            shadow       = shadow,
            letterSpacing = if (letterSpacing != 0f) letterSpacing.em else TextUnit.Unspecified,
            lineHeight    = if (lineHeight > 0f) lineHeight.em else TextUnit.Unspecified
        )
    }

    fun isVerticalTextSupported(): Boolean = Build.VERSION.SDK_INT >= 36

    private fun buildNativeTypeface(fontFamily: String, textStyle: Int): android.graphics.Typeface {
        val base = when (fontFamily) {
            "sans-serif" -> android.graphics.Typeface.SANS_SERIF
            "serif"      -> android.graphics.Typeface.SERIF
            "monospace"  -> android.graphics.Typeface.MONOSPACE
            else         -> android.graphics.Typeface.create(fontFamily, textStyle)
        }
        return if (base == android.graphics.Typeface.create(fontFamily, textStyle)) base
        else android.graphics.Typeface.create(base, textStyle)
    }

    private fun buildFillPaint(
        fontFamily: String, textStyle: Int, textColor: Int, textSizePx: Float,
        shadowRadius: Float, shadowDx: Float, shadowDy: Float, shadowColor: Int, density: Float
    ): TextPaint = TextPaint(NativePaint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSizePx
        this.typeface = buildNativeTypeface(fontFamily, textStyle)
        this.color = textColor
        if (shadowRadius > 0f) {
            setShadowLayer(shadowRadius * density, shadowDx * density, shadowDy * density, shadowColor)
        }
    }

    @androidx.annotation.RequiresApi(36)
    private fun findOptimalFontSizeVertical(
        text: String, paint: TextPaint, maxWidth: Float, maxHeight: Float
    ): Float {
        var lo = 1f
        var hi = 2000f
        while (hi - lo > 0.5f) {
            val mid = (lo + hi) / 2f
            paint.textSize = mid
            // Use maxHeight as the column constraint — if the text wraps to
            // multiple columns, layout.width will exceed a single glyph width
            val singleCol = androidx.text.vertical.VerticalTextLayout(
                text = text, paint = paint, height = maxHeight
            )
            val noWrap = androidx.text.vertical.VerticalTextLayout(
                text = text, paint = paint, height = Float.MAX_VALUE
            )
            // Fits if: single column (no wrapping) and column width fits
            val didWrap = singleCol.width > noWrap.width * 1.01f
            if (!didWrap && noWrap.width <= maxWidth) lo = mid else hi = mid
        }
        return lo
    }

    private fun findContentBounds(bitmap: Bitmap): android.graphics.Rect {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var top = h; var bottom = 0; var left = w; var right = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x].ushr(24) != 0) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        return if (top > bottom) android.graphics.Rect(0, 0, w, h)
        else android.graphics.Rect(left, top, right + 1, bottom + 1)
    }

    fun computeOptimalFontSize(
        context: Context, text: String, width: Int, height: Int,
        fontFamily: String, textStyle: Int,
        strokeWidth: Float = 0f,
        paddingFraction: Float = DEFAULT_PADDING_FRACTION
    ): Float {
        val dm = context.resources.displayMetrics
        val density = Density(context)
        val textMeasurer = TextMeasurer(getFontResolver(context), density, LayoutDirection.Ltr)
        val strokePx = strokeWidth * dm.density
        val padX = width * paddingFraction
        val padY = height * paddingFraction
        val availableWidth = (width - strokePx - padX * 2).coerceAtLeast(1f)
        val availableHeight = (height - strokePx - padY * 2).coerceAtLeast(1f)
        val baseStyle = buildBaseStyle(context, fontFamily, textStyle, 0, null)
        return findOptimalFontSize(textMeasurer, text, baseStyle, availableWidth, availableHeight)
    }

    /**
     * Computes a font size (in sp) whose actual ink bounds fill the available area.
     * Call this once (e.g. when config closes) and save the result.
     */
    fun computeAdjustedFontSize(
        context: Context, text: String, width: Int, height: Int,
        fontFamily: String, textStyle: Int,
        strokeWidth: Float = 0f, textDirection: String = "ltr",
        paddingFraction: Float = DEFAULT_PADDING_FRACTION,
        letterSpacing: Float = 0f, lineHeight: Float = 0f
    ): Float {
        val dmDensity = context.resources.displayMetrics.density
        val strokePx = strokeWidth * dmDensity
        val padX = width * paddingFraction
        val padY = height * paddingFraction
        val availW = (width - strokePx - padX * 2).coerceAtLeast(1f)
        val availH = (height - strokePx - padY * 2).coerceAtLeast(1f)

        // Render at layout-metrics size on oversized bitmap to get initial font size and ink bounds
        val bigInitW = width * 3
        val bigInitH = height * 3
        val (initialBitmap, initialFontSize) = renderToTempBitmap(
            context, text, width, height, 0xFFFFFFFF.toInt(), fontFamily, textStyle,
            0f, 0f, 0f, 0,
            strokeWidth, 0xFFFFFFFF.toInt(), 0f, textDirection, paddingFraction,
            bitmapWidth = bigInitW, bitmapHeight = bigInitH,
            letterSpacing = letterSpacing, lineHeight = lineHeight
        )
        val initialBounds = findContentBounds(initialBitmap)
        initialBitmap.recycle()

        // Scale so ink fills available area
        val scale = if (initialBounds.width() > 0 && initialBounds.height() > 0) {
            minOf(availW / initialBounds.width().toFloat(), availH / initialBounds.height().toFloat())
        } else 1f

        return initialFontSize * scale
    }

    fun renderToBitmap(
        context: Context, text: String, width: Int, height: Int,
        bgColor: Int, textColor: Int,
        fontFamily: String, textStyle: Int,
        shadowRadius: Float = 0f, shadowDx: Float = 0f,
        shadowDy: Float = 0f, shadowColor: Int = 0,
        strokeWidth: Float = 0f, strokeColor: Int = 0,
        fontSize: Float = 0f,
        textDirection: String = "ltr",
        paddingFraction: Float = DEFAULT_PADDING_FRACTION,
        letterSpacing: Float = 0f, lineHeight: Float = 0f
    ): Bitmap {
        val bigW = width * 3
        val bigH = height * 3

        // Render (no shadow) on oversized bitmap for centering bounds
        val (measureBitmap, _) = renderToTempBitmap(
            context, text, width, height, textColor, fontFamily, textStyle,
            0f, 0f, 0f, 0,
            strokeWidth, strokeColor, fontSize, textDirection, 0f,
            bitmapWidth = bigW, bitmapHeight = bigH,
            letterSpacing = letterSpacing, lineHeight = lineHeight
        )
        val bounds = findContentBounds(measureBitmap)
        measureBitmap.recycle()

        // Render (with shadow) on oversized bitmap for actual content
        val (tempBitmap, _) = renderToTempBitmap(
            context, text, width, height, textColor, fontFamily, textStyle,
            shadowRadius, shadowDx, shadowDy, shadowColor,
            strokeWidth, strokeColor, fontSize, textDirection, 0f,
            bitmapWidth = bigW, bitmapHeight = bigH,
            letterSpacing = letterSpacing, lineHeight = lineHeight
        )

        // Compose final bitmap — center ink bounds in widget
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(bgColor)

        val offsetX = (width - bounds.width()) / 2f - bounds.left
        val offsetY = (height - bounds.height()) / 2f - bounds.top
        canvas.drawBitmap(tempBitmap, offsetX, offsetY, null)
        tempBitmap.recycle()

        // Debug: draw bright green rect at the centered text bounds
        if (DEBUG_BOUNDS) {
            val debugPaint = NativePaint().apply {
                color = android.graphics.Color.GREEN
                style = NativePaint.Style.STROKE
                this.strokeWidth = 3f
            }
            val cx = (width - bounds.width()) / 2f
            val cy = (height - bounds.height()) / 2f
            canvas.drawRect(cx, cy, cx + bounds.width(), cy + bounds.height(), debugPaint)
        }

        return bitmap
    }

    /** Returns (bitmap, fontSizeUsed) */
    private fun renderToTempBitmap(
        context: Context, text: String, width: Int, height: Int,
        textColor: Int, fontFamily: String, textStyle: Int,
        shadowRadius: Float, shadowDx: Float, shadowDy: Float, shadowColor: Int,
        strokeWidth: Float, strokeColor: Int,
        fontSize: Float, textDirection: String,
        paddingFraction: Float,
        bitmapWidth: Int = width, bitmapHeight: Int = height,
        letterSpacing: Float = 0f, lineHeight: Float = 0f
    ): Pair<Bitmap, Float> {
        val dm = context.resources.displayMetrics
        val dmDensity = dm.density
        val strokePx = strokeWidth * dmDensity
        val padX = width * paddingFraction
        val padY = height * paddingFraction
        val availW = (width - strokePx - padX * 2).coerceAtLeast(1f)
        val availH = (height - strokePx - padY * 2).coerceAtLeast(1f)

        val isVertical = textDirection == "vertical" && isVerticalTextSupported()

        val tempBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(tempBitmap)

        if (isVertical) {
            // Native TextPaint + VerticalTextLayout (API 36+)
            val fillPaint = buildFillPaint(
                fontFamily, textStyle, textColor, 1f,
                shadowRadius, shadowDx, shadowDy, shadowColor, dmDensity
            )
            if (letterSpacing != 0f) fillPaint.letterSpacing = letterSpacing
            val optimalSize = if (fontSize > 0f) fontSize
                else findOptimalFontSizeVertical(text, fillPaint, availW, availH)
            fillPaint.textSize = optimalSize
            if (shadowRadius > 0f) {
                fillPaint.setShadowLayer(
                    shadowRadius * dmDensity, shadowDx * dmDensity,
                    shadowDy * dmDensity, shadowColor
                )
            }

            val fillLayout = androidx.text.vertical.VerticalTextLayout(
                text = text, paint = fillPaint, height = Float.MAX_VALUE
            )
            val drawX = fillLayout.width + strokePx / 2f
            val drawY = strokePx / 2f

            if (strokeWidth > 0f) {
                val strokePaint = TextPaint(fillPaint).apply {
                    clearShadowLayer()
                    color = strokeColor
                    style = NativePaint.Style.STROKE
                    this.strokeWidth = strokePx
                }
                val strokeLayout = androidx.text.vertical.VerticalTextLayout(
                    text = text, paint = strokePaint, height = Float.MAX_VALUE
                )
                strokeLayout.draw(canvas, drawX, drawY)
            }
            fillLayout.draw(canvas, drawX, drawY)

            return tempBitmap to optimalSize
        } else {
            // Compose TextMeasurer + CanvasDrawScope
            val density = Density(context)
            val layoutDir = if (textDirection == "rtl") LayoutDirection.Rtl else LayoutDirection.Ltr
            val textMeasurer = TextMeasurer(getFontResolver(context), density, layoutDir)

            val shadow = if (shadowRadius > 0f) Shadow(
                color      = Color(shadowColor),
                offset     = Offset(shadowDx * dmDensity, shadowDy * dmDensity),
                blurRadius = shadowRadius * dmDensity
            ) else null

            val baseStyle = buildBaseStyle(context, fontFamily, textStyle, textColor, shadow,
                letterSpacing = letterSpacing, lineHeight = lineHeight)
            val spSize = if (fontSize > 0f) fontSize
                else findOptimalFontSize(textMeasurer, text, baseStyle, availW, availH)
            val sizedStyle = baseStyle.copy(fontSize = spSize.sp)

            CanvasDrawScope().draw(
                density, layoutDir, Canvas(canvas),
                Size(bitmapWidth.toFloat(), bitmapHeight.toFloat())
            ) {
                if (strokeWidth > 0f) {
                    val strokeLayout = textMeasurer.measure(
                        text, sizedStyle, softWrap = false,
                        overflow = TextOverflow.Clip, constraints = Constraints()
                    )
                    val strokeOffset = Offset(strokePx / 2f, strokePx / 2f)
                    drawText(
                        strokeLayout,
                        color     = Color(strokeColor),
                        drawStyle = Stroke(strokePx),
                        topLeft   = strokeOffset
                    )
                }
                val fillOffset = Offset(strokePx / 2f, strokePx / 2f)
                val fillLayout = textMeasurer.measure(
                    text, sizedStyle, softWrap = false,
                    overflow = TextOverflow.Clip, constraints = Constraints()
                )
                drawText(fillLayout, color = Color(textColor), drawStyle = Fill, topLeft = fillOffset)
            }

            return tempBitmap to spSize
        }
    }
}
