package cx.aswin.boxlore.feature.history

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.history.HistoryUiEvent
import cx.aswin.boxlore.feature.history.HistoryViewModel
import cx.aswin.boxlore.feature.history.R
import cx.aswin.boxlore.feature.history.timeline.TimelineEntry
import cx.aswin.boxlore.feature.history.timeline.TimelineFilter
import cx.aswin.boxlore.feature.history.timeline.TimelineGrouping
import cx.aswin.boxlore.feature.history.timeline.timelineIndexFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

@Composable
internal fun HistoryScreenEffects(
    viewModel: HistoryViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val itemRemovedMessage = stringResource(R.string.history_item_removed)
    val undoLabel = stringResource(R.string.history_undo)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.trackScreenExit()
                    androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.onScreenResume()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryUiEvent.ShowUndoDelete -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = itemRemovedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemoval(event.removal)
                    }
                }
                HistoryUiEvent.HistoryCleared -> Unit
            }
        }
    }
}

/** Scrolls the Timeline for Jump to a day and All in progress, once the reset they asked for has applied. */
@Composable
internal fun TimelineScrollEffect(viewModel: HistoryViewModel, listState: LazyListState) {
    LaunchedEffect(viewModel) {
        followTimelineScrolls(viewModel.scrollRequests, viewModel.timelineState) { listState.animateScrollToItem(it) }
    }
}

/**
 * For each of [requests], waits for [timeline] to show the reset it asked for, then scrolls to its
 * index. A newer request cancels one still waiting: the listener may change the grouping or filter
 * before the reset arrives, and that state may never come.
 */
internal suspend fun followTimelineScrolls(
    requests: Flow<TimelineScrollTarget>,
    timeline: Flow<TimelineUiState>,
    scrollTo: suspend (index: Int) -> Unit,
) {
    requests.collectLatest { target ->
        // Wait for the reset (every episode, or the in-progress ones, by day) before working out where to go.
        val filter = if (target is TimelineScrollTarget.InProgress) TimelineFilter.IN_PROGRESS else TimelineFilter.ALL
        val ready =
            timeline.first {
                it is TimelineUiState.Ready && it.filter == filter && it.grouping == TimelineGrouping.DAYS && it.query.isEmpty()
            } as TimelineUiState.Ready
        val index =
            when (target) {
                is TimelineScrollTarget.Day -> timelineIndexFor(ready.entries, target.date)
                TimelineScrollTarget.InProgress -> ready.entries.indexOf(TimelineEntry.Chips).takeIf { it >= 0 }
            }
        index?.let { scrollTo(it) }
    }
}
