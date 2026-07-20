package cx.aswin.boxlore.feature.library.history

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.feature.library.HistoryUiEvent
import cx.aswin.boxlore.feature.library.HistoryViewModel
import cx.aswin.boxlore.feature.library.R

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

    LaunchedEffect(Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHistoryViewed("library_hub_card")
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
