package cx.aswin.boxlore.feature.library.subscriptions

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
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
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

internal fun showsSortLabel(sort: SubscriptionSort): String = when (sort) {
    SubscriptionSort.SmartRank -> "Smart"
    SubscriptionSort.RecentlyUpdated -> "Updated"
    SubscriptionSort.Alphabetical -> "A–Z"
    SubscriptionSort.MostListened -> "Listened"
    SubscriptionSort.Manual -> "Manual"
}

internal fun latestSortLabel(useSmartRank: Boolean): String = if (useSmartRank) "Smart" else "Chronological"

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
        label = "Smart Sort",
        selected = currentSort == SubscriptionSort.SmartRank,
        onClick = {
            onSortChange(SubscriptionSort.SmartRank)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Recently Updated",
        selected = currentSort == SubscriptionSort.RecentlyUpdated,
        onClick = {
            onSortChange(SubscriptionSort.RecentlyUpdated)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "A-Z",
        selected = currentSort == SubscriptionSort.Alphabetical,
        onClick = {
            onSortChange(SubscriptionSort.Alphabetical)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Most Listened",
        selected = currentSort == SubscriptionSort.MostListened,
        onClick = {
            onSortChange(SubscriptionSort.MostListened)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Manual",
        selected = currentSort == SubscriptionSort.Manual,
        onClick = {
            onSortChange(SubscriptionSort.Manual)
            onDismiss()
        }
    )
    if (onAutoOrganizeFoldersChange != null) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Auto-organize into folders") },
            onClick = {
                onAutoOrganizeFoldersChange(!autoOrganizeFolders)
                onDismiss()
            },
            trailingIcon = {
                if (autoOrganizeFolders) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected")
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
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
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
        text = { Text("Smart Sort") },
        onClick = {
            onUseSmartRankChange(true)
            onDismiss()
        },
        trailingIcon = {
            if (useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
    DropdownMenuItem(
        text = { Text("Chronological") },
        onClick = {
            onUseSmartRankChange(false)
            onDismiss()
        },
        trailingIcon = {
            if (!useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
    DropdownMenuItem(
        text = { Text("Hide played episodes") },
        onClick = {
            onHideCompletedChange(!hideCompleted)
            onDismiss()
        },
        trailingIcon = {
            if (hideCompleted) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
}
