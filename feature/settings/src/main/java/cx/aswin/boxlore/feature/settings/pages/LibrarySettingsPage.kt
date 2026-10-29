package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ContentLanguagesSheet
import cx.aswin.boxlore.core.designsystem.components.ContentRegionPickerSheet
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ContentRegions
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.dialogs.ClearSavedShowsDialog
import cx.aswin.boxlore.feature.settings.dialogs.savedShowsLabel
import kotlinx.coroutines.launch

/**
 * Discovery region and language preferences for [LibrarySettingsPage].
 */
data class LibraryDiscoveryPreferences(
    val currentRegion: String,
    /** The languages in effect: without English when the listener switched it off. */
    val contentLanguages: List<String>,
    val onSetRegion: (String) -> Unit,
    val onSetContentLanguages: (List<String>) -> Unit,
    /** The listener's "Include English" choice, inverted (D9). */
    val englishOff: Boolean = false,
    val onSetEnglishOff: (Boolean) -> Unit = {},
    /** Saved episode lists of shows the listener opened but doesn't follow (P4 PR 20). */
    val savedShows: SavedShowsControl = SavedShowsControl(),
)

/** Counts and clears the saved copies of unfollowed shows (`ShowPageFeedPort.unfollowedCount` / `clearUnfollowed`). */
data class SavedShowsControl(val count: suspend () -> Int = { 0 }, val clear: suspend () -> Int = { 0 },)

@Composable
internal fun LibrarySettingsPage(
    discoveryPreferences: LibraryDiscoveryPreferences,
    onAddRssClick: () -> Unit,
    onBack: () -> Unit,
    onOpenRecommendations: () -> Unit = {},
) {
    var isCountryFaqExpanded by rememberSaveable { mutableStateOf(false) }
    val collapseCountryFaq = { isCountryFaqExpanded = false }
    // Saveable, so an open sheet survives rotation, folding and window resizes.
    var showCountrySheet by rememberSaveable { mutableStateOf(false) }
    var showLanguagesSheet by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = "Library",
        onBack = onBack,
        onUnconsumedTap = if (isCountryFaqExpanded) collapseCountryFaq else null,
    ) {
        // One thing per row: the chart country, then the languages recommendations use.
        SettingsGroup(title = "Charts", footer = "Sets the top charts in Explore.") {
            SettingsNavigationRow(
                title = "Country",
                supportingText = ContentRegions.displayLabel(discoveryPreferences.currentRegion),
                icon = Icons.Rounded.Public,
                onClick = {
                    collapseCountryFaq()
                    showCountrySheet = true
                },
            )
            SettingsDivider()
            CountryNotListedFaq(
                expanded = isCountryFaqExpanded,
                onExpand = { isCountryFaqExpanded = true },
                onCollapse = collapseCountryFaq,
            )
        }

        SettingsGroup(title = "Recommendations", footer = "For You, search and vibes use these languages.") {
            SettingsNavigationRow(
                title = "Languages",
                supportingText = languagesSummary(discoveryPreferences.contentLanguages),
                icon = Icons.Rounded.Translate,
                onClick = {
                    collapseCountryFaq()
                    showLanguagesSheet = true
                },
            )
        }

        // Its own card, apart from region and languages: it switches suggestions off app-wide.
        SettingsGroup {
            SettingsNavigationRow(
                title = stringResource(R.string.recommendations_title),
                supportingText = stringResource(R.string.recommendations_row_description),
                icon = Icons.Rounded.AutoAwesome,
                onClick = {
                    collapseCountryFaq()
                    onOpenRecommendations()
                },
            )
        }

        SettingsGroup(title = "RSS") {
            SettingsNavigationRow(
                title = "Add podcast by RSS feed",
                supportingText = "For podcasts that are not in the catalog",
                icon = Icons.Rounded.RssFeed,
                onClick = {
                    collapseCountryFaq()
                    onAddRssClick()
                },
            )
        }
        SavedShowsGroup(discoveryPreferences.savedShows, onOpen = collapseCountryFaq)
    }

    if (showCountrySheet) {
        ContentRegionPickerSheet(
            activeRegion = discoveryPreferences.currentRegion,
            onSwitchRegion = discoveryPreferences.onSetRegion,
            onDismiss = { showCountrySheet = false },
        )
    }
    if (showLanguagesSheet) {
        ContentLanguagesSheet(
            country = discoveryPreferences.currentRegion,
            selectedLanguages = discoveryPreferences.contentLanguages,
            englishOff = discoveryPreferences.englishOff,
            onLanguagesChange = discoveryPreferences.onSetContentLanguages,
            onEnglishOffChange = discoveryPreferences.onSetEnglishOff,
            onDismiss = { showLanguagesSheet = false },
        )
    }
}

