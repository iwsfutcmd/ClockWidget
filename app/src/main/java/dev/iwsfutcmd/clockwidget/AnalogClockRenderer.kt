package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.text.NumberFormat
import android.icu.util.ULocale
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object AnalogClockRenderer {

    // Fractions of the face radius
    private const val NUMERAL_RADIUS = 0.78f
    private const val MAJOR_TICK_INNER = 0.92f
    private const val MINOR_TICK_INNER = 0.96f
    const val DEFAULT_HAND_LENGTH = 0.74f
    // Hour hand length relative to the minute hand
    private const val HOUR_HAND_RATIO = 0.52f / 0.74f
    // Auto numeral size as a fraction of the face radius (used when no
    // explicit point size is set)
    private const val NUMERAL_SIZE = 0.18f

    fun renderToBitmap(
        context: Context, time: LocalTime, width: Int, height: Int,
        bgColor: Int, faceColor: Int,
        localeTag: String = "",
        fontFamily: String = "sans-serif", textStyle: Int = Typeface.NORMAL,
        shadowRadius: Float = 0f, shadowDx: Float = 0f,
        shadowDy: Float = 0f, shadowColor: Int = 0,
        strokeWidth: Float = 0f, strokeColor: Int = 0,
        handStrokeWidth: Float = 0f, handStrokeColor: Int = 0,
        numeralFontSize: Float = 0f,
        showRing: Boolean = true,
        textDirection: String = "ltr",
        handLength: Float = DEFAULT_HAND_LENGTH,
        numeralRotation: String = "upright"
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        // The face always fills the widget; the "Text size" setting
        // (paddingFraction) scales only the numerals
        val density = context.resources.displayMetrics.density
        val strokePx = strokeWidth * density
        val handStrokePx = handStrokeWidth * density
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) / 2f).coerceAtLeast(1f)
        // RTL mirrors the face horizontally: numerals run counterclockwise and
        // the hands sweep counterclockwise, like a Hebrew clock
        val xSign = if (textDirection == "rtl") -1f else 1f

        fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = faceColor
            if (shadowRadius > 0f) {
                setShadowLayer(
                    shadowRadius * density, shadowDx * density,
                    shadowDy * density, shadowColor
                )
            }
        }

        // Outline beneath the hands/ring/cap, controlled separately from the
        // numeral stroke
        fun handOutlinePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = handStrokeColor
            style = Paint.Style.STROKE
        }

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, lineWidth: Float) {
            if (handStrokePx > 0f) {
                canvas.drawLine(x0, y0, x1, y1, handOutlinePaint().apply {
                    this.strokeWidth = lineWidth + handStrokePx * 2
                    strokeCap = Paint.Cap.ROUND
                })
            }
            canvas.drawLine(x0, y0, x1, y1, fillPaint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = lineWidth
                strokeCap = Paint.Cap.ROUND
            })
        }

        if (showRing) {
            // Face outline
            val rimWidth = radius * 0.02f
            val rimRadius = radius - rimWidth / 2f
            if (handStrokePx > 0f) {
                canvas.drawCircle(cx, cy, rimRadius, handOutlinePaint().apply {
                    this.strokeWidth = rimWidth + handStrokePx * 2
                })
            }
            canvas.drawCircle(cx, cy, rimRadius, fillPaint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = rimWidth
            })

            // Tick marks: 60 minor, every 5th major
            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0 - 90.0)
                val isMajor = i % 5 == 0
                val inner = radius * (if (isMajor) MAJOR_TICK_INNER else MINOR_TICK_INNER)
                val outer = radius * 0.985f
                line(
                    cx + inner * cos(angle).toFloat(), cy + inner * sin(angle).toFloat(),
                    cx + outer * cos(angle).toFloat(), cy + outer * sin(angle).toFloat(),
                    radius * (if (isMajor) 0.02f else 0.008f)
                )
            }
        }

        // Numerals 1–12, in the locale's numbering system (honors -u-nu- extensions)
        val locale = if (localeTag.isEmpty()) ULocale.getDefault()
                     else ULocale.forLanguageTag(localeTag)
        val numberFormat = NumberFormat.getInstance(locale).apply { isGroupingUsed = false }
        val typeface = ComposeClockRenderer.resolveTypeface(context, fontFamily, textStyle)
        val numeralPaint = fillPaint().apply {
            // Explicit point size when set; otherwise proportional to the face
            textSize = if (numeralFontSize > 0f) {
                android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, numeralFontSize,
                    context.resources.displayMetrics
                )
            } else radius * NUMERAL_SIZE
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        // Stroke applies to the numerals only, mirroring the digital clock's text outline
        val numeralStrokePaint = if (strokePx > 0f) Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokePx
            textSize = numeralPaint.textSize
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        } else null
        val textBounds = Rect()
        for (h in 1..12) {
            val label = numberFormat.format(h)
            val angle = Math.toRadians(h * 30.0 - 90.0)
            val nx = cx + xSign * radius * NUMERAL_RADIUS * cos(angle).toFloat()
            val ny = cy + radius * NUMERAL_RADIUS * sin(angle).toFloat()
            numeralPaint.getTextBounds(label, 0, label.length, textBounds)
            val baseline = ny - textBounds.exactCenterY()
            // Radial orientation: tops point away from ("outward") or toward
            // ("inward") the center
            val rotation = when (numeralRotation) {
                "outward" -> xSign * h * 30f
                "inward"  -> xSign * h * 30f + 180f
                else      -> 0f
            }
            if (rotation != 0f) {
                canvas.save()
                canvas.rotate(rotation, nx, ny)
            }
            numeralStrokePaint?.let { canvas.drawText(label, nx, baseline, it) }
            canvas.drawText(label, nx, baseline, numeralPaint)
            if (rotation != 0f) canvas.restore()
        }

        // Hands — minute hand sweeps continuously; hour hand follows the minutes
        val minuteAngle = Math.toRadians((time.minute + time.second / 60.0) * 6.0 - 90.0)
        val hourAngle = Math.toRadians((time.hour % 12 + time.minute / 60.0) * 30.0 - 90.0)
        val minuteLength = handLength.coerceIn(0.05f, 1f)
        val hourLength = minuteLength * HOUR_HAND_RATIO
        line(
            cx, cy,
            cx + xSign * radius * hourLength * cos(hourAngle).toFloat(),
            cy + radius * hourLength * sin(hourAngle).toFloat(),
            radius * 0.045f
        )
        line(
            cx, cy,
            cx + xSign * radius * minuteLength * cos(minuteAngle).toFloat(),
            cy + radius * minuteLength * sin(minuteAngle).toFloat(),
            radius * 0.03f
        )

        // Center cap
        val capRadius = radius * 0.04f
        if (handStrokePx > 0f) {
            canvas.drawCircle(cx, cy, capRadius + handStrokePx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = handStrokeColor
            })
        }
        canvas.drawCircle(cx, cy, capRadius, fillPaint())

        return bitmap
    }
}
