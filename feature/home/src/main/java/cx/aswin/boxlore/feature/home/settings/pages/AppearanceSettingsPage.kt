package cx.aswin.boxlore.feature.home.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.BrandSeeds
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.buildGoogleSansFamily
import cx.aswin.boxlore.core.designsystem.theme.buildSectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.customThemeBrandHex
import cx.aswin.boxlore.core.designsystem.theme.isCustomThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.isExactThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.resolveThemeSeedColor
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.core.prefs.OpenAppTo
import cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.feature.home.settings.components.AccentSwatchGrid
import cx.aswin.boxlore.feature.home.settings.components.SettingsChoiceRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsContent
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.home.settings.components.SettingsSwitchRow
import cx.aswin.boxlore.feature.home.settings.dialogs.AccentColorPickerDialog

/** Current values shown on [AppearanceSettingsPage]. Also used by [cx.aswin.boxlore.feature.home.settings.SettingsScreen]. */
data class AppearanceUiState(
    val currentThemeConfig: String,
    val isDynamicColorEnabled: Boolean,
    val currentThemeBrand: String,
    val currentSurfaceStyle: String,
    val currentFontRoundness: String = FontRoundness.DEFAULT_KEY,
    val currentNavigationStyle: String = NavigationStyle.Floating.key,
    val currentOpenAppTo: String = OpenAppTo.HOME,
    val homeShortcutsInLibrary: Boolean = false,
    val currentWidgetAppearance: String = WidgetAppearance.APP,
    val currentExploreDefaultTab: String = ExploreDefaultTab.FOR_YOU,
    val currentSubscriptionsDefaultTab: String = SubscriptionsDefaultTab.SHOWS,
    val currentSubscriptionsTabStyle: String = SubscriptionsTabStyle.TOP,
)

/** Callbacks for [AppearanceSettingsPage], grouped to keep the page's parameter count small. */
data class AppearanceActions(
    val onSetThemeConfig: (String) -> Unit,
    val onToggleDynamicColor: (Boolean) -> Unit,
    val onSetThemeBrand: (String) -> Unit,
    val onSetSurfaceStyle: (String) -> Unit,
    val onSetFontRoundness: (String) -> Unit = {},
    val onSetNavigationStyle: (String) -> Unit = {},
    val onSetOpenAppTo: (String) -> Unit = {},
    val onSetHomeShortcutsInLibrary: (Boolean) -> Unit = {},
    val onSetWidgetAppearance: (String) -> Unit = {},
    val onSetExploreDefaultTab: (String) -> Unit = {},
    val onSetSubscriptionsDefaultTab: (String) -> Unit = {},
    val onSetSubscriptionsTabStyle: (String) -> Unit = {},
)

