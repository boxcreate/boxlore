package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import cx.aswin.boxlore.feature.info.logic.FeedItem

/**
 * Whether the jump pill's target row is on screen or above it, so the pill is not needed.
 * [targetJumpIndex] is the row's index among [feedItems], or -1 when there is none; the list puts two rows before them.
 */
@Composable
internal fun rememberJumpTargetVisible(listState: LazyListState, feedItems: List<FeedItem>, targetJumpIndex: Int): State<Boolean> =
    remember(feedItems, listState, targetJumpIndex) {
        derivedStateOf {
            if (targetJumpIndex == -1 || feedItems.isEmpty()) {
                true
            } else {
                val listIndex = targetJumpIndex + 2
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    true
                } else {
                    val firstVisible = visibleItems.firstOrNull()?.index ?: 0
                    val lastVisible = visibleItems.lastOrNull()?.index ?: 0
                    listIndex in firstVisible..lastVisible || firstVisible >= listIndex
                }
            }
        }
    }

/** False while the list scrolls down and true again once it scrolls up, so the jump pill gets out of the way. */
@Composable
internal fun rememberListScrollingUp(listState: LazyListState): State<Boolean> {
    val scrollingUp = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex > lastIndex) {
                    scrollingUp.value = false
                } else if (currentIndex < lastIndex) {
                    scrollingUp.value = true
                } else if (currentOffset > lastOffset) {
                    scrollingUp.value = false
                } else if (currentOffset < lastOffset) {
                    scrollingUp.value = true
                }
                lastIndex = currentIndex
                lastOffset = currentOffset
            }
    }
    return scrollingUp
}
