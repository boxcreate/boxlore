package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcherMinimumHeight
import cx.aswin.boxlore.feature.library.R

val SubscriptionsTabSelectorFabHeight = DiscoveryTabSwitcherMinimumHeight

@Composable
fun SubscriptionsTabSelectorFab(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    DiscoveryTabSwitcher(
        tabs = listOf(
            DiscoveryTab(stringResource(R.string.library_tab_shows), Icons.Rounded.GridView),
            DiscoveryTab(stringResource(R.string.library_tab_new_episodes_short), Icons.Rounded.AutoAwesome, badgeCount),
        ),
        selectedIndex = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}
