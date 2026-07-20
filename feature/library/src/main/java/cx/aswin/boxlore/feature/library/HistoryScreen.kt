package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.library.history.HistoryScreenBody
import cx.aswin.boxlore.feature.library.history.HistoryScreenDialogs
import cx.aswin.boxlore.feature.library.history.HistoryScreenEffects
import cx.aswin.boxlore.feature.library.history.HistoryTopBar
import cx.aswin.boxlore.feature.library.history.HistoryTopBarCallbacks
import cx.aswin.boxlore.feature.library.history.HistoryTopBarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onExploreClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showTrackingNotice by viewModel.showTrackingNotice.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle =
        lerp(
            start = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            stop = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            fraction = scrollBehavior.state.collapsedFraction,
        )

    HistoryScreenEffects(viewModel, snackbarHostState)

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HistoryTopBar(
                state =
                    HistoryTopBarState(
                        showOverflowAction = uiState is HistoryUiState.Success,
                        showOverflow = showOverflow,
                        titleStyle = titleStyle,
                    ),
                callbacks =
                    HistoryTopBarCallbacks(
                        onBack = onBack,
                        onShowOverflow = { showOverflow = true },
                        onDismissOverflow = { showOverflow = false },
                        onRequestClearAll = { showClearDialog = true },
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        HistoryScreenBody(
            uiState = uiState,
            contentPadding = padding,
            viewModel = viewModel,
            onEpisodeClick = onEpisodeClick,
            onExploreClick = onExploreClick,
            onPickOlderDate = { showDatePicker = true },
        )
    }

    HistoryScreenDialogs(
        showTrackingNotice = showTrackingNotice,
        showClearDialog = showClearDialog,
        showDatePicker = showDatePicker,
        viewModel = viewModel,
        onDismissClearDialog = { showClearDialog = false },
        onDismissDatePicker = { showDatePicker = false },
    )
}
