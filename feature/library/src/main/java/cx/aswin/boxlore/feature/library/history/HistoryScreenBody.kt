package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.library.HistoryUiState
import cx.aswin.boxlore.feature.library.HistoryViewModel

@Composable
internal fun HistoryScreenBody(
    uiState: HistoryUiState,
    contentPadding: PaddingValues,
    viewModel: HistoryViewModel,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onExploreClick: () -> Unit,
    onPickOlderDate: () -> Unit,
) {
    when (uiState) {
        HistoryUiState.Loading -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        HistoryUiState.Empty -> {
            HistoryEmptyState(
                onExploreClick = onExploreClick,
                modifier = Modifier.padding(contentPadding),
            )
        }
        is HistoryUiState.Success -> {
            HistorySuccessList(
                success = uiState.state,
                viewModel = viewModel,
                contentPadding = contentPadding,
                onEpisodeClick = onEpisodeClick,
                onPickOlderDate = onPickOlderDate,
            )
        }
    }
}
