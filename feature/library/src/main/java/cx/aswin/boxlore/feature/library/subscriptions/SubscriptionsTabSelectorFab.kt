package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcherMinimumHeight

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
            DiscoveryTab("Shows", Icons.Rounded.GridView),
            DiscoveryTab("New Eps", Icons.Rounded.AutoAwesome, badgeCount),
        ),
        selectedIndex = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}
