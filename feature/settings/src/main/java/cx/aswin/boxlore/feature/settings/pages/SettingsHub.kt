package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.feature.settings.LocalSettingsSplitPane
import cx.aswin.boxlore.feature.settings.ProfileSettingsDestination
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsCategoryCard
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntryImpression
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntrySurface
import cx.aswin.boxlore.feature.settings.settingsCategoryColors
import cx.aswin.boxlore.feature.settings.settingsCategoryTone

/** What the hub's Cloud Sync card says about the account: [email] is null when signed out. */
internal data class SettingsHubAccount(val email: String?, val syncStatus: CloudSyncUiStatus)

/** What the hub's Cloud Sync card says about the account; nothing when the app offers no sign-in. */
internal fun settingsHubAccount(signInAvailable: Boolean, accountStatus: String?, syncStatus: CloudSyncUiStatus): SettingsHubAccount? =
    if (signInAvailable) SettingsHubAccount(accountStatus, syncStatus) else null

/** One hub card, in the order the hub lists them. */
private data class HubCard(
    val destination: ProfileSettingsDestination,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

/**
 * The Settings hub. [account] lets the Cloud Sync card say whether you're signed in and synced;
 * without it the card keeps its general description. From [SETTINGS_HUB_TWO_COLUMN_DP] wide (and
 * not beside the tablet list) cards sit two to a row.
 */
@Composable
internal fun SettingsHub(
    onBack: () -> Unit,
    onNavigate: (ProfileSettingsDestination) -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    showWearOs: Boolean = false,
    account: SettingsHubAccount? = null,
) {
    SupportEntryImpression(SupportEntrySurface.SettingsTopBar)
    val cards = hubCards(showWearOs, account)
    SettingsScaffold(
        title = stringResource(R.string.settings_page_hub),
        onBack = onBack,
        actions = {
            FilledTonalButton(
                onClick = { onNavigate(ProfileSettingsDestination.Support) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.settings_page_support),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_page_support),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = !LocalSettingsSplitPane.current && maxWidth >= SETTINGS_HUB_TWO_COLUMN_DP.dp
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                cards.chunked(if (twoColumns) 2 else 1).forEach { row ->
                    if (row.size == 1) {
                        HubCategoryCard(row.single(), onNavigate)
                    } else {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { card -> HubCategoryCard(card, onNavigate, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }
                }
                onCheckForUpdates?.let { check ->
                    SettingsGroup(modifier = Modifier.testTag(SETTINGS_HUB_UPDATES_TAG)) {
                        SettingsNavigationRow(
                            title = stringResource(R.string.settings_check_for_updates),
                            icon = Icons.Rounded.SystemUpdate,
                            onClick = check,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCategoryCard(card: HubCard, onNavigate: (ProfileSettingsDestination) -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = settingsCategoryColors(settingsCategoryTone(card.destination))
    SettingsCategoryCard(
        title = card.title,
        description = card.description,
        icon = card.icon,
        containerColor = container,
        contentColor = content,
        onClick = { onNavigate(card.destination) },
        modifier = modifier.testTag(HUB_CARD_TAG_PREFIX + card.destination.name),
    )
}

@Composable
private fun hubCards(showWearOs: Boolean, account: SettingsHubAccount?): List<HubCard> = listOfNotNull(
    HubCard(ProfileSettingsDestination.SyncAndBackups, stringResource(R.string.settings_page_sync), hubSyncDescription(account), Icons.Rounded.CloudSync),
    HubCard(ProfileSettingsDestination.Library, stringResource(R.string.settings_page_library), stringResource(R.string.settings_hub_library_description), Icons.AutoMirrored.Rounded.LibraryBooks),
    HubCard(ProfileSettingsDestination.Appearance, stringResource(R.string.settings_page_appearance), stringResource(R.string.settings_hub_appearance_description), Icons.Rounded.Palette),
    HubCard(ProfileSettingsDestination.Playback, stringResource(R.string.settings_page_playback), stringResource(R.string.settings_hub_playback_description), Icons.Rounded.Tune),
    HubCard(ProfileSettingsDestination.Downloads, stringResource(R.string.settings_page_downloads), stringResource(R.string.settings_hub_downloads_description), Icons.Rounded.DownloadForOffline),
    if (showWearOs) {
        HubCard(ProfileSettingsDestination.WearOs, stringResource(R.string.settings_page_wear_os), stringResource(R.string.settings_hub_wear_os_description), Icons.Rounded.Watch)
    } else {
        null
    },
    HubCard(ProfileSettingsDestination.Privacy, stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_hub_privacy_description), Icons.Rounded.PrivacyTip),
    HubCard(ProfileSettingsDestination.About, stringResource(R.string.settings_category_about), stringResource(R.string.settings_hub_about_description), Icons.Rounded.Info),
)

/** Signed out: the sign-in prompt. Signed in: the sync state, then who is signed in. Unknown: the general description. */
@Composable
private fun hubSyncDescription(account: SettingsHubAccount?): String = when {
    account == null -> stringResource(R.string.settings_hub_sync_description)
    account.email == null -> resolveCloudSyncSubtitle(null).asString()
    else -> stringResource(R.string.settings_hub_sync_signed_in, stringResource(resolveSyncBadgeText(account.syncStatus)), resolveCloudSyncSubtitle(account.email).asString())
}

/** From this content width (outside the tablet split) hub cards sit two to a row. */
internal const val SETTINGS_HUB_TWO_COLUMN_DP = 600f

/** Test tags on the hub's cards: this, then the destination's name. */
internal const val HUB_CARD_TAG_PREFIX = "settings_hub_card_"

/** Test tag on the group holding Check for updates. */
internal const val SETTINGS_HUB_UPDATES_TAG = "settings_hub_updates"
