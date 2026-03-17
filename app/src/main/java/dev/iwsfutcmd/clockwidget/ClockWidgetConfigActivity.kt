package dev.iwsfutcmd.clockwidget

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.toColorInt
import android.graphics.Typeface
import android.icu.text.DateTimePatternGenerator
import android.icu.text.LocaleDisplayNames
import android.icu.text.NumberingSystem
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Date

class ClockWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            ClockWidgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(widgetId)
                }
            }
        }
    }
}

// ── Theme ────────────────────────────────────────────────────────────────────

@Composable
private fun ClockWidgetTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ── Main config screen ───────────────────────────────────────────────────────

@Composable
private fun ConfigScreen(widgetId: Int) {
    val context = LocalContext.current
    val prefs = remember { ClockPrefs(context, widgetId) }

    // ── State ────────────────────────────────────────────────────────────────
    var currentLocale by remember { mutableStateOf(prefs.localeTag) }
    var currentPattern by remember { mutableStateOf(prefs.pattern) }
    var localeText by remember { mutableStateOf(prefs.localeTag) }
    var skeletonText by remember {
        mutableStateOf(
            DateTimePatternGenerator.getInstance(ULocale.forLanguageTag(prefs.localeTag))
                .getSkeleton(prefs.pattern)
        )
    }
    var patternText by remember { mutableStateOf(escape(prefs.pattern)) }
    var bgColor by remember { mutableIntStateOf(prefs.backgroundColor) }
    var textColor by remember { mutableIntStateOf(prefs.textColor) }
    var textStyle by remember { mutableIntStateOf(prefs.textStyle) }
    var fontFamily by remember { mutableStateOf(prefs.fontFamily) }
    var shadowRadius by remember { mutableFloatStateOf(prefs.shadowRadius) }
    var shadowDx by remember { mutableFloatStateOf(prefs.shadowDx) }
    var shadowDy by remember { mutableFloatStateOf(prefs.shadowDy) }
    var shadowColor by remember { mutableIntStateOf(prefs.shadowColor) }
    var strokeWidth by remember { mutableFloatStateOf(prefs.strokeWidth) }
    var strokeColor by remember { mutableIntStateOf(prefs.strokeColor) }

    // ── Dialog visibility ────────────────────────────────────────────────────
    var activeDialog by remember { mutableStateOf<ActiveDialog>(ActiveDialog.None) }

    // ── Preview bitmap ───────────────────────────────────────────────────────
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var previewWidth by remember { mutableIntStateOf(0) }
    var previewHeight by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Live tick
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Render bitmap whenever anything changes
    LaunchedEffect(
        tick, previewWidth, previewHeight,
        currentPattern, currentLocale, bgColor, textColor,
        textStyle, fontFamily, shadowRadius, shadowDx, shadowDy, shadowColor,
        strokeWidth, strokeColor
    ) {
        if (previewWidth <= 0 || previewHeight <= 0) return@LaunchedEffect
        val text = try {
            SimpleDateFormat(currentPattern, ULocale.forLanguageTag(currentLocale)).format(Date())
        } catch (_: Exception) { "\u2014" }
        bitmap = ComposeClockRenderer.renderToBitmap(
            context, text, previewWidth, previewHeight,
            bgColor, textColor, fontFamily, textStyle,
            shadowRadius, shadowDx, shadowDy, shadowColor,
            strokeWidth, strokeColor
        )
    }

    // ── Auto-save on pause ───────────────────────────────────────────────────
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        prefs.pattern = currentPattern
        prefs.localeTag = currentLocale
        prefs.backgroundColor = bgColor
        prefs.textColor = textColor
        prefs.textStyle = textStyle
        prefs.fontFamily = fontFamily
        prefs.shadowRadius = shadowRadius
        prefs.shadowDx = shadowDx
        prefs.shadowDy = shadowDy
        prefs.shadowColor = shadowColor
        prefs.strokeWidth = strokeWidth
        prefs.strokeColor = strokeColor

        val mgr = AppWidgetManager.getInstance(context)
        ClockWidget.updateOne(context, mgr, widgetId)
        ClockWidget.startService(context)
    }

    // ── Wallpaper color ──────────────────────────────────────────────────────
    val wallpaperColor = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val wm = WallpaperManager.getInstance(context)
            wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor?.toArgb()
        } else null
    }

    // ── Widget size hint for preview ─────────────────────────────────────────
    val options = remember { AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) }
    val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)

    // ── Layout ───────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {
        // Preview
        val previewBg = wallpaperColor?.let { androidx.compose.ui.graphics.Color(it) }
            ?: MaterialTheme.colorScheme.surfaceVariant
        val previewModifier = if (maxWidthDp > 0 && maxHeightDp > 0) {
            Modifier
                .width(maxWidthDp.dp)
                .height(maxHeightDp.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        }
        Box(
            modifier = Modifier
                .padding(16.dp)
                .then(previewModifier)
                .background(previewBg)
                .onSizeChanged { size ->
                    previewWidth = size.width
                    previewHeight = size.height
                },
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Scrollable controls
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            // ── Locale ───────────────────────────────────────────────────────
            Text(
                stringResource(R.string.config_locale_label),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = localeText,
                onValueChange = { localeText = it },
                placeholder = { Text(stringResource(R.string.config_locale_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            val input = localeText.trim()
                            val tag = input.ifBlank { ClockPrefs.defaultLocaleTag() }
                            try {
                                val locale = ULocale.forLanguageTag(tag)
                                if (locale.language.isEmpty() && tag.isNotEmpty())
                                    throw IllegalArgumentException("Unrecognised locale tag: $tag")
                                currentLocale = tag
                                localeText = tag
                            } catch (e: Exception) {
                                activeDialog = ActiveDialog.Error(e.message ?: "Invalid locale")
                                localeText = currentLocale
                            }
                        }
                    }
            )
            Spacer(Modifier.height(16.dp))

            // ── Number System ───────────────────────────────────────────────
            NumberSystemDropdown(
                currentLocale = currentLocale,
                onLocaleChange = { newTag ->
                    currentLocale = newTag
                    localeText = newTag
                }
            )
            Spacer(Modifier.height(16.dp))

            // ── Skeleton ─────────────────────────────────────────────────────
            Text(
                stringResource(R.string.config_skeleton_label),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = skeletonText,
                    onValueChange = { skeletonText = it },
                    placeholder = { Text(stringResource(R.string.config_skeleton_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val skeleton = skeletonText.trim()
                    try {
                        val locale = ULocale.forLanguageTag(currentLocale)
                        val pattern = DateTimePatternGenerator.getInstance(locale)
                            .getBestPattern(skeleton)
                        currentPattern = pattern
                        patternText = escape(pattern)
                    } catch (e: Exception) {
                        activeDialog = ActiveDialog.Error(e.message ?: "Invalid skeleton")
                        skeletonText = DateTimePatternGenerator
                            .getInstance(ULocale.forLanguageTag(currentLocale))
                            .getSkeleton(currentPattern)
                    }
                }) { Text(stringResource(R.string.set)) }
            }
            Spacer(Modifier.height(16.dp))

            // ── Pattern ──────────────────────────────────────────────────────
            Text(
                stringResource(R.string.config_pattern_label),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = patternText,
                onValueChange = { patternText = it },
                placeholder = { Text(stringResource(R.string.config_pattern_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            val pattern = unescape(patternText)
                            try {
                                SimpleDateFormat(
                                    pattern,
                                    ULocale.forLanguageTag(currentLocale)
                                )
                                currentPattern = pattern
                                skeletonText = DateTimePatternGenerator
                                    .getInstance(ULocale.forLanguageTag(currentLocale))
                                    .getSkeleton(currentPattern)
                            } catch (e: Exception) {
                                activeDialog = ActiveDialog.Error(e.message ?: "Invalid pattern")
                                patternText = escape(currentPattern)
                            }
                        }
                    }
            )
            Spacer(Modifier.height(16.dp))

            // ── Style toolbar ────────────────────────────────────────────────
            StyleToolbar(
                textStyle = textStyle,
                onBoldToggle = { bold ->
                    textStyle = (if (bold) textStyle or Typeface.BOLD else textStyle and Typeface.BOLD.inv())
                },
                onItalicToggle = { italic ->
                    textStyle = (if (italic) textStyle or Typeface.ITALIC else textStyle and Typeface.ITALIC.inv())
                },
                onTextColor = { activeDialog = ActiveDialog.Color(ColorTarget.TEXT) },
                onBgColor = { activeDialog = ActiveDialog.Color(ColorTarget.BACKGROUND) },
                onFont = { activeDialog = ActiveDialog.Font },
                onShadow = { activeDialog = ActiveDialog.Shadow },
                onStroke = { activeDialog = ActiveDialog.Stroke }
            )
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    val dismiss = { activeDialog = ActiveDialog.None }

    when (val dialog = activeDialog) {
        ActiveDialog.None -> {}

        is ActiveDialog.Color -> {
            val target = dialog.target
            ColorPickerDialog(
                title = when (target) {
                    ColorTarget.TEXT -> stringResource(R.string.config_text_color_label)
                    ColorTarget.BACKGROUND -> stringResource(R.string.config_bg_color_label)
                    ColorTarget.SHADOW -> "Shadow color"
                    ColorTarget.STROKE -> "Stroke color"
                },
                initialColor = when (target) {
                    ColorTarget.TEXT -> textColor
                    ColorTarget.BACKGROUND -> bgColor
                    ColorTarget.SHADOW -> shadowColor
                    ColorTarget.STROKE -> strokeColor
                },
                onColorChange = { color ->
                    when (target) {
                        ColorTarget.TEXT -> textColor = color
                        ColorTarget.BACKGROUND -> bgColor = color
                        ColorTarget.SHADOW -> shadowColor = color
                        ColorTarget.STROKE -> strokeColor = color
                    }
                },
                onDismiss = dismiss,
                onConfirm = dismiss
            )
        }

        ActiveDialog.Shadow -> ShadowDialog(
            initialRadius = shadowRadius,
            initialDx = shadowDx,
            initialDy = shadowDy,
            initialColor = shadowColor,
            onPreview = { r, dx, dy, c ->
                shadowRadius = r; shadowDx = dx; shadowDy = dy; shadowColor = c
            },
            onDismiss = dismiss,
            onConfirm = dismiss
        )

        ActiveDialog.Stroke -> StrokeDialog(
            initialWidth = strokeWidth,
            initialColor = strokeColor,
            onPreview = { w, c -> strokeWidth = w; strokeColor = c },
            onDismiss = dismiss,
            onConfirm = dismiss
        )

        ActiveDialog.Font -> FontDialog(
            initial = fontFamily,
            onDismiss = dismiss,
            onConfirm = { family ->
                fontFamily = family
                dismiss()
            }
        )

        is ActiveDialog.Error -> ErrorDialog(
            message = dialog.message,
            onDismiss = dismiss
        )
    }
}

// ── Dialog state ─────────────────────────────────────────────────────────────

private enum class ColorTarget { TEXT, BACKGROUND, SHADOW, STROKE }

private sealed interface ActiveDialog {
    data object None : ActiveDialog
    data class Color(val target: ColorTarget) : ActiveDialog
    data object Shadow : ActiveDialog
    data object Stroke : ActiveDialog
    data object Font : ActiveDialog
    data class Error(val message: String) : ActiveDialog
}

// ── Number system dropdown ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberSystemDropdown(
    currentLocale: String,
    onLocaleChange: (String) -> Unit
) {
    val uLocale = remember(currentLocale) { ULocale.forLanguageTag(currentLocale) }

    val aliases = listOf("native", "traditio", "finance")

    // CLDR uses long-form names for display lookups; BCP 47 truncates to 8 chars
    val displayAliases = mapOf("traditio" to "traditional")

    val displayNames = remember {
        val ldn = LocaleDisplayNames.getInstance(ULocale.getDefault())
        (NumberingSystem.getAvailableNames().toList() + aliases)
            .associateWith { ldn.keyValueDisplayName("numbers", displayAliases[it] ?: it) }
    }

    val pinnedIds = listOf("native", "traditio")
    val nuIds = remember {
        pinnedIds + displayNames.entries
            .filter { it.key !in pinnedIds }
            .sortedBy { it.value.lowercase() }
            .map { it.key }
    }

    val currentNu = remember(currentLocale) {
        uLocale.getUnicodeLocaleType("nu")
    }

    val selectedLabel = remember(currentNu, displayNames) {
        if (currentNu == null) "Default"
        else displayNames[currentNu] ?: currentNu
    }

    var expanded by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.config_number_system_label),
        style = MaterialTheme.typography.labelLarge
    )
    Spacer(Modifier.height(4.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Default") },
                onClick = {
                    expanded = false
                    val newLocale = ULocale.Builder()
                        .setLocale(uLocale)
                        .setUnicodeLocaleKeyword("nu", null)
                        .build()
                    onLocaleChange(newLocale.toLanguageTag())
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
            nuIds.forEach { id ->
                DropdownMenuItem(
                    text = { Text(displayNames[id] ?: id) },
                    onClick = {
                        expanded = false
                        val newLocale = ULocale.Builder()
                            .setLocale(uLocale)
                            .setUnicodeLocaleKeyword("nu", id)
                            .build()
                        onLocaleChange(newLocale.toLanguageTag())
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// ── Style toolbar ────────────────────────────────────────────────────────────

@Composable
private fun StyleToolbar(
    textStyle: Int,
    onBoldToggle: (Boolean) -> Unit,
    onItalicToggle: (Boolean) -> Unit,
    onTextColor: () -> Unit,
    onBgColor: () -> Unit,
    onFont: () -> Unit,
    onShadow: () -> Unit,
    onStroke: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconToggleButton(
            checked = textStyle and Typeface.BOLD != 0,
            onCheckedChange = onBoldToggle
        ) {
            Icon(painterResource(R.drawable.ic_format_bold), contentDescription = "Bold")
        }
        IconToggleButton(
            checked = textStyle and Typeface.ITALIC != 0,
            onCheckedChange = onItalicToggle
        ) {
            Icon(painterResource(R.drawable.ic_format_italic), contentDescription = "Italic")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onTextColor) {
            Icon(painterResource(R.drawable.ic_format_color_text), contentDescription = "Text color")
        }
        IconButton(onClick = onBgColor) {
            Icon(painterResource(R.drawable.ic_palette), contentDescription = "Background color")
        }
        IconButton(onClick = onFont) {
            Icon(painterResource(R.drawable.ic_font_family), contentDescription = "Font family")
        }
        IconButton(onClick = onShadow) {
            Icon(painterResource(R.drawable.ic_shadow), contentDescription = "Text shadow")
        }
        IconButton(onClick = onStroke) {
            Icon(painterResource(R.drawable.ic_stroke), contentDescription = "Text outline")
        }
    }
}

// ── Color picker dialog ──────────────────────────────────────────────────────

@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onColorChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { onColorChange(initialColor); onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            ColorPickerContent(
                title = title,
                initialColor = initialColor,
                onColorChange = onColorChange,
                onBack = { onColorChange(initialColor); onDismiss() },
                onConfirm = onConfirm
            )
        }
    }
}

@Composable
private fun ColorPickerContent(
    title: String,
    initialColor: Int,
    onColorChange: (Int) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    var r by remember { mutableIntStateOf(Color.red(initialColor)) }
    var g by remember { mutableIntStateOf(Color.green(initialColor)) }
    var b by remember { mutableIntStateOf(Color.blue(initialColor)) }
    var a by remember { mutableIntStateOf(Color.alpha(initialColor)) }
    var hexText by remember { mutableStateOf(colorToHex(initialColor)) }
    var hexFocused by remember { mutableStateOf(false) }

    val currentColor = Color.argb(a, r, g, b)

    // Live preview callback
    LaunchedEffect(r, g, b, a) {
        onColorChange(Color.argb(a, r, g, b))
        if (!hexFocused) hexText = colorToHex(Color.argb(a, r, g, b))
    }

    fun applyColor(color: Int) {
        r = Color.red(color)
        g = Color.green(color)
        b = Color.blue(color)
        a = Color.alpha(color)
        hexText = colorToHex(color)
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        ThemeSwatches(onPick = ::applyColor)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(androidx.compose.ui.graphics.Color(currentColor))
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = hexText,
            onValueChange = { hexText = it },
            label = { Text(stringResource(R.string.color_hex_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    hexFocused = state.isFocused
                    if (!state.isFocused) {
                        try {
                            applyColor(hexText.trim().toColorInt())
                        } catch (_: Exception) {
                            hexText = colorToHex(currentColor)
                        }
                    }
                }
        )
        Spacer(Modifier.height(8.dp))

        ColorSliderRow("R", r) { r = it }
        ColorSliderRow("G", g) { g = it }
        ColorSliderRow("B", b) { b = it }
        ColorSliderRow("A", a) { a = it }

        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) { Text("Cancel") }
            TextButton(onClick = onConfirm) { Text("OK") }
        }
    }
}

@Composable
private fun ColorSliderRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$value",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ThemeSwatches(onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(
        cs.primary, cs.onPrimary,
        cs.primaryContainer, cs.onPrimaryContainer,
        cs.secondary, cs.onSecondary,
        cs.tertiary, cs.onTertiary,
        cs.surface, cs.onSurface,
        cs.surfaceVariant, cs.onSurfaceVariant,
        cs.error, cs.outline
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (color in colors) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                    .clickable { onPick(color.toArgb()) }
            )
        }
    }
}