@Composable
private fun CountryNotListedFaq(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        if (expanded) onCollapse() else onExpand()
                    },
                ).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Why is my country not listed?",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = GoogleSansWeight.medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector =
                if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onCollapse,
                    ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Why isn't my country listed?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "We are actively working to expand our global coverage! Currently, we prioritize adding new countries based on active user demand.",
                )
                Text(
                    text = "How this affects your experience:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Search is still global: You can still search for, find, and access all content, even if your country isn't officially listed yet.",
                )
                FaqParagraph(
                    "Smart recommendations still work: The recommendation engine will still suggest the closest matches based on your listening habits, irrespective of your country selection.",
                )
                FaqParagraph(
                    "What country changes: The top charts in Explore. Your languages stay as they are.",
                )
                FaqParagraph(
                    "Languages are separate: Pick them under Recommendations › Languages for For You, search and vibes. Charts stay country-based and aren't filtered by language.",
                )
                Text(
                    text = "Want another country or language?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Tell us via Feedback or GitHub — we expand coverage based on demand.",
                )
            }
        }
    }
}

@Composable
private fun FaqParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The Languages row's summary: the languages in effect, as the listener reads them ("English, Hindi"). */
internal fun languagesSummary(languages: List<String>): String = languages.mapNotNull { ContentRegions.LANGUAGE_LABELS[it] }.joinToString(", ")

/** The Clear saved shows row's summary: counting, nothing saved, or how many shows have saved lists. */
internal fun savedShowsSupportingText(count: Int?): String = when (count) {
    null -> "Counting…"
    0 -> "Nothing saved for shows you don’t follow"
    else -> "${savedShowsLabel(count)} you don’t follow"
}

/**
 * Library settings › Storage › Clear saved shows (P4 PR 20): the count of unfollowed shows with
 * saved episode lists, and a confirmation before they are cleared. The open confirmation is
 * saveable, so it stays through window changes and recreation.
 */
@Composable
private fun SavedShowsGroup(control: SavedShowsControl, onOpen: () -> Unit) {
    // The count the open confirmation shows, saved so it survives recreation; null while closed.
    var confirming by rememberSaveable { mutableStateOf<Int?>(null) }
    // Counted again after each clear; null while counting.
    var reads by remember { mutableIntStateOf(0) }
    val count by produceState<Int?>(initialValue = null, reads) { value = control.count() }
    val scope = rememberCoroutineScope()
    SettingsGroup(title = "Storage") {
        SettingsActionRow(
            title = "Clear saved shows",
            supportingText = savedShowsSupportingText(count),
            icon = Icons.Rounded.DeleteSweep,
            onClick = {
                onOpen()
                count?.takeIf { it > 0 }?.let { confirming = it }
            },
        )
    }
    confirming?.let { shown ->
        ClearSavedShowsDialog(
            count = shown,
            onConfirm = {
                confirming = null
                scope.launch {
                    control.clear()
                    reads++
                }
            },
            onDismiss = { confirming = null },
        )
    }
}