@Composable
internal fun AppearanceSettingsPage(
    state: AppearanceUiState,
    actions: AppearanceActions,
    onBack: () -> Unit,
) {
    val look = BackgroundLook.fromSurfaceStyle(state.currentSurfaceStyle)

    fun applyStyle(style: String) {
        selectSurfaceStyle(
            style = style,
            onSetSurfaceStyle = actions.onSetSurfaceStyle,
            onToggleDynamicColor = actions.onToggleDynamicColor,
            onSetThemeBrand = actions.onSetThemeBrand,
        )
    }

    SettingsScaffold(
        title = "Appearance",
        onBack = onBack,
    ) {
        ThemeModeSection(
            currentThemeConfig = state.currentThemeConfig,
            currentSurfaceStyle = state.currentSurfaceStyle,
            onSetThemeConfig = actions.onSetThemeConfig,
            onUnlockToAutomatic = { automaticStyle ->
                applyStyle(automaticStyle)
            },
        )

        BackgroundLookSection(
            selectedLook = look,
            onSelectLook = { nextLook ->
                applyStyle(nextLook.surfaceStyleKey)
            },
        )

        ColorsSection(
            isDynamicColorEnabled = state.isDynamicColorEnabled,
            onToggleDynamicColor = actions.onToggleDynamicColor,
            currentThemeBrand = state.currentThemeBrand,
            onSetThemeBrand = actions.onSetThemeBrand,
        )

        LetteringSection(
            currentFontRoundness = state.currentFontRoundness,
            onSetFontRoundness = actions.onSetFontRoundness,
        )

        NavigationStyleSection(
            currentNavigationStyle = state.currentNavigationStyle,
            onSetNavigationStyle = actions.onSetNavigationStyle,
        )

        OpenAppToSection(
            currentOpenAppTo = state.currentOpenAppTo,
            onSetOpenAppTo = actions.onSetOpenAppTo,
        )

        DefaultTabsSection(
            currentExploreDefaultTab = state.currentExploreDefaultTab,
            onSetExploreDefaultTab = actions.onSetExploreDefaultTab,
            currentSubscriptionsDefaultTab = state.currentSubscriptionsDefaultTab,
            onSetSubscriptionsDefaultTab = actions.onSetSubscriptionsDefaultTab,
        )

        SubscriptionsTabStyleSection(
            currentSubscriptionsTabStyle = state.currentSubscriptionsTabStyle,
            onSetSubscriptionsTabStyle = actions.onSetSubscriptionsTabStyle,
        )

        HomeChromeSection(
            homeShortcutsInLibrary = state.homeShortcutsInLibrary,
            onSetHomeShortcutsInLibrary = actions.onSetHomeShortcutsInLibrary,
        )

        WidgetsSection(
            currentWidgetAppearance = state.currentWidgetAppearance,
            onSetWidgetAppearance = actions.onSetWidgetAppearance,
        )
    }
}

@Composable
private fun NavigationStyleSection(
    currentNavigationStyle: String,
    onSetNavigationStyle: (String) -> Unit,
) {
    val selectedStyle = NavigationStyle.fromKey(currentNavigationStyle)
    SettingsGroup(
        title = "Navigation",
        footer = "Choose the floating pill or a classic Material bottom bar.",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options = NavigationStyle.entries.map { it.key to it.label },
                selected = selectedStyle.key,
                onSelect = onSetNavigationStyle,
            )
        }
    }
}

