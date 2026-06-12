package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.icu.text.DateTimePatternGenerator
import android.icu.util.ULocale

class ClockPrefs(private val context: Context, private val widgetId: Int) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clock_widget_$widgetId", Context.MODE_PRIVATE)

    var clockType: String
        get() = prefs.getString("clock_type", "digital") ?: "digital"
        set(value) = prefs.edit().putString("clock_type", value).apply()

    var showRing: Boolean
        get() = prefs.getBoolean("show_ring", true)
        set(value) = prefs.edit().putBoolean("show_ring", value).apply()

    // Outline around the analog hands, ring, and center cap (numerals use strokeWidth/strokeColor)
    var handStrokeWidth: Float
        get() = prefs.getFloat("hand_stroke_width", 0f)
        set(value) = prefs.edit().putFloat("hand_stroke_width", value).apply()

    var handStrokeColor: Int
        get() = prefs.getInt("hand_stroke_color", Color.BLACK)
        set(value) = prefs.edit().putInt("hand_stroke_color", value).apply()

    // Analog numeral size in sp; 0 = auto (proportional to the face radius).
    // Kept separate from the digital "Text size" percentage (padding).
    var numeralFontSize: Float
        get() = prefs.getFloat("numeral_font_size", 0f)
        set(value) = prefs.edit().putFloat("numeral_font_size", value).apply()

    // Minute-hand length as a fraction of the face radius; the hour hand
    // keeps a fixed ratio to it
    var handLength: Float
        get() = prefs.getFloat("hand_length", 0.74f)
        set(value) = prefs.edit().putFloat("hand_length", value).apply()

    // Analog numeral orientation: "upright", "outward" (tops away from
    // center), or "inward" (tops toward center)
    var numeralRotation: String
        get() = prefs.getString("numeral_rotation", null)
            ?: if (prefs.getBoolean("rotate_numerals", false)) "outward" else "upright"
        set(value) = prefs.edit().putString("numeral_rotation", value).apply()

    var pattern: String
        get() = prefs.getString("pattern", null) ?: defaultPattern()
        set(value) = prefs.edit().putString("pattern", value).apply()

    var localeTag: String
        get() = prefs.getString("locale_tag", null) ?: defaultLocaleTag()
        set(value) = prefs.edit().putString("locale_tag", value).apply()

    var backgroundColor: Int
        get() = prefs.getInt("background_color", Color.argb(0xCC, 0x1E, 0x1E, 0x2E))
        set(value) = prefs.edit().putInt("background_color", value).apply()

    var textColor: Int
        get() = prefs.getInt("text_color", Color.argb(0xFF, 0xCD, 0xD6, 0xF4))
        set(value) = prefs.edit().putInt("text_color", value).apply()

    var textStyle: Int
        get() = prefs.getInt("text_style", android.graphics.Typeface.NORMAL)
        set(value) = prefs.edit().putInt("text_style", value).apply()

    var fontFamily: String
        get() = prefs.getString("font_family", "sans-serif") ?: "sans-serif"
        set(value) = prefs.edit().putString("font_family", value).apply()

    var shadowRadius: Float
        get() = prefs.getFloat("shadow_radius", 0f)
        set(value) = prefs.edit().putFloat("shadow_radius", value).apply()

    var shadowDx: Float
        get() = prefs.getFloat("shadow_dx", 0f)
        set(value) = prefs.edit().putFloat("shadow_dx", value).apply()

    var shadowDy: Float
        get() = prefs.getFloat("shadow_dy", 0f)
        set(value) = prefs.edit().putFloat("shadow_dy", value).apply()

    var shadowColor: Int
        get() = prefs.getInt("shadow_color", Color.argb(0x80, 0, 0, 0))
        set(value) = prefs.edit().putInt("shadow_color", value).apply()

    var strokeWidth: Float
        get() = prefs.getFloat("stroke_width", 0f)
        set(value) = prefs.edit().putFloat("stroke_width", value).apply()

    var strokeColor: Int
        get() = prefs.getInt("stroke_color", Color.BLACK)
        set(value) = prefs.edit().putInt("stroke_color", value).apply()

    var fontSize: Float
        get() = prefs.getFloat("font_size", 0f)
        set(value) = prefs.edit().putFloat("font_size", value).apply()

    var fontSizeWidth: Int
        get() = prefs.getInt("font_size_width", 0)
        set(value) = prefs.edit().putInt("font_size_width", value).apply()

    var fontSizeHeight: Int
        get() = prefs.getInt("font_size_height", 0)
        set(value) = prefs.edit().putInt("font_size_height", value).apply()

    var textDirection: String
        get() = prefs.getString("text_direction", "ltr") ?: "ltr"
        set(value) = prefs.edit().putString("text_direction", value).apply()

    var padding: Float
        get() = prefs.getFloat("padding", 0.05f)
        set(value) = prefs.edit().putFloat("padding", value).apply()

    var letterSpacing: Float
        get() = prefs.getFloat("letter_spacing", 0f)
        set(value) = prefs.edit().putFloat("letter_spacing", value).apply()

    var lineHeight: Float
        get() = prefs.getFloat("line_height", 0f)
        set(value) = prefs.edit().putFloat("line_height", value).apply()

    val skeleton: String
        get() = DateTimePatternGenerator
            .getInstance(ULocale.forLanguageTag(localeTag))
            .getSkeleton(pattern)

    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val DEFAULT_SKELETON = "jm"

        fun defaultLocaleTag(): String = ULocale.getDefault().toLanguageTag()

        fun defaultPattern(): String =
            DateTimePatternGenerator
                .getInstance(ULocale.getDefault())
                .getBestPattern(DEFAULT_SKELETON)
    }
}
