package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.core.prefs.HeroTideProgress
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsPicture
import cx.aswin.boxlore.feature.settings.components.SettingsPictureRow
import cx.aswin.boxlore.feature.settings.components.SettingsPictureTile
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection

private data class HeroStyleOption(val key: String, val label: Int, val description: Int)

private val HeroStyleOptions = listOf(
    HeroStyleOption(HeroCardStyle.BLOCK, R.string.hero_card_style_block, R.string.hero_card_style_block_description),
    HeroStyleOption(HeroCardStyle.COVER, R.string.hero_card_style_cover, R.string.hero_card_style_cover_description),
    HeroStyleOption(HeroCardStyle.TIDE, R.string.hero_card_style_tide, R.string.hero_card_style_tide_description),
    HeroStyleOption(HeroCardStyle.DUOTONE, R.string.hero_card_style_duotone, R.string.hero_card_style_duotone_description),
    HeroStyleOption(HeroCardStyle.HALO, R.string.hero_card_style_halo, R.string.hero_card_style_halo_description),
    HeroStyleOption(HeroCardStyle.MARQUEE, R.string.hero_card_style_marquee, R.string.hero_card_style_marquee_description),
)

/** The colour choice's name and footer for each style. */
private fun heroColorsCopy(style: String): Pair<Int, Int> = when (style) {
    HeroCardStyle.DUOTONE -> R.string.hero_duotone_colors_title to R.string.hero_duotone_colors_footer
    HeroCardStyle.BLOCK, HeroCardStyle.HALO, HeroCardStyle.TIDE -> R.string.hero_card_colors_title to R.string.hero_card_colors_footer
    HeroCardStyle.MARQUEE -> R.string.hero_marquee_colors_title to R.string.hero_marquee_colors_footer
    else -> R.string.hero_cover_shade_title to R.string.hero_cover_shade_footer
}

/**
 * The hero picture's height in a [tileWidth] tile: the shape of the preview stage above (260dp on a
 * phone-wide card), kept between 150dp and 240dp so short and wide tiles both stay readable.
 */
internal fun heroPickerPictureHeight(tileWidth: Dp): Dp = ((tileWidth - HeroTilePadding * 2) * HeroPictureShape).coerceIn(HeroPictureMin, HeroPictureMax)

private val HeroTilePadding = 12.dp
private const val HeroPictureShape = 0.78f
private val HeroPictureMin = 150.dp
private val HeroPictureMax = 240.dp

/**
 * Home: a preview of the picked hero card style with its Jump back in card, every style as a
 * scrolling row of picture tiles drawn from the real cards, the colours the artwork styles take,
 * and the top bar shortcuts.
 */
@Composable
internal fun AppearanceHomePage(
    state: AppearanceUiState,
    actions: AppearanceActions,
    heroPreview: AppearanceHeroPreview?,
    onBack: () -> Unit,
    onOpenRecommendations: () -> Unit = {},
) {
    val style = HeroCardStyle.sanitize(state.heroCardStyle)
    // The stored choice, unpicked until a colour is chosen: each preview applies its own style's default.
    val storedColors = state.heroCoverShade
    val shade = HeroCoverShade.forStyle(style, storedColors)
    // Marquee is a phone layout; tablets show Cover cards, so the preview does too.
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val previewStyle = if (tablet && style == HeroCardStyle.MARQUEE) HeroCardStyle.COVER else style
    SettingsScaffold(title = "Home", onBack = onBack) {
        SettingsTileSection(title = stringResource(R.string.hero_card_style_title), footer = stringResource(R.string.hero_card_style_footer)) {
            SettingsPictureRow(HeroStyleOptions, selectedIndex = HeroStyleOptions.indexOfFirst { it.key == style }) { option, modifier, width ->
                SettingsPictureTile(
                    label = stringResource(option.label),
                    description = stringResource(if (tablet && option.key == HeroCardStyle.MARQUEE) R.string.hero_card_style_marquee_description_tablet else option.description),
                    selected = style == option.key,
                    onClick = { actions.onSetHeroCardStyle(option.key) },
                    modifier = modifier,
                ) {
                    // The picture keeps the Home card's shape as the tile widens.
                    val pictureHeight = heroPickerPictureHeight(width)
                    if (heroPreview != null) {
                        SettingsPicture { heroPreview(option.key, storedColors, false, Modifier.fillMaxWidth().height(pictureHeight)) }
                    } else {
                        AppearancePicturePlaceholder(Modifier.height(pictureHeight))
                    }
                }
            }
        }
        // The picked style's look sits right under the styles: a live preview with that style's own
        // options beneath it, so every change shows at once and clearly belongs to the style.
        val styleLabel = stringResource(HeroStyleOptions.first { it.key == style }.label)
        SettingsTileSection(title = stringResource(R.string.hero_card_look_title, styleLabel)) {
            AppearanceHeroLookCard(previewStyle, storedColors, heroPreview, previewHeight = 260.dp) {
                // Every style takes the colour choice; Block fills its card with it.
                val (colorsTitle, colorsFooter) = heroColorsCopy(style)
                AppearanceLookChoice(
                    label = stringResource(colorsTitle),
                    help = stringResource(colorsFooter),
                    options = listOf(
                        // Marquee keeps covers in full colour for this choice, so it says so.
                        HeroCoverShade.DARK to stringResource(
                            if (style == HeroCardStyle.MARQUEE) R.string.hero_marquee_colors_full else R.string.hero_cover_shade_dark,
                        ),
                        HeroCoverShade.ARTWORK to stringResource(R.string.hero_cover_shade_artwork),
                        HeroCoverShade.THEME to stringResource(R.string.hero_cover_shade_theme),
                    ),
                    selected = shade,
                    onSelect = actions.onSetHeroCoverShade,
                )
                // Tide alone rings its Play disc with Resume progress, so only it offers the ring's shape.
                if (style == HeroCardStyle.TIDE) {
                    AppearanceLookChoice(
                        label = stringResource(R.string.hero_tide_progress_title),
                        help = stringResource(R.string.hero_tide_progress_footer),
                        options = listOf(
                            HeroTideProgress.WAVY to stringResource(R.string.hero_tide_progress_wavy),
                            HeroTideProgress.CIRCLE to stringResource(R.string.hero_tide_progress_circle),
                        ),
                        selected = HeroTideProgress.sanitize(state.heroTideProgress),
                        onSelect = actions.onSetHeroTideProgress,
                    )
                }
            }
        }
        // Home's switchable sections live on Library > Recommendations; this opens the same page.
        SettingsGroup {
            SettingsNavigationRow(
                title = stringResource(R.string.recommendations_title),
                supportingText = stringResource(R.string.recommendations_row_description),
                icon = Icons.Rounded.AutoAwesome,
                onClick = onOpenRecommendations,
            )
        }
        SettingsGroup(title = "Top bar") {
            SettingsSwitchRow(
                title = "Move Home shortcuts to Library",
                supportingText = "Show Settings and Feedback in Library instead of Home",
                checked = state.homeShortcutsInLibrary,
                onCheckedChange = actions.onSetHomeShortcutsInLibrary,
                icon = Icons.Rounded.Home,
            )
        }
    }
}