@Composable
private fun OpenAppToSection(
    currentOpenAppTo: String,
    onSetOpenAppTo: (String) -> Unit,
) {
    val selected =
        when (currentOpenAppTo) {
            OpenAppTo.SUBSCRIPTIONS -> OpenAppTo.SUBSCRIPTIONS
            OpenAppTo.DOWNLOADS -> OpenAppTo.DOWNLOADS
            else -> OpenAppTo.HOME
        }
    SettingsGroup(
        title = "Open app to",
        footer = "Choose where boxlore opens after a cold start. Applies the next time you fully relaunch the app.",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options =
                listOf(
                    OpenAppTo.HOME to "Home",
                    OpenAppTo.SUBSCRIPTIONS to "Subscriptions",
                    OpenAppTo.DOWNLOADS to "Downloads",
                ),
                selected = selected,
                onSelect = onSetOpenAppTo,
                labelStyle = MaterialTheme.typography.labelMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DefaultTabsSection(
    currentExploreDefaultTab: String,
    onSetExploreDefaultTab: (String) -> Unit,
    currentSubscriptionsDefaultTab: String,
    onSetSubscriptionsDefaultTab: (String) -> Unit,
) {
    SettingsGroup(
        title = "Default tabs",
        footer = "Used when you open Explore or Subscriptions. Links that pick a tab still go there.",
    ) {
        SettingsContent {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DefaultTabPicker(
                    label = "Explore",
                    options =
                    listOf(
                        ExploreDefaultTab.FOR_YOU to "For You",
                        ExploreDefaultTab.TOP to "Top",
                    ),
                    selected = ExploreDefaultTab.sanitize(currentExploreDefaultTab),
                    onSelect = onSetExploreDefaultTab,
                )
                DefaultTabPicker(
                    label = "Subscriptions",
                    options =
                    listOf(
                        SubscriptionsDefaultTab.SHOWS to "Shows",
                        SubscriptionsDefaultTab.NEW_EPISODES to "New episodes",
                    ),
                    selected = SubscriptionsDefaultTab.sanitize(currentSubscriptionsDefaultTab),
                    onSelect = onSetSubscriptionsDefaultTab,
                )
            }
        }
    }
}

@Composable
private fun DefaultTabPicker(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ConnectedOptionSelector(
            options = options,
            selected = selected,
            onSelect = onSelect,
            labelStyle = MaterialTheme.typography.labelMedium,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SubscriptionsTabStyleSection(
    currentSubscriptionsTabStyle: String,
    onSetSubscriptionsTabStyle: (String) -> Unit,
) {
    val selected = SubscriptionsTabStyle.sanitize(currentSubscriptionsTabStyle)
    SettingsGroup(
        title = "Subscriptions tabs",
        footer = "Choose whether tabs sit in the top header or float at the bottom (like Explore).",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options =
                listOf(
                    SubscriptionsTabStyle.TOP to "Top",
                    SubscriptionsTabStyle.FLOATING to "Floating",
                ),
                selected = selected,
                onSelect = onSetSubscriptionsTabStyle,
                labelStyle = MaterialTheme.typography.labelMedium,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ThemeModeSection(
    currentThemeConfig: String,
    currentSurfaceStyle: String,
    onSetThemeConfig: (String) -> Unit,
    onUnlockToAutomatic: (String) -> Unit,
) {
    val modeLock = themeModeLockFor(currentSurfaceStyle)
    val selectedMode = modeLock?.mode ?: ThemeMode.fromKey(currentThemeConfig) ?: ThemeMode.SYSTEM

    SettingsGroup(
        title = "Theme",
        footer =
        modeLock?.let {
            "This background was locked to ${it.mode.label.lowercase()}. Choosing another theme unlocks it."
        } ?: "Chooses whether the app looks light or dark. Backgrounds follow this.",
    ) {
        SettingsContent {
            if (modeLock != null) {
                ForcedModeBadge(modeLock.mode)
            }
            ConnectedOptionSelector(
                options = ThemeMode.entries.map { it.key to it.label },
                selected = selectedMode.key,
                onSelect = { key ->
                    val mode = ThemeMode.fromKey(key) ?: return@ConnectedOptionSelector
                    if (modeLock != null && mode != modeLock.mode) {
                        onUnlockToAutomatic(modeLock.automaticSiblingStyle)
                    }
                    onSetThemeConfig(mode.key)
                },
            )
        }
    }
}

@Composable
private fun ForcedModeBadge(mode: ThemeMode) {
    Surface(
        modifier = Modifier.padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Locked to ${mode.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
    }
}

@Composable
private fun BackgroundLookSection(
    selectedLook: BackgroundLook,
    onSelectLook: (BackgroundLook) -> Unit,
) {
    SettingsGroup(
        title = "Background",
        footer = "How the app’s surfaces look. They follow Theme above.",
    ) {
        BackgroundLook.entries.forEachIndexed { index, look ->
            SettingsChoiceRow(
                title = look.label,
                supportingText = look.subtext,
                selected = selectedLook == look,
                onClick = { onSelectLook(look) },
            )
            if (index < BackgroundLook.entries.lastIndex) {
                SettingsDivider()
            }
        }
    }
}

@Composable
private fun LetteringSection(
    currentFontRoundness: String,
    onSetFontRoundness: (String) -> Unit,
) {
    var previewExpanded by remember { mutableStateOf(false) }
    val selectedKey = FontRoundness.sanitizeKey(currentFontRoundness)
    val axis = remember(selectedKey) { FontRoundness.axisValue(selectedKey) }
    val context = LocalContext.current
    val titleFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 600) }
    val bodyFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 400) }
    val captionFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 500) }
    val headerFamily = remember(context, axis) { buildSectionHeaderFontFamily(context, axis) }

    SettingsGroup(
        title = "Lettering",
        footer = "How rounded Google Sans Flex letters feel across the app.",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options = FontRoundness.entries.map { it.key to it.label },
                selected = selectedKey,
                onSelect = onSetFontRoundness,
            )
            LetteringTips(
                modifier = Modifier.padding(top = 10.dp),
            )
            LetteringPreviewToggle(
                expanded = previewExpanded,
                onToggle = { previewExpanded = !previewExpanded },
            )
            AnimatedVisibility(visible = previewExpanded) {
                LetteringPreviewSamples(
                    titleFamily = titleFamily,
                    bodyFamily = bodyFamily,
                    captionFamily = captionFamily,
                    headerFamily = headerFamily,
                )
            }
        }
    }
}

