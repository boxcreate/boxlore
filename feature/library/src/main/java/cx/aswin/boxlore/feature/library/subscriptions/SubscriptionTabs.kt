package cx.aswin.boxlore.feature.library.subscriptions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
