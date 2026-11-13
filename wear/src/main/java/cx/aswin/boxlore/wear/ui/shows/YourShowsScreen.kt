package cx.aswin.boxlore.wear.ui.shows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.ShowsLayout
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.ui.components.Artwork
import cx.aswin.boxlore.wear.ui.components.EmptyMessage
import cx.aswin.boxlore.wear.ui.components.RowDetail
import cx.aswin.boxlore.wear.ui.components.WatchRow

@Composable
internal fun YourShowsScreen(
    layout: ShowsLayout,
    shows: List<WatchShow>,
    swipeToDismissState: SwipeToDismissBoxState,
    onShow: (String) -> Unit,
) {
    when {
        shows.isEmpty() -> ShowsEmpty()
        // The honeycomb moves sideways too, so only a swipe from the screen edge goes back.
        layout == ShowsLayout.Honeycomb ->
            ScreenScaffold(timeText = {}) {
                HoneycombShows(
                    shows = shows,
                    onShow = onShow,
                    modifier = Modifier.edgeSwipeToDismiss(swipeToDismissState),
                )
            }
        else -> ShowsList(shows, onShow)
    }
}

@Composable
private fun ShowsEmpty() {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyMessage(
                icon = Icons.Rounded.Podcasts,
                title = stringResource(R.string.wear_shows_empty),
                body = stringResource(R.string.wear_shows_empty_body),
            )
        }
    }
}

@Composable
private fun ShowsList(shows: List<WatchShow>, onShow: (String) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_your_shows))
                }
            }
            items(shows, key = { it.id }) { show ->
                WatchRow(
                    title = show.title,
                    onClick = { onShow(show.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    leading = { Artwork(url = show.artworkUrl, modifier = Modifier.size(34.dp)) },
                    secondary =
                        if (show.newEpisodes > 0) {
                            {
                                RowDetail(
                                    text = pluralStringResource(R.plurals.wear_new_episode_count, show.newEpisodes, show.newEpisodes),
                                    emphasised = true,
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