@Composable
private fun LetteringTips(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Most visible in large titles; smaller text changes less.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Some skins (like Xiaomi HyperOS) may use the system font instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LetteringPreviewToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "Hide preview" else "Show preview",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LetteringPreviewSamples(
    titleFamily: FontFamily,
    bodyFamily: FontFamily,
    captionFamily: FontFamily,
    headerFamily: FontFamily,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LetteringPreviewLine(
            label = "Section",
            sample = "Your morning stack",
            fontFamily = headerFamily,
            fontWeight = FontWeight.Normal,
            fontSizeSp = 22f,
            lineHeightSp = 26f,
        )
        LetteringPreviewLine(
            label = "Title",
            sample = "The Daily Briefing",
            fontFamily = titleFamily,
            fontWeight = FontWeight.Normal,
            fontSizeSp = 18f,
            lineHeightSp = 22f,
        )
        LetteringPreviewLine(
            label = "Body",
            sample = "A short episode about curiosity, craft, and listening habits.",
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSizeSp = 15f,
            lineHeightSp = 22f,
        )
        LetteringPreviewLine(
            label = "Caption",
            sample = "Updated just now · 24 min",
            fontFamily = captionFamily,
            fontWeight = FontWeight.Normal,
            fontSizeSp = 12f,
            lineHeightSp = 16f,
        )
    }
}

@Composable
private fun LetteringPreviewLine(
    label: String,
    sample: String,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSizeSp: Float,
    lineHeightSp: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = sample,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WidgetsSection(
    currentWidgetAppearance: String,
    onSetWidgetAppearance: (String) -> Unit,
) {
    SettingsGroup(
        title = "Widgets",
        footer =
        "App theme uses the same Theme, Background, and Colors as boxlore. " +
            "System uses the launcher’s light/dark and wallpaper accents.",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options =
                listOf(
                    WidgetAppearance.APP to "App theme",
                    WidgetAppearance.SYSTEM to "System",
                ),
                selected = WidgetAppearance.sanitize(currentWidgetAppearance),
                onSelect = onSetWidgetAppearance,
            )
        }
    }
}

@Composable
private fun HomeChromeSection(
    homeShortcutsInLibrary: Boolean,
    onSetHomeShortcutsInLibrary: (Boolean) -> Unit,
) {
    SettingsGroup(
        title = "Home",
        footer =
        "When this is on, Home only shows the logo. Settings and Feedback move to the Library top bar.",
    ) {
        SettingsSwitchRow(
            title = "Cleaner Home",
            supportingText = "Move Settings and Feedback to Library",
            checked = homeShortcutsInLibrary,
            onCheckedChange = onSetHomeShortcutsInLibrary,
            icon = Icons.Rounded.Home,
        )
    }
}

