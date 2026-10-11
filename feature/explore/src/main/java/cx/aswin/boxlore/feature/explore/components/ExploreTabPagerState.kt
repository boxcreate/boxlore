package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import cx.aswin.boxlore.feature.explore.logic.ExploreTabPagerLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Stable
internal class ExploreTabPagerState(val pager: PagerState, private val scope: CoroutineScope) {
    val visibleTab: Int get() = ExploreTabPagerLogic.tabForPage(pager.currentPage)

    fun selectTab(tab: Int) {
        scope.launch { pager.animateScrollToPage(ExploreTabPagerLogic.pageForTab(tab)) }
    }
}

/** Gestures and tab taps settle through the existing page-owned selection callback. */
@Composable
internal fun rememberExploreTabPagerState(
    selectedTab: Int,
    enabled: Boolean,
    onTabSelected: (Int) -> Unit,
): ExploreTabPagerState {
    val pager = rememberPagerState(initialPage = ExploreTabPagerLogic.pageForTab(selectedTab)) {
        ExploreTabPagerLogic.PAGE_COUNT
    }
    val scope = rememberCoroutineScope()
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    // Keep restored/external tab selections authoritative without replaying callbacks.
    LaunchedEffect(selectedTab, enabled) {
        val target = ExploreTabPagerLogic.pageForTab(selectedTab)
        if (pager.settledPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager, enabled) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            ExploreTabPagerLogic.selectionAfterSettle(page, latestSelectedTab, enabled)?.let(latestOnTabSelected)
        }
    }
    return remember(pager, scope) { ExploreTabPagerState(pager, scope) }
}