// ── Shadow dialog ────────────────────────────────────────────────────────────

@Composable
private fun ShadowDialog(
    initialRadius: Float,
    initialDx: Float,
    initialDy: Float,
    initialColor: Int,
    onPreview: (Float, Float, Float, Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // Slider progress: radius 0..200 -> 0..20.0, dx/dy 0..200 -> -10..10
    var radiusProgress by remember { mutableFloatStateOf((initialRadius * 10).coerceIn(0f, 200f)) }
    var dxProgress by remember { mutableFloatStateOf(((initialDx + 10f) * 10).coerceIn(0f, 200f)) }
    var dyProgress by remember { mutableFloatStateOf(((initialDy + 10f) * 10).coerceIn(0f, 200f)) }
    var color by remember { mutableIntStateOf(initialColor) }
    var pickingColor by remember { mutableStateOf(false) }

    val radius = radiusProgress / 10f
    val dx = (dxProgress - 100) / 10f
    val dy = (dyProgress - 100) / 10f

    LaunchedEffect(radiusProgress, dxProgress, dyProgress, color) {
        onPreview(radius, dx, dy, color)
    }

    Dialog(onDismissRequest = {
        if (pickingColor) pickingColor = false
        else { onPreview(initialRadius, initialDx, initialDy, initialColor); onDismiss() }
    }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            if (!pickingColor) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Text shadow", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))

                    SliderRow("Blur", "%.1f dp".format(radius), radiusProgress, 0f, 200f) {
                        radiusProgress = it
                    }
                    SliderRow("X offset", "%.1f dp".format(dx), dxProgress, 0f, 200f) {
                        dxProgress = it
                    }
                    SliderRow("Y offset", "%.1f dp".format(dy), dyProgress, 0f, 200f) {
                        dyProgress = it
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color", modifier = Modifier.width(64.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(androidx.compose.ui.graphics.Color(color))
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { pickingColor = true }) { Text("Pick\u2026") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = {
                            onPreview(initialRadius, initialDx, initialDy, initialColor)
                            onDismiss()
                        }) { Text("Cancel") }
                        TextButton(onClick = onConfirm) { Text("OK") }
                    }
                }
            } else {
                ColorPickerContent(
                    title = "Shadow color",
                    initialColor = color,
                    onColorChange = { c ->
                        color = c
                        onPreview(radius, dx, dy, c)
                    },
                    onBack = { pickingColor = false },
                    onConfirm = { pickingColor = false }
                )
            }
        }
    }
}