@Composable
private fun ColorsSection(
    isDynamicColorEnabled: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    currentThemeBrand: String,
    onSetThemeBrand: (String) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val customSelected = isCustomThemeBrand(currentThemeBrand)
    val customPreview = resolveThemeSeedColor(currentThemeBrand)
    val customHex = customThemeBrandHex(currentThemeBrand)
    val exactSelected = isExactThemeBrand(currentThemeBrand)

    SettingsGroup(
        title = "Colors",
        footer =
        if (isDynamicColorEnabled) {
            "Uses your wallpaper. Turn off to pick a fixed color."
        } else {
            null
        },
    ) {
        SettingsSwitchRow(
            title = "Wallpaper colors",
            supportingText = "Use colors from your home-screen wallpaper",
            checked = isDynamicColorEnabled,
            onCheckedChange = onToggleDynamicColor,
        )
        AnimatedVisibility(visible = !isDynamicColorEnabled) {
            val seeds =
                remember {
                    BrandSeeds.map { (key, brand) ->
                        Triple(key, brand.first, brand.second)
                    }
                }
            Column {
                SettingsDivider()
                AccentSwatchGrid(
                    seeds = seeds,
                    selectedKey = if (customSelected) "" else currentThemeBrand,
                    onSelect = onSetThemeBrand,
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Custom color",
                    supportingText =
                    when {
                        exactSelected && customHex != null ->
                            "$customHex · exact, not recommended"
                        customSelected && customHex != null -> customHex
                        else -> "Pick any accent with the full color picker"
                    },
                    selected = customSelected,
                    onClick = { showColorPicker = true },
                    leading = {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = customPreview,
                            border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {}
                    },
                )
            }
        }
    }

    if (showColorPicker) {
        AccentColorPickerDialog(
            initialColor = customPreview,
            initialExact = exactSelected,
            onConfirm = { hex ->
                onSetThemeBrand(hex)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

private fun selectSurfaceStyle(
    style: String,
    onSetSurfaceStyle: (String) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSetThemeBrand: (String) -> Unit,
) {
    onSetSurfaceStyle(style)
    when (style) {
        SurfaceStyles.CLASSIC_DYNAMIC -> {
            onToggleDynamicColor(false)
            onSetThemeBrand(DEFAULT_BRAND)
        }

        SurfaceStyles.STANDARD -> {
            onToggleDynamicColor(true)
        }

        SurfaceStyles.DYNAMIC_OLED_WHITE -> {
            onToggleDynamicColor(false)
        }
    }
}

/**
 * Legacy locked styles still resolve in the theme engine. Changing Theme unlocks them
 * to Soft/Pure automatic so Theme can drive light/dark again.
 */
private fun themeModeLockFor(surfaceStyle: String): ThemeModeLock? = when (surfaceStyle) {
    SurfaceStyles.AMOLED ->
        ThemeModeLock(
            mode = ThemeMode.DARK,
            automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
        )
    SurfaceStyles.PURE_WHITE ->
        ThemeModeLock(
            mode = ThemeMode.LIGHT,
            automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
        )
    SurfaceStyles.CLASSIC_DARK ->
        ThemeModeLock(
            mode = ThemeMode.DARK,
            automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
        )
    SurfaceStyles.CLASSIC_LIGHT ->
        ThemeModeLock(
            mode = ThemeMode.LIGHT,
            automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
        )
    else -> null
}

private enum class BackgroundLook(
    val label: String,
    val subtext: String,
    val surfaceStyleKey: String,
) {
    Classic(
        label = "boxlore classic",
        subtext = "Gentle gray when dark, warm cream when light",
        surfaceStyleKey = SurfaceStyles.CLASSIC_DYNAMIC,
    ),
    Pure(
        label = "Pure",
        subtext = "True black when dark, pure white when light",
        surfaceStyleKey = SurfaceStyles.DYNAMIC_OLED_WHITE,
    ),
    MaterialYouSoft(
        label = "Material You Soft",
        subtext = "Colored surfaces from your wallpaper (or your accent below)",
        surfaceStyleKey = SurfaceStyles.STANDARD,
    ),
    ;

    companion object {
        fun fromSurfaceStyle(style: String): BackgroundLook = when (style) {
            SurfaceStyles.CLASSIC_DYNAMIC,
            SurfaceStyles.CLASSIC_DARK,
            SurfaceStyles.CLASSIC_LIGHT,
            -> Classic

            SurfaceStyles.DYNAMIC_OLED_WHITE,
            SurfaceStyles.AMOLED,
            SurfaceStyles.PURE_WHITE,
            -> Pure

            else -> MaterialYouSoft
        }
    }
}

private data class ThemeModeLock(
    val mode: ThemeMode,
    val automaticSiblingStyle: String,
)

private enum class ThemeMode(
    val key: String,
    val label: String,
) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromKey(key: String): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}

private const val DEFAULT_BRAND = "violet"
