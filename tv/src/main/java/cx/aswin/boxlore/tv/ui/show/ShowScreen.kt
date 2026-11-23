package cx.aswin.boxlore.tv.ui.show

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.data.ShowEpisodes
import cx.aswin.boxlore.tv.model.TvShow
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.EpisodeRow
import cx.aswin.boxlore.tv.ui.components.ListTop
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.entersOn
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/** A show's page: artwork, Subscribe and sort, then its episodes newest first. Opens on Subscribe. */
@Composable
fun ShowScreen(showId: String, container: TvContainer, go: TvNavigator) {
    val library = container.library
    val show by remember(showId) { library.show(showId) }.collectAsState(initial = null)
    val listing by remember(showId) { library.episodes(showId) }.collectAsState(initial = ShowEpisodes.Loading)
    val episodes = (listing as? ShowEpisodes.Loaded)?.episodes.orEmpty()
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val ordered = if (newestFirst) episodes else episodes.reversed()
    val subscribeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val current = show
    if (current == null) {
        // A show the API cannot describe: say so rather than leave the page blank.
        if (listing == ShowEpisodes.Failed) ListNote(stringResource(R.string.tv_show_failed), Modifier.padding(start = TvSpace.contentStart, top = TvSpace.safeTop))
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            ListTop(listState) {
            ShowHeader(
                show = current,
                newestFirst = newestFirst,
                subscribeFocus = subscribeFocus,
                onSubscribe = { library.setSubscribed(current.id, !current.subscribed) },
                onSort = { newestFirst = !newestFirst },
            )
            }
        }
        item(key = "episodes-title") {
            Text(
                stringResource(R.string.tv_episodes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
        }
        items(ordered, key = { it.id }) { episode ->
            EpisodeRow(episode, onPlay = {
                container.playback.play(episode.id)
                go.player()
            }, onOpen = { go.episode(episode.id) }, withShow = false)
        }
        when (listing) {
            ShowEpisodes.Loading -> item(key = "loading") { ListNote(stringResource(R.string.tv_episodes_loading)) }
            ShowEpisodes.Failed -> item(key = "failed") { ListNote(stringResource(R.string.tv_episodes_failed)) }
            is ShowEpisodes.Loaded -> if (episodes.isEmpty()) item(key = "none") { ListNote(stringResource(R.string.tv_episodes_none)) }
        }
    }
    LaunchedEffect(current.id) { runCatching { subscribeFocus.requestFocus() } }
}

/** A quiet line in place of the episode list: loading, nothing found, or no connection. */
@Composable
private fun ListNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant, modifier = modifier.padding(vertical = 12.dp))
}

@Composable
private fun ShowHeader(show: TvShow, newestFirst: Boolean, subscribeFocus: FocusRequester, onSubscribe: () -> Unit, onSort: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Artwork(show.artworkUrl, Modifier.size(168.dp), RoundedCornerShape(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(show.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val byline = (listOf(show.publisher) + show.genres.joinToString(", ").ifEmpty { null }).filterNotNull().joinToString(" · ")
            Text(byline, style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (show.description.isNotEmpty()) {
                Text(
                    show.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            Row(Modifier.padding(top = 10.dp).entersOn(subscribeFocus), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    label = stringResource(if (show.subscribed) R.string.tv_subscribed else R.string.tv_subscribe),
                    onClick = onSubscribe,
                    icon = if (show.subscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                    modifier = Modifier.focusRequester(subscribeFocus),
                )
                PillButton(
                    label = stringResource(if (newestFirst) R.string.tv_newest_first else R.string.tv_oldest_first),
                    onClick = onSort,
                    icon = Icons.AutoMirrored.Rounded.Sort,
                )
            }
        }
    }
}
