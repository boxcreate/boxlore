package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.prefs.AppLanguage
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
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsPicture
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A home screen widget the listener can add from Appearance › Widgets, named as the widget picker
 * names it, with the size its preview is drawn for.
 */
data class AddableWidget(
    val key: String,
    val name: String,
    val description: String,
    val previewWidthDp: Int = 320,
    val previewHeightDp: Int = 170,
    /** Whether this widget follows the Your Shows cover setting, so its card carries that choice. */
    val usesShowsCoverStyle: Boolean = false,
)

/**
 * Draws a home screen widget as it looks on the home screen, fitted into the modifier's bounds. The
 * app supplies it from the widgets feature, so Appearance shows the real widgets without depending
 * on it; without it the Widgets page lists them by name only.
 */
typealias AppearanceWidgetPreview = @Composable (widget: AddableWidget, modifier: Modifier) -> Unit

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
    val currentWidgetShowsArtwork: String = WidgetShowsArtwork.ROUNDED,
    /** Every widget the app offers. Whether the home screen app can add them is asked when the page opens. */
    val addableWidgets: List<AddableWidget> = emptyList(),
    val currentExploreDefaultTab: String = ExploreDefaultTab.FOR_YOU,
    val currentSubscriptionsDefaultTab: String = SubscriptionsDefaultTab.SHOWS,
    val currentSubscriptionsTabStyle: String = SubscriptionsTabStyle.TOP,
    val miniPlayerSeekButtonsEnabled: Boolean = false,
    /** Show pages list episodes as short title rows (Appearance › Show pages, and each show page's menu). */
    val episodeTitlesOnly: Boolean = false,
    val artworkColorsEnabled: Boolean = true,
    val navigationBlurLevel: String = NavigationBlurLevel.DEFAULT,
    val heroCardStyle: String = HeroCardStyle.DEFAULT,
    val heroCoverShade: String = HeroCoverShade.UNSET,
    val heroTideProgress: String = HeroTideProgress.DEFAULT,
    val savedCustomTheme: ThemeSelection? = null,
    val fullPlayerStyle: String = FullPlayerStyle.STANDARD,
    /** Read-only here: Navigation hides Explore's opening tab without For you. Library > Recommendations sets it. */
    val discoverySections: DiscoverySections = DiscoverySections(),
    /** The language boxlore's own text is shown in (App language page). */
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
) {
    /** A theme is its brand, surface and wallpaper colours together; [selection] is the same only if all match. */
    fun hasSameTheme(selection: ThemeSelection): Boolean = currentThemeBrand == selection.brand &&
        currentSurfaceStyle == selection.surfaceStyle &&
        isDynamicColorEnabled == selection.wallpaperColors
}

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
    val onSetWidgetShowsArtwork: (String) -> Unit = {},
    /** Whether the current home screen app adds a widget when asked. Asked once when the Widgets page opens. */
    val canAddWidgets: () -> Boolean = { false },
    /** Asks the home screen app to add the widget with this key; it confirms with the listener itself. */
    val onAddWidget: (String) -> Unit = {},
    /** The key of each widget the home screen app has just added after such a request. */
    val addedWidgets: Flow<String> = emptyFlow(),
    val onSetExploreDefaultTab: (String) -> Unit = {},
    val onSetSubscriptionsDefaultTab: (String) -> Unit = {},
    val onSetSubscriptionsTabStyle: (String) -> Unit = {},
    val onSetMiniPlayerSeekButtonsEnabled: (Boolean) -> Unit = {},
    val onSetEpisodeTitlesOnly: (Boolean) -> Unit = {},
    val onSetThemePreset: (String) -> Unit = {},
    val onSetArtworkColorsEnabled: (Boolean) -> Unit = {},
    val onSetNavigationBlurLevel: (String) -> Unit = {},
    val onSetHeroCardStyle: (String) -> Unit = {},
    val onSetHeroCoverShade: (String) -> Unit = {},
    val onSetHeroTideProgress: (String) -> Unit = {},
    val onApplyTheme: (ThemeSelection, ThemeSelection?) -> Unit = { _, _ -> },
    val onSaveCustomTheme: (ThemeSelection) -> Unit = { onApplyTheme(it, it) },
    val onSetFullPlayerStyle: (String) -> Unit = {},
    /** Applies the App language at once; the app owns how (per-app language, or restarting the screen). */
    val onSetAppLanguage: (AppLanguage) -> Unit = {},
)

