package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveThemeSeedColor
import cx.aswin.boxlore.core.designsystem.theme.toThemeBrandHex
import cx.aswin.boxlore.core.prefs.ThemeSelection
import cx.aswin.boxlore.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomThemeEditor(state: AppearanceUiState, onDismiss: () -> Unit, onSave: (ThemeSelection) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val initial = remember { initialThemeSeeds(context, state) }
    var encoded by rememberSaveable { mutableStateOf(initial.encode()) }
    val initialSurface = remember { editorSurfaceStyle(state.currentSurfaceStyle) }
    var surface by rememberSaveable { mutableStateOf(initialSurface) }
    val effectiveDark = LocalEffectiveDarkTheme.current
    var dark by rememberSaveable { mutableStateOf(effectiveDark) }
    var role by rememberSaveable { mutableIntStateOf(0) }
    var hexDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val seeds = CustomThemeSeeds.decode(encoded) ?: initial
    val validInput = hexDraft == null || parseEditorHex(hexDraft.orEmpty()) != null
    // Editing seeds stays independent of preview mode; suggested tones always use the light scheme.
    val lightColors = remember(seeds, surface) { generatePersonalColorScheme(seeds, false, surface) }
    val colors = remember(seeds, surface, dark) { if (dark) generatePersonalColorScheme(seeds, true, surface) else lightColors }
    val color = themeRoleColor(seeds, lightColors, role)
    val labels = listOf(stringResource(R.string.theme_editor_primary), stringResource(R.string.theme_editor_secondary), stringResource(R.string.theme_editor_tertiary))
    val onColorChange: (androidx.compose.ui.graphics.Color) -> Unit = {
        encoded = seeds.withRoleColor(role, it).encode()
        hexDraft = null
    }
    LaunchedEffect(Unit) { AnalyticsHelper.trackCustomThemeEditorOpened() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Scaffold(
            topBar = {
                ThemeEditorTopBar(validInput && !saving, onDismiss) {
                    if (validInput && !saving) {
                        focus.clearFocus()
                        saving = true
                        onSave(ThemeSelection(encoded, surface))
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize().imePadding()) {
                val fontScale = LocalDensity.current.fontScale
                val sideBySide = maxWidth >= 700.dp && fontScale < 1.7f
                val compact = maxHeight < 520.dp || fontScale > 1.4f
                val minimal = !sideBySide && maxHeight < 280.dp
                val editor: @Composable () -> Unit = {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("theme-editor-controls")
                            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ThemeRoleSelector(labels, seeds, lightColors, role) {
                            focus.clearFocus()
                            role = it
                            hexDraft = null
                        }
                        ThemeRoleExplanation(role, seeds) {
                            focus.clearFocus()
                            encoded = seeds.withRoleColor(role, null).encode()
                            hexDraft = null
                        }
                        ThemeColorControls(
                            label = labels[role],
                            color = color,
                            hex = hexDraft ?: color.toThemeBrandHex().drop(1),
                            expanded = expanded,
                            onExpandedChange = {
                                focus.clearFocus()
                                expanded = it
                            },
                            onChange = onColorChange,
                            onHexChange = {
                                hexDraft = normalizeEditorHex(it)
                                parseEditorHex(hexDraft.orEmpty())?.let { picked -> encoded = seeds.withRoleColor(role, picked).encode() }
                            },
                        )
                        ThemeBackgroundOptions(surface, initialSurface, onSelect = { surface = it })
                        Text(stringResource(R.string.theme_editor_contrast_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.artworkColorsEnabled) {
                            Text(stringResource(R.string.theme_editor_artwork_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (sideBySide) {
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxSize().align(Alignment.TopCenter)) {
                        ThemeEditorPreview(dark, colors, compact, minimal, Modifier.weight(0.42f).padding(start = 16.dp, top = 12.dp, end = 8.dp)) { dark = it }
                        Column(Modifier.weight(0.58f)) { editor() }
                    }
                } else {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxSize().align(Alignment.TopCenter)) {
                        ThemeEditorPreview(dark, colors, compact, minimal, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { dark = it }
                        Column(Modifier.weight(1f)) { editor() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeEditorTopBar(saveEnabled: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.4f
    TopAppBar(
        title = { Text(stringResource(R.string.theme_editor_title), maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis) },
        expandedHeight = if (largeText) 104.dp else 64.dp,
        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.theme_editor_back)) } },
        actions = {
            FilledTonalButton(
                modifier = Modifier.padding(end = 12.dp).testTag("theme-editor-save"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                enabled = saveEnabled,
                onClick = onSave,
            ) { Text(stringResource(R.string.theme_editor_save), maxLines = 1) }
        },
    )
}

private fun initialThemeSeeds(context: Context, state: AppearanceUiState): CustomThemeSeeds {
    CustomThemeSeeds.decode(state.currentThemeBrand)?.let { return it }
    val light = resolveBoxLoreColorScheme(context, false, state.isDynamicColorEnabled, state.currentThemeBrand, state.currentSurfaceStyle)
    val primary = if (state.isDynamicColorEnabled) light.primary else resolveThemeSeedColor(state.currentThemeBrand)
    return CustomThemeSeeds(primary, light.secondary, light.tertiary)
}
