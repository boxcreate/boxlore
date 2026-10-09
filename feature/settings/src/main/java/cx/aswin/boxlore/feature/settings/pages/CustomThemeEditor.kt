package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveThemeSeedColor
import cx.aswin.boxlore.core.designsystem.theme.toThemeBrandHex
import cx.aswin.boxlore.core.prefs.ThemeSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomThemeEditor(state: AppearanceUiState, onDismiss: () -> Unit, onSave: (ThemeSelection) -> Unit) {
    val context = LocalContext.current
    val initial = remember { initialThemeSeeds(context, state) }
    var encoded by rememberSaveable { mutableStateOf(initial.encode()) }
    var surface by rememberSaveable { mutableStateOf(editorSurfaceStyle(state.currentSurfaceStyle)) }
    val effectiveDark = LocalEffectiveDarkTheme.current
    var dark by rememberSaveable { mutableStateOf(effectiveDark) }
    var role by rememberSaveable { mutableIntStateOf(0) }
    var validInput by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val seeds = CustomThemeSeeds.decode(encoded) ?: initial
    // Update on every drag frame; debouncing would keep cancelling while the finger moves.
    val colors = remember(seeds, surface, dark) { generatePersonalColorScheme(seeds, dark, surface) }
    val roles = listOf("Primary", "Secondary", "Tertiary")
    LaunchedEffect(Unit) { AnalyticsHelper.trackCustomThemeEditorOpened() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom theme", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton(enabled = validInput && !saving, onClick = {
                        if (validInput && !saving) {
                            saving = true
                            onSave(ThemeSelection(encoded, surface))
                        }
                    }) { Text("Save theme", maxLines = 1) }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().imePadding()) {
            val compact = maxHeight < 520.dp || LocalConfiguration.current.fontScale > 1.4f
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ThemeEditorPreview(dark, colors, compact, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { dark = it }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Accent colors", style = MaterialTheme.typography.titleMedium)
                    ThemeRoleSelector(roles, seeds, colors, role) {
                        role = it
                        validInput = true
                    }
                    Text(themeRoleDescription(role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ThemeColorControls(roles[role], themeRoleColor(seeds, colors, role), onValidChange = { validInput = it }) {
                        encoded = seeds.withRoleColor(role, it).encode()
                    }
                    if (role != 0) {
                        TextButton(onClick = {
                            encoded = seeds.withRoleColor(role, null).encode()
                            validInput = true
                        }) { Text("Use suggested color") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Background style", style = MaterialTheme.typography.titleMedium)
                    ConnectedOptionSelector(editorSurfaceOptions(surface), surface, onSelect = { surface = it })
                    Text("Text and container shades adapt to light and dark mode. The preview uses the same color roles as the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.artworkColorsEnabled) {
                        Text("Artwork colors are on for episode and podcast pages. Turn them off in Appearance to use your theme on those pages too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun themeRoleDescription(role: Int): String = when (role) {
    1 -> "Supporting controls and selected navigation items."
    2 -> "Highlights and details that need a separate accent."
    else -> "Main actions, playback controls and key highlights."
}

private fun initialThemeSeeds(context: Context, state: AppearanceUiState): CustomThemeSeeds {
    CustomThemeSeeds.decode(state.currentThemeBrand)?.let { return it }
    val light = resolveBoxLoreColorScheme(context, false, state.isDynamicColorEnabled, state.currentThemeBrand, state.currentSurfaceStyle)
    val primary = if (state.isDynamicColorEnabled) light.primary else resolveThemeSeedColor(state.currentThemeBrand)
    return CustomThemeSeeds(primary, light.secondary, light.tertiary)
}

private fun editorSurfaceStyle(surface: String): String {
    val style = automaticSurfaceStyle(surface)
    val supported = style in listOf(SurfaceStyles.STANDARD, SurfaceStyles.CLASSIC_DYNAMIC, SurfaceStyles.DYNAMIC_OLED_WHITE)
    return if (supported || findThemePreset(style) != null) style else SurfaceStyles.STANDARD
}

private fun editorSurfaceOptions(surface: String): List<Pair<String, String>> {
    val options = listOf(SurfaceStyles.STANDARD to "Tinted", SurfaceStyles.CLASSIC_DYNAMIC to "Neutral", SurfaceStyles.DYNAMIC_OLED_WHITE to "Pure")
    return if (findThemePreset(surface) != null) options + (surface to "Saved") else options
}

private fun themeRoleColor(seeds: CustomThemeSeeds, colors: ColorScheme, role: Int): Color = when (role) {
    1 -> seeds.secondary ?: colors.secondary
    2 -> seeds.tertiary ?: colors.tertiary
    else -> seeds.primary
}

private fun CustomThemeSeeds.withRoleColor(role: Int, color: Color?): CustomThemeSeeds = when (role) {
    1 -> copy(secondary = color)
    2 -> copy(tertiary = color)
    else -> copy(primary = color ?: primary)
}

@Composable
private fun ThemeRoleSelector(roles: List<String>, seeds: CustomThemeSeeds, colors: ColorScheme, role: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        roles.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).semantics { selected = role == index },
                shape = MaterialTheme.shapes.large,
                color = if (role == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = themeRoleColor(seeds, colors, index), shape = CircleShape, modifier = Modifier.size(32.dp)) {}
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ThemeEditorPreview(dark: Boolean, colors: ColorScheme, compact: Boolean, modifier: Modifier = Modifier, onDarkChange: (Boolean) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectedOptionSelector(listOf("light" to "Light preview", "dark" to "Dark preview"), if (dark) "dark" else "light", onSelect = { onDarkChange(it == "dark") })
        CustomThemePreview(colors, compact)
    }
}

@Composable
private fun ThemeColorControls(label: String, color: Color, onValidChange: (Boolean) -> Unit, onChange: (Color) -> Unit) {
    val initialHsv = remember(label) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hue by remember(label) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(label) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(label) { mutableFloatStateOf(initialHsv[2]) }
    LaunchedEffect(color) {
        onValidChange(true)
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }
    var hex by remember(label, color) { mutableStateOf(color.toThemeBrandHex().drop(1)) }
    fun update() {
        onValidChange(true)
        onChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        ThemeColorSwatches(label, onChange = {
            onValidChange(true)
            onChange(it)
        })
        OutlinedTextField(value = hex, onValueChange = { input ->
            hex = input.filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6)
            onValidChange(hex.length == 6)
            if (hex.length == 6) {
                val picked = Color(hex.toLong(16) or 0xFF000000)
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(picked.toArgb(), it) }
                hue = hsv[0]
                saturation = hsv[1]
                brightness = hsv[2]
                onChange(picked)
            }
        }, isError = hex.length != 6, supportingText = { if (hex.length != 6) Text("Enter all 6 characters, for example 6255E8.") }, label = { Text("Hex color") }, prefix = { Text("#") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Palette, null) })
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Slider(modifier = Modifier.semantics { contentDescription = "$label hue" }, value = hue, valueRange = 0f..360f, onValueChange = {
            hue = it
            update()
        })
        Text("Saturation", style = MaterialTheme.typography.labelMedium)
        Slider(modifier = Modifier.semantics { contentDescription = "$label saturation" }, value = saturation, onValueChange = {
            saturation = it
            update()
        })
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(modifier = Modifier.semantics { contentDescription = "$label brightness" }, value = brightness, onValueChange = {
            brightness = it
            update()
        })
    }
}

@Composable
private fun ThemeColorSwatches(role: String, onChange: (Color) -> Unit) {
    val swatches = listOf(0xFF6255E8, 0xFF287BDE, 0xFF008C86, 0xFF527A35, 0xFFC46A25, 0xFFBD466F, 0xFF756C82)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(swatches) { value ->
            val color = Color(value)
            Surface(onClick = { onChange(color) }, color = color, shape = CircleShape, modifier = Modifier.size(48.dp).semantics { contentDescription = "Use ${color.toThemeBrandHex()} for $role" }) {}
        }
    }
}
