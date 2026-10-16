package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.ExploreDefaultTab
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.core.prefs.HeroTideProgress
import cx.aswin.boxlore.core.prefs.NavigationBlurLevel
import cx.aswin.boxlore.core.prefs.OpenAppTo
import cx.aswin.boxlore.core.prefs.SubscriptionsDefaultTab
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.prefs.ThemeSelection
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsPicture
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

/** Current values shown on [AppearanceSettingsPage]. Also used by [cx.aswin.boxlore.feature.settings.SettingsScreen]. */
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
    val miniPlayerSeekButtonsEnabled: Boolean = false,
    val artworkColorsEnabled: Boolean = true,
    val navigationBlurLevel: String = NavigationBlurLevel.DEFAULT,
    val heroCardStyle: String = HeroCardStyle.DEFAULT,
    val heroCoverShade: String = HeroCoverShade.UNSET,
    val heroTideProgress: String = HeroTideProgress.DEFAULT,
    val savedCustomTheme: ThemeSelection? = null,
    val fullPlayerStyle: String = FullPlayerStyle.STANDARD,
    /** Read-only here: Navigation hides Explore's opening tab without For you. Library > Recommendations sets it. */
    val discoverySections: DiscoverySections = DiscoverySections(),
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
    val onSetMiniPlayerSeekButtonsEnabled: (Boolean) -> Unit = {},
    val onSetThemePreset: (String) -> Unit = {},
    val onSetArtworkColorsEnabled: (Boolean) -> Unit = {},
    val onSetNavigationBlurLevel: (String) -> Unit = {},
    val onSetHeroCardStyle: (String) -> Unit = {},
    val onSetHeroCoverShade: (String) -> Unit = {},
    val onSetHeroTideProgress: (String) -> Unit = {},
    val onApplyTheme: (ThemeSelection, ThemeSelection?) -> Unit = { _, _ -> },
    val onSaveCustomTheme: (ThemeSelection) -> Unit = { onApplyTheme(it, it) },
    val onSetFullPlayerStyle: (String) -> Unit = {},
)

/**
 * Draws Home's hero cards for a [style] and Cover [coverShade] (the single-episode card, or the Jump
 * back in card when [collection] is true), fitted into the modifier's bounds. The app supplies it
 * from the Home feature, so Appearance shows the real cards without depending on Home; without it
 * the pages show the choices without previews.
 */
typealias AppearanceHeroPreview = @Composable (style: String, coverShade: String, collection: Boolean, modifier: Modifier) -> Unit

/** Where each Appearance row leads, so the page needs no navigation types of its own. */
internal enum class AppearanceSection { Theme, Home, Player, Navigation, Text }

/**
 * The Appearance overview: Display mode (the setting people change most, also on Theme & colors),
 * then one row per page, each showing what is picked now. Hero card previews live on the Home page.
 */
@Composable
internal fun AppearanceSettingsPage(
    state: AppearanceUiState,
    actions: AppearanceActions,
    onBack: () -> Unit,
    onOpenSection: (AppearanceSection) -> Unit,
) {
    val summary = appearanceSummary(state)
    SettingsScaffold(title = "Appearance", onBack = onBack) {
        SettingsGroup(title = "Display mode") {
            SettingsContent { DisplayModeSelector(state, actions) }
        }
        SettingsGroup {
            SettingsNavigationRow(title = "Theme & colors", supportingText = summary.theme, icon = Icons.Rounded.Palette, onClick = { onOpenSection(AppearanceSection.Theme) })
            SettingsDivider()
            SettingsNavigationRow(title = "Home", supportingText = summary.home, icon = Icons.Rounded.Home, onClick = { onOpenSection(AppearanceSection.Home) })
            SettingsDivider()
            SettingsNavigationRow(title = "Player", supportingText = summary.player, icon = Icons.Rounded.PlayCircle, onClick = { onOpenSection(AppearanceSection.Player) })
            SettingsDivider()
            SettingsNavigationRow(title = "Navigation", supportingText = summary.navigation, icon = Icons.Rounded.ViewDay, onClick = { onOpenSection(AppearanceSection.Navigation) })
            SettingsDivider()
            SettingsNavigationRow(title = "Text", supportingText = summary.text, icon = Icons.Rounded.TextFields, onClick = { onOpenSection(AppearanceSection.Text) })
        }
    }
}

/** What each Appearance row shows under its title. */
internal data class AppearanceSummary(val theme: String, val home: String, val player: String, val navigation: String, val text: String)

