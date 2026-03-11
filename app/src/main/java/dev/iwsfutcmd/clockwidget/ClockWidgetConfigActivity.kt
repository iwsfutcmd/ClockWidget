package dev.iwsfutcmd.clockwidget

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.icu.text.DateTimePatternGenerator
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.R as M3R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date

class ClockWidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var saveAction: (() -> Unit)? = null
    private lateinit var bitmapPreviewView: ImageView

    override fun onPause() {
        saveAction?.invoke()
        super.onPause()
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContentView(R.layout.activity_config)

        val prefs = ClockPrefs(this, widgetId)

        // ── Wallpaper background ─────────────────────────────────────────────

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(this)
            val wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            wc?.primaryColor?.toArgb()?.let { color ->
                findViewById<FrameLayout>(R.id.preview_container).setBackgroundColor(color)
            }
        }

        // ── Preview — inflated from the real widget layout ───────────────────

        val previewContainer = findViewById<FrameLayout>(R.id.preview_container)
        val previewView = layoutInflater.inflate(R.layout.widget_clock, previewContainer, false) as TextView

        val density = resources.displayMetrics.density
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val maxWidthDp  = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,  0)
        val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (maxWidthDp > 0 && maxHeightDp > 0) {
            val clp = previewContainer.layoutParams
            clp.width  = (maxWidthDp  * density).toInt()
            clp.height = (maxHeightDp * density).toInt()
            previewContainer.layoutParams = clp
        }
        previewView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        previewContainer.addView(previewView)
        bitmapPreviewView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
        previewContainer.addView(bitmapPreviewView)

        // ── Text fields ──────────────────────────────────────────────────────

        val localeEdit   = findViewById<EditText>(R.id.edit_locale)
        val skeletonEdit = findViewById<EditText>(R.id.edit_skeleton)
        val patternEdit  = findViewById<EditText>(R.id.edit_pattern)

        var currentLocale  = prefs.localeTag
        var currentPattern = prefs.pattern

        localeEdit.setText(currentLocale)
        skeletonEdit.setText(
            DateTimePatternGenerator
                .getInstance(ULocale.forLanguageTag(currentLocale))
                .getSkeleton(currentPattern)
        )
        patternEdit.setText(escape(currentPattern))

        localeEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val input = localeEdit.text.toString().trim()
            val tag = input.ifBlank { ClockPrefs.defaultLocaleTag() }
            try {
                val locale = ULocale.forLanguageTag(tag)
                if (locale.language.isEmpty() && tag.isNotEmpty())
                    throw IllegalArgumentException("Unrecognised locale tag: $tag")
                currentLocale = tag
                localeEdit.setText(tag)
            } catch (e: Exception) {
                showError(e.message ?: "Invalid locale")
                localeEdit.setText(currentLocale)
            }
        }

        fun applySkeleton() {
            val skeleton = skeletonEdit.text.toString().trim()
            try {
                val locale  = ULocale.forLanguageTag(currentLocale)
                val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)
                currentPattern = pattern
                patternEdit.setText(escape(pattern))
            } catch (e: Exception) {
                showError(e.message ?: "Invalid skeleton")
                skeletonEdit.setText(
                    DateTimePatternGenerator
                        .getInstance(ULocale.forLanguageTag(currentLocale))
                        .getSkeleton(currentPattern)
                )
            }
        }

        findViewById<Button>(R.id.btn_set_skeleton).setOnClickListener {
            applySkeleton()
        }

        patternEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val pattern = unescape(patternEdit.text.toString())
            try {
                SimpleDateFormat(pattern, ULocale.forLanguageTag(currentLocale))
                currentPattern = pattern
                skeletonEdit.setText(
                    DateTimePatternGenerator
                        .getInstance(ULocale.forLanguageTag(currentLocale))
                        .getSkeleton(currentPattern)
                )
            } catch (e: Exception) {
                showError(e.message ?: "Invalid pattern")
                patternEdit.setText(escape(currentPattern))
            }
        }

        // ── Style state ──────────────────────────────────────────────────────

        var currentBgColor       = prefs.backgroundColor
        var currentTextColor     = prefs.textColor
        var currentTextSizeSp    = prefs.textSizeSp
        var currentTextStyle     = prefs.textStyle
        var currentFontFamily    = prefs.fontFamily
        var currentUseBitmap     = prefs.useBitmapRendering
        var currentUseCompose    = prefs.useComposeRendering
        var currentShadowRadius  = prefs.shadowRadius
        var currentShadowDx      = prefs.shadowDx
        var currentShadowDy      = prefs.shadowDy
        var currentShadowColor   = prefs.shadowColor
        var currentStrokeWidth   = prefs.strokeWidth
        var currentStrokeColor   = prefs.strokeColor

        // ── Shared update helper ─────────────────────────────────────────────

        fun refreshPreview() = updatePreview(
            previewView, currentPattern, currentLocale,
            currentTextSizeSp, currentBgColor, currentTextColor, currentTextStyle, currentFontFamily,
            currentUseBitmap, currentUseCompose,
            currentShadowRadius, currentShadowDx, currentShadowDy, currentShadowColor,
            currentStrokeWidth, currentStrokeColor
        )

        // ── Style toolbar ────────────────────────────────────────────────────

        val btnBold   = findViewById<MaterialButton>(R.id.btn_bold)
        val btnItalic = findViewById<MaterialButton>(R.id.btn_italic)

        btnBold.isChecked   = currentTextStyle and Typeface.BOLD   != 0
        btnItalic.isChecked = currentTextStyle and Typeface.ITALIC != 0

        fun syncStyle() {
            currentTextStyle = (if (btnBold.isChecked) Typeface.BOLD else 0) or
                               (if (btnItalic.isChecked) Typeface.ITALIC else 0)
            refreshPreview()
        }

        btnBold.addOnCheckedChangeListener   { _, _ -> syncStyle() }
        btnItalic.addOnCheckedChangeListener { _, _ -> syncStyle() }

        findViewById<MaterialButton>(R.id.btn_text_size).setOnClickListener {
            showTextSizeDialog(currentTextSizeSp,
                onPreview = { size -> currentTextSizeSp = size; refreshPreview() }
            ) { size -> currentTextSizeSp = size; refreshPreview() }
        }

        findViewById<MaterialButton>(R.id.btn_text_color).setOnClickListener {
            showColorDialog(getString(R.string.config_text_color_label), currentTextColor,
                onPreview = { color -> currentTextColor = color; refreshPreview() }
            ) { color -> currentTextColor = color; refreshPreview() }
        }

        findViewById<MaterialButton>(R.id.btn_bg_color).setOnClickListener {
            showColorDialog(getString(R.string.config_bg_color_label), currentBgColor,
                onPreview = { color -> currentBgColor = color; refreshPreview() }
            ) { color -> currentBgColor = color; refreshPreview() }
        }

        findViewById<MaterialButton>(R.id.btn_font_family).setOnClickListener {
            showFontDialog(currentFontFamily, currentUseCompose) { family ->
                currentFontFamily = family
                refreshPreview()
            }
        }

        val checkboxBitmap = findViewById<CheckBox>(R.id.checkbox_bitmap_mode)
        checkboxBitmap.isChecked = currentUseBitmap
        checkboxBitmap.setOnCheckedChangeListener { _, isChecked ->
            currentUseBitmap = isChecked
            refreshPreview()
        }

        val checkboxCompose = findViewById<CheckBox>(R.id.checkbox_compose_mode)
        checkboxCompose.isChecked = currentUseCompose
        checkboxCompose.setOnCheckedChangeListener { _, isChecked ->
            currentUseCompose = isChecked
            refreshPreview()
        }

        findViewById<MaterialButton>(R.id.btn_shadow).setOnClickListener {
            showShadowDialog(
                currentShadowRadius, currentShadowDx, currentShadowDy, currentShadowColor,
                onPreview = { r, dx, dy, c ->
                    currentShadowRadius = r; currentShadowDx = dx
                    currentShadowDy = dy; currentShadowColor = c
                    refreshPreview()
                }
            ) { r, dx, dy, c ->
                currentShadowRadius = r; currentShadowDx = dx
                currentShadowDy = dy; currentShadowColor = c
                refreshPreview()
            }
        }

        findViewById<MaterialButton>(R.id.btn_stroke).setOnClickListener {
            showStrokeDialog(currentStrokeWidth, currentStrokeColor,
                onPreview = { w, c -> currentStrokeWidth = w; currentStrokeColor = c; refreshPreview() }
            ) { w, c -> currentStrokeWidth = w; currentStrokeColor = c; refreshPreview() }
        }

        // ── Live preview tick ────────────────────────────────────────────────

        val tick = object : Runnable {
            override fun run() {
                refreshPreview()
                handler.postDelayed(this, 1_000L)
            }
        }
        tickRunnable = tick
        handler.post(tick)

        // ── Autosave (called from onPause) ───────────────────────────────────

        saveAction = {
            prefs.pattern            = currentPattern
            prefs.localeTag          = currentLocale
            prefs.textSizeSp         = currentTextSizeSp
            prefs.backgroundColor    = currentBgColor
            prefs.textColor          = currentTextColor
            prefs.textStyle          = currentTextStyle
            prefs.fontFamily          = currentFontFamily
            prefs.useBitmapRendering  = currentUseBitmap
            prefs.useComposeRendering = currentUseCompose
            prefs.shadowRadius        = currentShadowRadius
            prefs.shadowDx           = currentShadowDx
            prefs.shadowDy           = currentShadowDy
            prefs.shadowColor        = currentShadowColor
            prefs.strokeWidth        = currentStrokeWidth
            prefs.strokeColor        = currentStrokeColor

            val mgr = AppWidgetManager.getInstance(this)
            ClockWidget.updateOne(this, mgr, widgetId)
            ClockWidget.startService(this)
        }
    }

    private fun themeColor(@AttrRes attr: Int): Int? {
        val tv = TypedValue()
        return if (theme.resolveAttribute(attr, tv, true)) tv.data else null
    }

    private fun populateSwatches(container: LinearLayout, onPick: (Int) -> Unit) {
        val attrs = listOf(
            M3R.attr.colorPrimary,
            M3R.attr.colorOnPrimary,
            M3R.attr.colorPrimaryContainer,
            M3R.attr.colorOnPrimaryContainer,
            M3R.attr.colorSecondary,
            M3R.attr.colorOnSecondary,
            M3R.attr.colorTertiary,
            M3R.attr.colorOnTertiary,
            M3R.attr.colorSurface,
            M3R.attr.colorOnSurface,
            M3R.attr.colorSurfaceVariant,
            M3R.attr.colorOnSurfaceVariant,
            M3R.attr.colorError,
            M3R.attr.colorOutline,
        )
        val dp     = resources.displayMetrics.density
        val size   = (32 * dp).toInt()
        val margin = (4 * dp).toInt()
        for (attr in attrs) {
            val color = themeColor(attr) ?: continue
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke((dp + 0.5f).toInt(), 0x33000000)
                }
                setOnClickListener { onPick(color) }
            }
            container.addView(swatch)
        }
    }

    private fun showColorDialog(
        title: String,
        initial: Int,
        onPreview: ((Int) -> Unit)? = null,
        onApply: (Int) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color, null)

        val preview  = dialogView.findViewById<View>(R.id.dialog_color_preview)
        val hexEdit  = dialogView.findViewById<EditText>(R.id.dialog_hex)
        val seekR    = dialogView.findViewById<SeekBar>(R.id.dialog_seek_r)
        val seekG    = dialogView.findViewById<SeekBar>(R.id.dialog_seek_g)
        val seekB    = dialogView.findViewById<SeekBar>(R.id.dialog_seek_b)
        val seekA    = dialogView.findViewById<SeekBar>(R.id.dialog_seek_a)
        val labelR   = dialogView.findViewById<TextView>(R.id.dialog_label_r)
        val labelG   = dialogView.findViewById<TextView>(R.id.dialog_label_g)
        val labelB   = dialogView.findViewById<TextView>(R.id.dialog_label_b)
        val labelA   = dialogView.findViewById<TextView>(R.id.dialog_label_a)

        seekR.progress = Color.red(initial)   ; labelR.text = "${Color.red(initial)}"
        seekG.progress = Color.green(initial) ; labelG.text = "${Color.green(initial)}"
        seekB.progress = Color.blue(initial)  ; labelB.text = "${Color.blue(initial)}"
        seekA.progress = Color.alpha(initial) ; labelA.text = "${Color.alpha(initial)}"
        preview.setBackgroundColor(initial)
        hexEdit.setText(colorToHex(initial))

        val currentColor: () -> Int = {
            Color.argb(seekA.progress, seekR.progress, seekG.progress, seekB.progress)
        }

        fun seekListener(label: TextView) = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = "$progress"
                val color = currentColor()
                preview.setBackgroundColor(color)
                if (!hexEdit.hasFocus()) hexEdit.setText(colorToHex(color))
                onPreview?.invoke(color)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        }

        seekR.setOnSeekBarChangeListener(seekListener(labelR))
        seekG.setOnSeekBarChangeListener(seekListener(labelG))
        seekB.setOnSeekBarChangeListener(seekListener(labelB))
        seekA.setOnSeekBarChangeListener(seekListener(labelA))

        populateSwatches(dialogView.findViewById(R.id.dialog_swatches)) { color ->
            seekR.progress = Color.red(color)   ; labelR.text = "${Color.red(color)}"
            seekG.progress = Color.green(color) ; labelG.text = "${Color.green(color)}"
            seekB.progress = Color.blue(color)  ; labelB.text = "${Color.blue(color)}"
            seekA.progress = Color.alpha(color) ; labelA.text = "${Color.alpha(color)}"
            preview.setBackgroundColor(color)
            hexEdit.setText(colorToHex(color))
            onPreview?.invoke(color)
        }

        hexEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            try {
                val color = Color.parseColor(hexEdit.text.toString().trim())
                seekR.progress = Color.red(color)   ; labelR.text = "${Color.red(color)}"
                seekG.progress = Color.green(color) ; labelG.text = "${Color.green(color)}"
                seekB.progress = Color.blue(color)  ; labelB.text = "${Color.blue(color)}"
                seekA.progress = Color.alpha(color) ; labelA.text = "${Color.alpha(color)}"
                preview.setBackgroundColor(color)
                onPreview?.invoke(color)
            } catch (e: Exception) {
                hexEdit.setText(colorToHex(currentColor()))
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> onApply(currentColor()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onPreview?.invoke(initial) }
            .show()
    }

    private fun showTextSizeDialog(
        initial: Float,
        onPreview: ((Float) -> Unit)? = null,
        onApply: (Float) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_size, null)
        val seekSize  = dialogView.findViewById<SeekBar>(R.id.dialog_seek_size)
        val labelSize = dialogView.findViewById<TextView>(R.id.dialog_label_size)

        seekSize.progress = initial.toInt()
        labelSize.text = "${initial.toInt()} sp"

        seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                labelSize.text = "$progress sp"
                onPreview?.invoke(progress.toFloat())
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.config_text_size_label))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> onApply(seekSize.progress.toFloat()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onPreview?.invoke(initial) }
            .show()
    }

    private fun colorToHex(color: Int) = String.format(
        "#%02X%02X%02X%02X", Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color))

    private fun updatePreview(
        view: TextView,
        pattern: String,
        localeTag: String,
        textSizeSp: Float,
        bgColor: Int,
        textColor: Int,
        textStyle: Int = Typeface.NORMAL,
        fontFamily: String = "sans-serif",
        useBitmap: Boolean = false,
        useCompose: Boolean = false,
        shadowRadius: Float = 0f,
        shadowDx: Float = 0f,
        shadowDy: Float = 0f,
        shadowColor: Int = Color.TRANSPARENT,
        strokeWidth: Float = 0f,
        strokeColor: Int = Color.BLACK
    ) {
        val text = try {
            SimpleDateFormat(pattern, ULocale.forLanguageTag(localeTag)).format(Date())
        } catch (e: Exception) { "—" }

        if (useCompose && view.width > 0 && view.height > 0) {
            val bmp = ComposeClockRenderer.renderToBitmap(
                this, text, view.width, view.height,
                textSizeSp, bgColor, textColor, fontFamily, textStyle,
                shadowRadius, shadowDx, shadowDy, shadowColor,
                strokeWidth, strokeColor
            )
            bitmapPreviewView.setImageBitmap(bmp)
            bitmapPreviewView.visibility = View.VISIBLE
            view.visibility = View.GONE
            return
        }

        if (useBitmap && view.width > 0 && view.height > 0) {
            val bmp = ClockWidget.renderBitmap(
                this, text, view.width, view.height,
                textSizeSp, bgColor, textColor, fontFamily, textStyle,
                shadowRadius, shadowDx, shadowDy, shadowColor,
                strokeWidth, strokeColor
            )
            bitmapPreviewView.setImageBitmap(bmp)
            bitmapPreviewView.visibility = View.VISIBLE
            view.visibility = View.GONE
            return
        }

        bitmapPreviewView.visibility = View.GONE
        view.visibility = View.VISIBLE

        val dm = view.resources.displayMetrics
        val cornerPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, dm)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(bgColor)
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        view.setTextColor(textColor)
        view.setTypeface(Typeface.create(fontFamily, Typeface.NORMAL), textStyle)
        view.setShadowLayer(0f, 0f, 0f, 0)
        view.text = text
    }

    private fun showFontDialog(initial: String, isComposeMode: Boolean, onApply: (String) -> Unit) {
        val families = arrayOf("sans-serif", "serif", "monospace", "cursive")
        val labels: Array<String> = if (isComposeMode)
            arrayOf("Sans-serif", "Serif", "Monospace", "Cursive", "Google Font…")
        else
            arrayOf("Sans-serif", "Serif", "Monospace", "Cursive")
        val current = families.indexOf(initial)
        MaterialAlertDialogBuilder(this)
            .setTitle("Font family")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which < families.size) onApply(families[which])
                else showGoogleFontInput(initial, onApply)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGoogleFontInput(initial: String, onApply: (String) -> Unit) {
        val dp16 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
        val input = EditText(this).apply {
            hint = "e.g. Josefin Sans"
            if (initial !in listOf("sans-serif", "serif", "monospace", "cursive")) setText(initial)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(dp16, dp16 / 2, dp16, dp16 / 2)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Google Font name")
            .setMessage("Enter a family name exactly as it appears on fonts.google.com")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    onApply(name)
                    ComposeClockRenderer.validateGoogleFont(this, name) { error ->
                        if (error != null) {
                            Toast.makeText(this, "Font error: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStrokeDialog(
        initialWidth: Float,
        initialColor: Int,
        onPreview: ((Float, Int) -> Unit)? = null,
        onApply: (Float, Int) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_stroke, null)
        val seekWidth  = dialogView.findViewById<SeekBar>(R.id.dialog_seek_stroke_width)
        val labelWidth = dialogView.findViewById<TextView>(R.id.dialog_label_stroke_width)
        val colorChip  = dialogView.findViewById<View>(R.id.dialog_stroke_color_chip)

        seekWidth.progress = (initialWidth * 10).toInt().coerceIn(0, 200)
        var currentStrokeColor = initialColor
        colorChip.setBackgroundColor(currentStrokeColor)

        val currentWidth = { seekWidth.progress / 10f }

        fun updateLabel() { labelWidth.text = "%.1f dp".format(currentWidth()) }
        updateLabel()
        fun previewCurrent() = onPreview?.invoke(currentWidth(), currentStrokeColor)

        seekWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) { updateLabel(); previewCurrent() }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })

        dialogView.findViewById<Button>(R.id.dialog_btn_stroke_color).setOnClickListener {
            val colorAtOpen = currentStrokeColor
            showColorDialog("Stroke color", colorAtOpen,
                onPreview = { color -> currentStrokeColor = color; colorChip.setBackgroundColor(color); previewCurrent() }
            ) { color -> currentStrokeColor = color; colorChip.setBackgroundColor(color); previewCurrent() }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Text outline")
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> onApply(currentWidth(), currentStrokeColor) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onPreview?.invoke(initialWidth, initialColor) }
            .show()
    }

    private fun showShadowDialog(
        initialRadius: Float,
        initialDx: Float,
        initialDy: Float,
        initialColor: Int,
        onPreview: ((Float, Float, Float, Int) -> Unit)? = null,
        onApply: (Float, Float, Float, Int) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_shadow, null)
        val seekRadius  = dialogView.findViewById<SeekBar>(R.id.dialog_seek_radius)
        val seekDx      = dialogView.findViewById<SeekBar>(R.id.dialog_seek_dx)
        val seekDy      = dialogView.findViewById<SeekBar>(R.id.dialog_seek_dy)
        val labelRadius = dialogView.findViewById<TextView>(R.id.dialog_label_radius)
        val labelDx     = dialogView.findViewById<TextView>(R.id.dialog_label_dx)
        val labelDy     = dialogView.findViewById<TextView>(R.id.dialog_label_dy)
        val colorChip   = dialogView.findViewById<View>(R.id.dialog_shadow_color_chip)

        seekRadius.progress = (initialRadius * 10).toInt().coerceIn(0, 200)
        seekDx.progress     = ((initialDx + 10f) * 10).toInt().coerceIn(0, 200)
        seekDy.progress     = ((initialDy + 10f) * 10).toInt().coerceIn(0, 200)

        var currentShadowColor = initialColor
        colorChip.setBackgroundColor(currentShadowColor)

        val currentRadius = { seekRadius.progress / 10f }
        val currentDxVal  = { (seekDx.progress  - 100) / 10f }
        val currentDyVal  = { (seekDy.progress  - 100) / 10f }

        fun updateLabels() {
            labelRadius.text = "%.1f dp".format(currentRadius())
            labelDx.text     = "%.1f dp".format(currentDxVal())
            labelDy.text     = "%.1f dp".format(currentDyVal())
        }
        updateLabels()

        fun previewCurrent() =
            onPreview?.invoke(currentRadius(), currentDxVal(), currentDyVal(), currentShadowColor)

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                updateLabels()
                previewCurrent()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        }
        seekRadius.setOnSeekBarChangeListener(seekListener)
        seekDx.setOnSeekBarChangeListener(seekListener)
        seekDy.setOnSeekBarChangeListener(seekListener)

        dialogView.findViewById<Button>(R.id.dialog_btn_shadow_color).setOnClickListener {
            val colorAtOpen = currentShadowColor
            showColorDialog("Shadow color", colorAtOpen,
                onPreview = { color ->
                    currentShadowColor = color
                    colorChip.setBackgroundColor(color)
                    previewCurrent()
                }
            ) { color ->
                currentShadowColor = color
                colorChip.setBackgroundColor(color)
                previewCurrent()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Text shadow")
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onApply(currentRadius(), currentDxVal(), currentDyVal(), currentShadowColor)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onPreview?.invoke(initialRadius, initialDx, initialDy, initialColor)
            }
            .show()
    }

    private fun escape(s: String) = s.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Invalid input")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
