package cx.aswin.boxlore.feature.home.settings.pages

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

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
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ContentRegionLanguagePicker
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.home.settings.components.SettingsContent
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold

/**
 * Export/import callbacks for [LibrarySettingsPage], grouped to keep its parameter count small.
 * Also used by [cx.aswin.boxlore.feature.home.settings.SettingsScreen].
 */
data class LibraryBackupActions(
    val onExportJson: () -> Unit,
    val onExportOpml: () -> Unit,
    val onImportJson: () -> Unit,
    val onImportOpml: () -> Unit,
)

@Composable
internal fun LibrarySettingsPage(
    currentRegion: String,
    contentLanguages: List<String>,
    onSetRegion: (String) -> Unit,
    onSetContentLanguages: (List<String>) -> Unit,
    onAddRssClick: () -> Unit,
    backupActions: LibraryBackupActions,
    onBack: () -> Unit,
) {
    var isCountryFaqExpanded by rememberSaveable { mutableStateOf(false) }
    val collapseCountryFaq = { isCountryFaqExpanded = false }

    SettingsScaffold(
        title = "Library",
        onBack = onBack,
        onUnconsumedTap = if (isCountryFaqExpanded) collapseCountryFaq else null,
    ) {
        SettingsGroup(title = "Discovery") {
            SettingsContent {
                ContentRegionLanguagePicker(
                    activeRegion = currentRegion,
                    selectedLanguages = contentLanguages,
                    onSwitchRegion = {
                        collapseCountryFaq()
                        onSetRegion(it)
                    },
                    onLanguagesChange = {
                        collapseCountryFaq()
                        onSetContentLanguages(it)
                    },
                )
            }
            SettingsDivider()
            CountryNotListedFaq(
                expanded = isCountryFaqExpanded,
                onExpand = { isCountryFaqExpanded = true },
                onCollapse = collapseCountryFaq,
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

        SettingsGroup(title = "Export") {
            SettingsNavigationRow(
                title = "Full library backup",
                supportingText = "Subscriptions, history, likes, settings, and Missing episodes? shows (JSON)",
                icon = Icons.Rounded.FileUpload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onExportJson()
                },
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Subscriptions only",
                supportingText = "OPML file",
                icon = Icons.Rounded.FileUpload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onExportOpml()
                },
            )
        }

        SettingsGroup(title = "Import") {
            SettingsNavigationRow(
                title = "Full library backup",
                supportingText = "Restore from a JSON backup",
                icon = Icons.Rounded.FileDownload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onImportJson()
                },
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Subscriptions only",
                supportingText = "Import from an OPML file",
                icon = Icons.Rounded.FileDownload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onImportOpml()
                },
            )
        }
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
                    "What country changes: Charts and regional ranking. Changing country also resets your language picks to a recommended set for that market.",
                )
                FaqParagraph(
                    "Languages are separate: Use the language chips in this section to fine-tune For You and vibes (English stays on; pick up to three more). Charts stay country-based and are not hard-filtered by language.",
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