/**
 * Draws Home's hero cards for a [style] and Cover [coverShade] (the single-episode card, or the Jump
 * back in card when [collection] is true), fitted into the modifier's bounds. The app supplies it
 * from the Home feature, so Appearance shows the real cards without depending on Home; without it
 * the pages show the choices without previews.
 */
typealias AppearanceHeroPreview = @Composable (style: String, coverShade: String, collection: Boolean, modifier: Modifier) -> Unit

/** Where each Appearance row leads, so the page needs no navigation types of its own. */
internal enum class AppearanceSection { Theme, Home, ShowPages, Player, Navigation, Text, Widgets, Language }

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
    TrackScreenView("appearance")
    val summary = appearanceSummary(state)
    SettingsScaffold(title = stringResource(R.string.settings_page_appearance), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_display_mode_title)) {
            SettingsContent { DisplayModeSelector(state, actions) }
        }
        SettingsGroup {
            SettingsNavigationRow(
                title = stringResource(R.string.settings_app_language_title),
                supportingText = appLanguageName(state.appLanguage),
                icon = Icons.Rounded.Language,
                onClick = { onOpenSection(AppearanceSection.Language) },
            )
        }
        SettingsGroup {
            SettingsNavigationRow(title = stringResource(R.string.settings_page_theme), supportingText = summary.theme.asString(), icon = Icons.Rounded.Palette, onClick = { onOpenSection(AppearanceSection.Theme) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_home), supportingText = summary.home.asString(), icon = Icons.Rounded.Home, onClick = { onOpenSection(AppearanceSection.Home) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_show_pages), supportingText = summary.showPages.asString(), icon = Icons.AutoMirrored.Rounded.ViewList, onClick = { onOpenSection(AppearanceSection.ShowPages) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_player), supportingText = summary.player.asString(), icon = Icons.Rounded.PlayCircle, onClick = { onOpenSection(AppearanceSection.Player) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_navigation), supportingText = summary.navigation.asString(), icon = Icons.Rounded.ViewDay, onClick = { onOpenSection(AppearanceSection.Navigation) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_text), supportingText = summary.text.asString(), icon = Icons.Rounded.TextFields, onClick = { onOpenSection(AppearanceSection.Text) })
            SettingsDivider()
            SettingsNavigationRow(title = stringResource(R.string.settings_page_widgets), supportingText = summary.widgets.asString(), icon = Icons.Rounded.Widgets, onClick = { onOpenSection(AppearanceSection.Widgets) })
        }
    }
}

/** What each Appearance row shows under its title. */
internal data class AppearanceSummary(
    val theme: UiText,
    val home: UiText,
    val showPages: UiText,
    val player: UiText,
    val navigation: UiText,
    val text: UiText,
    val widgets: UiText,
)

internal fun appearanceSummary(state: AppearanceUiState): AppearanceSummary {
    val look = UiText.res(themeLookOptions().firstOrNull { it.key == state.selectedLookKey() }?.name ?: R.string.settings_theme_look_custom)
    val player = UiText.res(if (FullPlayerStyle.sanitize(state.fullPlayerStyle) == FullPlayerStyle.BUNPOD) R.string.player_style_bunpod else R.string.player_style_standard)
    val start = UiText.res(
        when (state.currentOpenAppTo) {
            OpenAppTo.SUBSCRIPTIONS -> R.string.settings_navigation_start_subscriptions
            OpenAppTo.DOWNLOADS -> R.string.settings_navigation_start_downloads
            else -> R.string.settings_navigation_start_home
        },
    )
    val letters = UiText.res(fontRoundnessLabel(FontRoundness.sanitizeKey(state.currentFontRoundness)))
    return AppearanceSummary(
        theme = if (state.artworkColorsEnabled) UiText.res(R.string.settings_appearance_summary_theme_artwork, look) else look,
        home = homeSummary(state),
        showPages = showPagesSummary(state),
        player = if (state.miniPlayerSeekButtonsEnabled) UiText.res(R.string.settings_appearance_summary_player_seek, player) else player,
        navigation = UiText.res(R.string.settings_appearance_summary_navigation, UiText.res(navigationStyleLabel(NavigationStyle.fromKey(state.currentNavigationStyle))), start),
        text = UiText.res(R.string.settings_appearance_summary_letters, letters),
        widgets = widgetsSummary(state),
    )
}

