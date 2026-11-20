package cx.aswin.boxlore.feature.library.subscriptions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.SubscriptionSort

@Composable
internal fun ExpressiveTabSwitcher(
    tabs: List<String>,
    selectedIndex: Int,
    badge: Map<Int, Int> = emptyMap(),
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    DiscoveryTabSwitcher(
        tabs = tabs.mapIndexed { index, label ->
            DiscoveryTab(label, if (index == 0) Icons.Rounded.GridView else Icons.Rounded.AutoAwesome, badge[index] ?: 0)
        },
        selectedIndex = selectedIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        floating = false,
    )
}

@StringRes
internal fun showsSortLabel(sort: SubscriptionSort): Int = when (sort) {
    SubscriptionSort.SmartRank -> R.string.library_sort_smart
    SubscriptionSort.RecentlyUpdated -> R.string.library_sort_updated_short
    SubscriptionSort.Alphabetical -> R.string.library_sort_a_to_z
    SubscriptionSort.MostListened -> R.string.library_sort_listened_short
    SubscriptionSort.Manual -> R.string.library_sort_manual
}

@StringRes
internal fun latestSortLabel(useSmartRank: Boolean): Int =
    if (useSmartRank) R.string.library_sort_smart else R.string.library_sort_chronological

/**
 * Explore-style genre pills only (icons + short labels). Sort / hide-played live in the top bar.
 */
@Composable
internal fun SubscriptionGenreChips(
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    distinctGenres: List<String>,
    podcasts: List<cx.aswin.boxlore.core.model.Podcast> = emptyList(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    onNewFolderClick: (() -> Unit)? = null,
) {
    SubscriptionsFilterRow(
        selectedGenre = selectedGenre,
        onGenreChange = onGenreChange,
        distinctGenres = distinctGenres,
        podcasts = podcasts,
        modifier = modifier,
        contentPadding = contentPadding,
        onNewFolderClick = onNewFolderClick,
    )
}

@Composable
internal fun ShowsSortMenuItems(
    currentSort: SubscriptionSort,
    onSortChange: (SubscriptionSort) -> Unit,
    autoOrganizeFolders: Boolean = false,
    onAutoOrganizeFoldersChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ShowsSortOption(
        label = stringResource(R.string.library_sort_smart_sort),
        selected = currentSort == SubscriptionSort.SmartRank,
        onClick = {
            onSortChange(SubscriptionSort.SmartRank)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = stringResource(R.string.library_sort_recently_updated_menu),
        selected = currentSort == SubscriptionSort.RecentlyUpdated,
        onClick = {
            onSortChange(SubscriptionSort.RecentlyUpdated)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = stringResource(R.string.library_sort_a_to_z_menu),
        selected = currentSort == SubscriptionSort.Alphabetical,
        onClick = {
            onSortChange(SubscriptionSort.Alphabetical)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = stringResource(R.string.library_sort_most_listened_menu),
        selected = currentSort == SubscriptionSort.MostListened,
        onClick = {
            onSortChange(SubscriptionSort.MostListened)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = stringResource(R.string.library_sort_manual),
        selected = currentSort == SubscriptionSort.Manual,
        onClick = {
            onSortChange(SubscriptionSort.Manual)
            onDismiss()
        }
    )
    if (onAutoOrganizeFoldersChange != null) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_sort_auto_organize_into_folders)) },
            onClick = {
                onAutoOrganizeFoldersChange(!autoOrganizeFolders)
                onDismiss()
            },
            trailingIcon = {
                if (autoOrganizeFolders) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.library_selected))
                }
            }
        )
    }
}

@Composable
private fun ShowsSortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.library_selected))
            }
        }
    )
}

@Composable
internal fun LatestSortMenuItems(
    useSmartRank: Boolean,
    onUseSmartRankChange: (Boolean) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.library_sort_smart_sort)) },
        onClick = {
            onUseSmartRankChange(true)
            onDismiss()
        },
        trailingIcon = {
            if (useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.library_selected))
            }
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.library_sort_chronological)) },
        onClick = {
            onUseSmartRankChange(false)
            onDismiss()
        },
        trailingIcon = {
            if (!useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.library_selected))
            }
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.library_hide_played_episodes)) },
        onClick = {
            onHideCompletedChange(!hideCompleted)
            onDismiss()
        },
        trailingIcon = {
            if (hideCompleted) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.library_selected))
            }
        }
    )
}
