package cx.aswin.boxlore.wear.ui.search

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.ui.components.Artwork
import cx.aswin.boxlore.wear.ui.components.EmptyMessage
import cx.aswin.boxlore.wear.ui.components.RowDetail
import cx.aswin.boxlore.wear.ui.components.WatchRow

private const val TYPED_QUERY_KEY = "query"

/**
 * Search has no screen of its own: opening it hands over to the watch's input screen, which
 * already offers dictation and a keyboard. The words come back to [onQuery]; backing out of the
 * input screen calls [onCancel].
 */
@Composable
internal fun SearchScreen(onQuery: (String) -> Unit, onCancel: () -> Unit) {
    val label = stringResource(R.string.wear_search_title)
    var asked by rememberSaveable { mutableStateOf(false) }
    val input =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val query =
                result.data?.let { RemoteInput.getResultsFromIntent(it) }
                    ?.getCharSequence(TYPED_QUERY_KEY)?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
            if (query != null) onQuery(query) else onCancel()
        }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            input.launch(inputIntent(label))
        }
    }
    ScreenScaffold {}
}

private fun inputIntent(label: String): Intent {
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    val input = RemoteInput.Builder(TYPED_QUERY_KEY).setLabel(label).build()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
    return intent
}

/** Results for one query. [result] is null while the search is running. */
@Composable
internal fun SearchResultsScreen(
    query: String,
    result: WatchSearchResult?,
    onShow: (String) -> Unit,
    onDownloads: () -> Unit,
) {
    when (result) {
        null -> SearchLoading()
        is WatchSearchResult.NoConnection -> SearchNoConnection(onDownloads)
        is WatchSearchResult.Shows -> SearchShows(query, result.shows, onShow)
    }
}

@Composable
private fun SearchLoading() {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun SearchNoConnection(onDownloads: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onDownloads) {
                Icon(Icons.Rounded.DownloadDone, contentDescription = null)
                Text(stringResource(R.string.wear_downloads))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                EmptyMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.wear_no_connection),
                    body = stringResource(R.string.wear_no_connection_body),
                )
            }
        }
    }
}

@Composable
private fun SearchShows(query: String, shows: List<WatchShow>, onShow: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(text = stringResource(R.string.wear_search_query, query), maxLines = 1)
                }
            }
            if (shows.isEmpty()) {
                item {
                    EmptyMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.wear_search_no_results),
                        body = stringResource(R.string.wear_search_no_results_body),
                    )
                }
            }
            items(shows, key = { it.id }) { show ->
                WatchRow(
                    title = show.title,
                    onClick = { onShow(show.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    leading = { Artwork(url = show.artworkUrl, modifier = Modifier.size(34.dp)) },
                    secondary = {
                        if (show.subscribed) {
                            RowDetail(text = stringResource(R.string.wear_subscribed), icon = Icons.Rounded.Check)
                        } else {
                            RowDetail(text = show.publisher)
                        }
                    },
                )
            }
        }
    }
}
