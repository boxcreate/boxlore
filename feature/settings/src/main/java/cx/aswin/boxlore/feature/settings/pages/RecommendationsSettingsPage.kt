package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.prefs.DiscoverySection
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

private data class RecommendationSwitch(val section: DiscoverySection, val title: Int, val description: Int, val icon: ImageVector)

/** Home's switchable sections, in the order they appear on Home. */
private val HomeSwitches = listOf(
    RecommendationSwitch(DiscoverySection.DAILY_BRIEF, R.string.recommendations_brief, R.string.recommendations_brief_description, Icons.Rounded.Newspaper),
    RecommendationSwitch(DiscoverySection.PICKED_FOR_YOU, R.string.recommendations_picked_for_you, R.string.recommendations_picked_for_you_description, Icons.Rounded.AutoAwesome),
    RecommendationSwitch(DiscoverySection.BECAUSE_YOU_LIKE, R.string.recommendations_because_you_like, R.string.recommendations_because_you_like_description, Icons.Rounded.Favorite),
    RecommendationSwitch(DiscoverySection.CURATED_ROWS, R.string.recommendations_time_of_day, R.string.recommendations_time_of_day_description, Icons.Rounded.WbTwilight),
)

private val EpisodeSwitches = listOf(
    RecommendationSwitch(DiscoverySection.MORE_LIKE_THIS, R.string.recommendations_more_like_this, R.string.recommendations_more_like_this_description, Icons.Rounded.Podcasts),
)

/**
 * Library > Recommendations: one switch for each optional suggestion section. A section that is
 * off is hidden and makes no requests. Appearance > Home links here too.
 */
@Composable
internal fun RecommendationsSettingsPage(sections: DiscoverySections, onSet: (DiscoverySection, Boolean) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.recommendations_title), onBack = onBack) {
        RecommendationSwitchGroup(stringResource(R.string.recommendations_home_title), stringResource(R.string.recommendations_home_footer), HomeSwitches, sections, onSet)
        RecommendationSwitchGroup(stringResource(R.string.recommendations_episodes_title), null, EpisodeSwitches, sections, onSet)
    }
}

@Composable
private fun RecommendationSwitchGroup(
    title: String,
    footer: String?,
    rows: List<RecommendationSwitch>,
    sections: DiscoverySections,
    onSet: (DiscoverySection, Boolean) -> Unit,
) {
    SettingsGroup(title = title, footer = footer) {
        rows.forEachIndexed { index, row ->
            if (index > 0) SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(row.title),
                supportingText = stringResource(row.description),
                checked = sections.isOn(row.section),
                onCheckedChange = { onSet(row.section, it) },
                icon = row.icon,
            )
        }
    }
}