internal fun appearanceSummary(state: AppearanceUiState): AppearanceSummary {
    val look = themeLookOptions().firstOrNull { it.key == state.selectedLookKey() }?.name ?: "Custom"
    val player = if (FullPlayerStyle.sanitize(state.fullPlayerStyle) == FullPlayerStyle.BUNPOD) "bunpod" else "Standard"
    val start = when (state.currentOpenAppTo) {
        OpenAppTo.SUBSCRIPTIONS -> "Subscriptions"
        OpenAppTo.DOWNLOADS -> "Downloads"
        else -> "Home"
    }
    val letters = FontRoundness.entries.firstOrNull { it.key == FontRoundness.sanitizeKey(state.currentFontRoundness) }?.label ?: "Round"
    return AppearanceSummary(
        theme = if (state.artworkColorsEnabled) "$look · artwork colors on" else look,
        home = homeSummary(state),
        player = if (state.miniPlayerSeekButtonsEnabled) "$player · seek buttons on" else player,
        navigation = "${NavigationStyle.fromKey(state.currentNavigationStyle).label} bar · starts on $start",
        text = "$letters letters",
    )
}

/** The hero card style's name as Settings shows it. */
private fun heroStyleName(style: String): String = when (style) {
    HeroCardStyle.COVER -> "Cover"
    HeroCardStyle.TIDE -> "Tide"
    HeroCardStyle.DUOTONE -> "Duotone"
    HeroCardStyle.HALO -> "Halo"
    HeroCardStyle.MARQUEE -> "Marquee"
    else -> "Block"
}

/** The hero card style, with the colours it takes. */
private fun homeSummary(state: AppearanceUiState): String {
    val style = HeroCardStyle.sanitize(state.heroCardStyle)
    return heroStyleName(style) + " hero cards, " + heroColorsSummary(style, HeroCoverShade.forStyle(style, state.heroCoverShade))
}

/** What the colour choice does in each style: Cover's shade, Duotone's and Marquee's tones, Block's, Tide's and Halo's card. */
private fun heroColorsSummary(style: String, colors: String): String {
    val (dark, theme, artwork) = when (style) {
        HeroCardStyle.COVER -> Triple("dark shade", "app theme shade", "artwork shade")
        HeroCardStyle.DUOTONE -> Triple("black and white", "app theme tones", "artwork tones")
        HeroCardStyle.MARQUEE -> Triple("full color", "app theme tones", "artwork tones")
        else -> Triple("dark cards", "app theme color", "artwork color")
    }
    return when (colors) {
        HeroCoverShade.DARK -> dark
        HeroCoverShade.THEME -> theme
        else -> artwork
    }
}

/**
 * The picked hero style's look: a live preview of its single and collection cards on top, and that
 * style's own [options] (its colours, Tide's ring) right beneath, in one card, so each choice reads
 * as part of the style and its effect shows at once. Without a preview it is just the options.
 */
@Composable
internal fun AppearanceHeroLookCard(
    style: String,
    coverShade: String,
    heroPreview: AppearanceHeroPreview?,
    previewHeight: Dp,
    options: @Composable ColumnScope.() -> Unit,
) {
    val description = "Preview of ${heroStyleName(HeroCardStyle.sanitize(style))} hero cards"
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            if (heroPreview != null) {
                Box(Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
                    SettingsPicture {
                        Row(Modifier.fillMaxWidth().height(previewHeight).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            heroPreview(style, coverShade, false, Modifier.weight(1f).fillMaxHeight())
                            heroPreview(style, coverShade, true, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = if (heroPreview != null) 4.dp else 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                content = options,
            )
        }
    }
}

/** One of a hero style's options inside its look card: the connected choices, then a line on what they do. */
@Composable
internal fun AppearanceLookChoice(label: String, help: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppearanceChoicePicker(label, options, selected, onSelect)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DisplayModeSelector(state: AppearanceUiState, actions: AppearanceActions) {
    ConnectedOptionSelector(
        listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
        appearanceDisplayMode(state),
        onSelect = { mode ->
            val unlocked = automaticSurfaceStyle(state.currentSurfaceStyle)
            if (unlocked != state.currentSurfaceStyle) actions.onApplyTheme(state.themeSelection().copy(surfaceStyle = unlocked), null)
            actions.onSetThemeConfig(mode)
        },
    )
}

/** A labelled row of connected choices, for short lists that need no pictures. */
@Composable
internal fun AppearanceChoicePicker(
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

/** Fills a tile's picture when no hero preview is available. */
@Composable
internal fun AppearancePicturePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}