// ── Stroke dialog ────────────────────────────────────────────────────────────

@Composable
private fun StrokeDialog(
    initialWidth: Float,
    initialColor: Int,
    onPreview: (Float, Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var widthProgress by remember { mutableFloatStateOf((initialWidth * 10).coerceIn(0f, 200f)) }
    var color by remember { mutableIntStateOf(initialColor) }
    var pickingColor by remember { mutableStateOf(false) }

    val width = widthProgress / 10f

    LaunchedEffect(widthProgress, color) { onPreview(width, color) }

    Dialog(onDismissRequest = {
        if (pickingColor) pickingColor = false
        else { onPreview(initialWidth, initialColor); onDismiss() }
    }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            if (!pickingColor) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Text outline", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))

                    SliderRow("Width", "%.1f dp".format(width), widthProgress, 0f, 200f) {
                        widthProgress = it
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color", modifier = Modifier.width(64.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(androidx.compose.ui.graphics.Color(color))
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { pickingColor = true }) { Text("Pick\u2026") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { onPreview(initialWidth, initialColor); onDismiss() }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = onConfirm) { Text("OK") }
                    }
                }
            } else {
                ColorPickerContent(
                    title = "Stroke color",
                    initialColor = color,
                    onColorChange = { c ->
                        color = c
                        onPreview(width, c)
                    },
                    onBack = { pickingColor = false },
                    onConfirm = { pickingColor = false }
                )
            }
        }
    }
}

