package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.feature.explore.R
import cx.aswin.boxlore.feature.explore.SearchTab

@Composable
fun ExploreTabSelectorFab(selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    DiscoveryTabSwitcher(
        tabs = listOf(
            DiscoveryTab(stringResource(R.string.explore_for_you), Icons.Rounded.AutoAwesome),
            DiscoveryTab(stringResource(R.string.explore_top), Icons.AutoMirrored.Rounded.TrendingUp),
        ),
        selectedIndex = if (selectedTab == 1) 0 else 1,
        onTabSelected = { onTabSelected(if (it == 0) 1 else 0) },
        modifier = modifier,
    )
}

@Composable
fun SearchTabSelector(selectedTab: SearchTab, onTabSelected: (SearchTab) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        DiscoveryTabSwitcher(
            tabs = listOf(
                DiscoveryTab(stringResource(R.string.explore_find_show), Icons.Rounded.Search),
                DiscoveryTab(stringResource(R.string.explore_search_episodes), Icons.Rounded.AutoAwesome),
            ),
            selectedIndex = if (selectedTab == SearchTab.SHOWS) 0 else 1,
            onTabSelected = { onTabSelected(if (it == 0) SearchTab.SHOWS else SearchTab.EPISODES) },
            floating = false,
            modifier = Modifier.testTag("explore_search_switcher"),
        )
    }
}
