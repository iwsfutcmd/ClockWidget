package dev.iwsfutcmd.clockwidget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.icu.text.DateTimePatternGenerator
import android.icu.util.ULocale

class ClockPrefs(private val context: Context, private val widgetId: Int) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clock_widget_$widgetId", Context.MODE_PRIVATE)

    var pattern: String
        get() = prefs.getString("pattern", null) ?: defaultPattern()
        set(value) = prefs.edit().putString("pattern", value).apply()

    var localeTag: String
        get() = prefs.getString("locale_tag", null) ?: defaultLocaleTag()
        set(value) = prefs.edit().putString("locale_tag", value).apply()

    var textSizeSp: Float
        get() = prefs.getFloat("text_size_sp", 24f)
        set(value) = prefs.edit().putFloat("text_size_sp", value).apply()

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