// ── Shared slider row ────────────────────────────────────────────────────────

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ── Font dialog ──────────────────────────────────────────────────────────────

private data class FontEntry(val family: String, val category: String, val subsets: List<String>)

@Composable
private fun FontDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val builtInFamilies = listOf("sans-serif", "serif", "monospace", "cursive")
    val builtInLabels = listOf("Sans-serif", "Serif", "Monospace", "Cursive")
    var selectedFont by remember { mutableStateOf(initial) }
    val isBuiltIn = selectedFont in builtInFamilies
    // Start on Google picker if the current font is already a Google font
    var showGooglePicker by remember { mutableStateOf(!isBuiltIn) }

    // Single Dialog — swap content inside to avoid dismiss-on-recomposition.
    // Immediately show Google picker if the current font is already a Google font.
    Dialog(onDismissRequest = {
        if (showGooglePicker) showGooglePicker = false else onDismiss()
    }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            if (!showGooglePicker) {
                // Built-in font list
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Font family",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    builtInFamilies.forEachIndexed { index, family ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(family) }
                                .padding(horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedFont == family,
                                onClick = { onConfirm(family) }
                            )
                            Text(builtInLabels[index])
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGooglePicker = true }
                            .padding(horizontal = 8.dp)
                    ) {
                        RadioButton(
                            selected = !isBuiltIn,
                            onClick = { showGooglePicker = true }
                        )
                        Text("Google Font\u2026")
                    }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
            } else {
                // Google Font picker
                GoogleFontPickerContent(
                    initial = if (isBuiltIn) initial else selectedFont,
                    onBack = { showGooglePicker = false },
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun GoogleFontPickerContent(
    initial: String,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    val allFonts = remember {
        try {
            val json = context.assets.open("google_fonts.json").bufferedReader().readText()
            val items = JSONObject(json).getJSONArray("items")
            (0 until items.length()).map { i ->
                val obj = items.getJSONObject(i)
                val subsetsArr = obj.getJSONArray("subsets")
                FontEntry(
                    obj.getString("family"),
                    obj.getString("category"),
                    (0 until subsetsArr.length()).map { j -> subsetsArr.getString(j) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val categories = remember { allFonts.map { it.category }.distinct().sorted() }
    val subsets = remember { allFonts.flatMap { it.subsets }.distinct().sorted() }

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSubset by remember { mutableStateOf<String?>(null) }
    var selectedFont by remember { mutableStateOf(initial) }
    var subsetText by remember { mutableStateOf("") }

    val filteredSubsets = remember(subsetText) {
        if (subsetText.isEmpty()) subsets
        else subsets.filter { it.contains(subsetText, ignoreCase = true) }
    }
    val filtered = remember(query, selectedCategory, selectedSubset) {
        allFonts.filter { font ->
            (selectedCategory == null || font.category == selectedCategory) &&
                    (selectedSubset == null || selectedSubset in font.subsets) &&
                    (query.isEmpty() || font.family.lowercase().contains(query.lowercase()))
        }.map { it.family }
    }

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            "Google Font",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All") }
                )
            }
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    },
                    label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        OutlinedTextField(
            value = subsetText,
            onValueChange = { subsetText = it; selectedSubset = null },
            label = { Text("Subset") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.height(120.dp)) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSubset = null; subsetText = "" }
                        .padding(horizontal = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedSubset == null,
                        onClick = { selectedSubset = null; subsetText = "" }
                    )
                    Text("All")
                }
            }
            items(filteredSubsets) { subset ->
                val label = subset.split("-")
                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSubset = subset; subsetText = label }
                        .padding(horizontal = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedSubset == subset,
                        onClick = { selectedSubset = subset; subsetText = label }
                    )
                    Text(label)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        LazyColumn(modifier = Modifier.height(260.dp)) {
            items(filtered, key = { it }) { family ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFont = family }
                        .padding(horizontal = 8.dp)
                ) {
                    RadioButton(
                        selected = family == selectedFont,
                        onClick = { selectedFont = family }
                    )
                    Text(family)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = onBack) { Text("Cancel") }
            TextButton(onClick = { onConfirm(selectedFont) }) { Text("OK") }
        }
    }
}

// ── Error dialog ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invalid input") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun escape(s: String) = s.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")

private fun colorToHex(color: Int) = String.format(
    "#%02X%02X%02X%02X",
    Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color)
)
