package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import cx.aswin.boxlore.feature.library.R

internal data class HistoryTopBarState(
    val showOverflowAction: Boolean,
    val showOverflow: Boolean,
    val titleStyle: TextStyle,
)

internal data class HistoryTopBarCallbacks(
    val onBack: () -> Unit,
    val onShowOverflow: () -> Unit,
    val onDismissOverflow: () -> Unit,
    val onRequestClearAll: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryTopBar(
    state: HistoryTopBarState,
    callbacks: HistoryTopBarCallbacks,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.history_title),
                style = state.titleStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.history_back),
                )
            }
        },
        actions = {
            if (state.showOverflowAction) {
                Box {
                    IconButton(onClick = callbacks.onShowOverflow) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.history_clear_all),
                        )
                    }
                    DropdownMenu(
                        expanded = state.showOverflow,
                        onDismissRequest = callbacks.onDismissOverflow,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_clear_all)) },
                            onClick = {
                                callbacks.onDismissOverflow()
                                callbacks.onRequestClearAll()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.ClearAll, contentDescription = null)
                            },
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