/** The letter shape's name as Settings shows it; the keys are stored values and never change. */
@StringRes
internal fun fontRoundnessLabel(key: String): Int = when (key) {
    FontRoundness.CRISP -> R.string.settings_letters_crisp
    FontRoundness.SOFT -> R.string.settings_letters_soft
    else -> R.string.settings_letters_round
}

/** The navigation bar style's name as Settings shows it. */
@StringRes
internal fun navigationStyleLabel(style: NavigationStyle): Int = when (style) {
    NavigationStyle.Classic -> R.string.settings_navigation_style_classic
    NavigationStyle.Floating -> R.string.settings_navigation_style_floating
}

/** The widgets' colour source, then how Your Shows cuts its covers. */
private fun widgetsSummary(state: AppearanceUiState): UiText {
    val system = WidgetAppearance.sanitize(state.currentWidgetAppearance) == WidgetAppearance.SYSTEM
    val shapes = WidgetShowsArtwork.usesShapes(state.currentWidgetShowsArtwork)
    return UiText.res(
        when {
            system && shapes -> R.string.settings_appearance_summary_widgets_system_shapes
            system -> R.string.settings_appearance_summary_widgets_system_covers
            shapes -> R.string.settings_appearance_summary_widgets_theme_shapes
            else -> R.string.settings_appearance_summary_widgets_theme_covers
        },
    )
}

/** The hero card style's name as Settings shows it. */
@StringRes
private fun heroStyleName(style: String): Int = when (style) {
    HeroCardStyle.COVER -> R.string.hero_card_style_cover
    HeroCardStyle.TIDE -> R.string.hero_card_style_tide
    HeroCardStyle.DUOTONE -> R.string.hero_card_style_duotone
    HeroCardStyle.HALO -> R.string.hero_card_style_halo
    HeroCardStyle.MARQUEE -> R.string.hero_card_style_marquee
    else -> R.string.hero_card_style_block
}

/** The hero card style, with the colours it takes. */
private fun homeSummary(state: AppearanceUiState): UiText {
    val style = HeroCardStyle.sanitize(state.heroCardStyle)
    return UiText.res(
        R.string.settings_appearance_summary_home,
        UiText.res(heroStyleName(style)),
        UiText.res(heroColorsSummary(style, HeroCoverShade.forStyle(style, state.heroCoverShade))),
    )
}

/** What the colour choice does in each style: Cover's shade, Duotone's and Marquee's tones, Block's, Tide's and Halo's card. */
@StringRes
private fun heroColorsSummary(style: String, colors: String): Int {
    val (dark, theme, artwork) = when (style) {
        HeroCardStyle.COVER -> Triple(R.string.settings_hero_summary_dark_shade, R.string.settings_hero_summary_theme_shade, R.string.settings_hero_summary_artwork_shade)
        HeroCardStyle.DUOTONE -> Triple(R.string.settings_hero_summary_black_white, R.string.settings_hero_summary_theme_tones, R.string.settings_hero_summary_artwork_tones)
        HeroCardStyle.MARQUEE -> Triple(R.string.settings_hero_summary_full_color, R.string.settings_hero_summary_theme_tones, R.string.settings_hero_summary_artwork_tones)
        else -> Triple(R.string.settings_hero_summary_dark_cards, R.string.settings_hero_summary_theme_color, R.string.settings_hero_summary_artwork_color)
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
    val description = stringResource(R.string.settings_hero_preview_description, stringResource(heroStyleName(HeroCardStyle.sanitize(style))))
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
        listOf("system" to stringResource(R.string.settings_display_mode_system), "light" to stringResource(R.string.settings_display_mode_light), "dark" to stringResource(R.string.settings_display_mode_dark)),
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
