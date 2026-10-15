package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
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
    HeroStyleOption(HeroCardStyle.DUOTONE, R.string.hero_card_style_duotone, R.string.hero_card_style_duotone_description),
    HeroStyleOption(HeroCardStyle.HALO, R.string.hero_card_style_halo, R.string.hero_card_style_halo_description),
)

/** The colour choice's name and footer for each style that colours its cards from the artwork. */
private fun heroColorsCopy(style: String): Pair<Int, Int> = when (style) {
    HeroCardStyle.DUOTONE -> R.string.hero_duotone_colors_title to R.string.hero_duotone_colors_footer
    HeroCardStyle.HALO -> R.string.hero_halo_colors_title to R.string.hero_halo_colors_footer
    else -> R.string.hero_cover_shade_title to R.string.hero_cover_shade_footer
}

/**
 * Home: a preview of the picked hero card style with its Jump back in card, every style as a
 * scrolling row of picture tiles drawn from the real cards, the colours the artwork styles take,
 * and the top bar shortcuts.
 */
@Composable
internal fun AppearanceHomePage(state: AppearanceUiState, actions: AppearanceActions, heroPreview: AppearanceHeroPreview?, onBack: () -> Unit) {
    val style = HeroCardStyle.sanitize(state.heroCardStyle)
    val shade = HeroCoverShade.sanitize(state.heroCoverShade)
    SettingsScaffold(title = "Home", onBack = onBack) {
        if (heroPreview != null) {
            SettingsTileSection(title = stringResource(R.string.hero_card_preview_title)) {
                AppearanceHeroPreviewStage(style, shade, heroPreview, height = 260.dp)
            }
        }
        SettingsTileSection(title = stringResource(R.string.hero_card_style_title), footer = stringResource(R.string.hero_card_style_footer)) {
            SettingsPictureRow(HeroStyleOptions, selectedIndex = HeroStyleOptions.indexOfFirst { it.key == style }) { option, modifier ->
                SettingsPictureTile(
                    label = stringResource(option.label),
                    description = stringResource(option.description),
                    selected = style == option.key,
                    onClick = { actions.onSetHeroCardStyle(option.key) },
                    modifier = modifier,
                ) {
                    if (heroPreview != null) {
                        SettingsPicture { heroPreview(option.key, shade, false, Modifier.fillMaxWidth().height(170.dp)) }
                    } else {
                        AppearancePicturePlaceholder()
                    }
                }
            }
        }
        // Every style but Block colours its cards from the artwork, so only they take these colours.
        if (HeroCardStyle.usesArtworkColors(style)) {
            val (colorsTitle, colorsFooter) = heroColorsCopy(style)
            SettingsGroup(footer = stringResource(colorsFooter)) {
                SettingsContent {
                    AppearanceChoicePicker(
                        label = stringResource(colorsTitle),
                        options = listOf(
                            HeroCoverShade.DARK to stringResource(R.string.hero_cover_shade_dark),
                            HeroCoverShade.ARTWORK to stringResource(R.string.hero_cover_shade_artwork),
                            HeroCoverShade.THEME to stringResource(R.string.hero_cover_shade_theme),
                        ),
                        selected = shade,
                        onSelect = actions.onSetHeroCoverShade,
                    )
                }
            }
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
