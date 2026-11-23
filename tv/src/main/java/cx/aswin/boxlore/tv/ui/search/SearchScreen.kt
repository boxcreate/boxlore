package cx.aswin.boxlore.tv.ui.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.model.TvSearchResult
import cx.aswin.boxlore.tv.model.TvShow
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.ContentSurface
import cx.aswin.boxlore.tv.ui.components.RoundButton
import cx.aswin.boxlore.tv.ui.components.RowPadding
import cx.aswin.boxlore.tv.ui.components.SectionTitle
import cx.aswin.boxlore.tv.ui.components.TvTextField
import cx.aswin.boxlore.tv.ui.components.bleedRow
import cx.aswin.boxlore.tv.ui.show.EpisodeRow
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 400L

/**
 * Search, the same search the phone and watch use. Speaking is quicker than typing with a remote,
 * so the mic gets the focus first; OK on the field opens the TV's own keyboard.
 */
@Composable
fun SearchScreen(container: TvContainer, go: TvNavigator) {
    val library = container.library
    val trending by library.trending.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<TvSearchResult?>(null) }
    val micFocus = remember { FocusRequester() }
    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { answer ->
            if (answer.resultCode == Activity.RESULT_OK) {
                answer.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
            }
        }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = null
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MS)
        result = library.search(query)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "field") {
            SearchBar(query, onQuery = { query = it }, micFocus = micFocus, onMic = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                try {
                    voice.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    // A TV without voice input keeps the keyboard.
                }
            })
        }
        val found = result as? TvSearchResult.Found
        when {
            found == null -> item(key = "trending") { ShowRow(stringResource(R.string.tv_trending), trending, onShow = { go.show(it.id) }) }
            found.shows.isEmpty() && found.episodes.isEmpty() -> item(key = "none") {
                Text(stringResource(R.string.tv_no_results, found.query), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
            }
            else -> {
                if (found.shows.isNotEmpty()) item(key = "shows") { ShowRow(stringResource(R.string.tv_shows), found.shows, onShow = { go.show(it.id) }) }
                if (found.episodes.isNotEmpty()) {
                    item(key = "episodes-title") { SectionTitle(stringResource(R.string.tv_episodes)) }
                    items(found.episodes, key = { it.id }) { episode ->
                        EpisodeRow(episode, onPlay = {
                            container.playback.play(episode.id)
                            go.player()
                        }, onOpen = { go.episode(episode.id) })
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { micFocus.requestFocus() } }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, micFocus: FocusRequester, onMic: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = stringResource(R.string.tv_search_hint),
                icon = Icons.Rounded.Search,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            RoundButton(Icons.Rounded.Mic, stringResource(R.string.tv_search_voice), onMic, size = 52.dp, modifier = Modifier.focusRequester(micFocus))
        }
        if (query.isEmpty()) {
            Text(stringResource(R.string.tv_search_help), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp))
        }
    }
}

/** A row of show covers with their names; OK opens the show. */
@Composable
fun ShowRow(title: String, shows: List<TvShow>, onShow: (TvShow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(title)
        // Coming into the row lands on its first show, not the one nearest the search box's middle;
        // coming back lands where the remote left it.
        val first = remember { FocusRequester() }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = RowPadding, modifier = Modifier.bleedRow().focusRestorer(first)) {
            itemsIndexed(shows, key = { _, show -> show.id }) { index, show ->
                ShowTile(show, onClick = { onShow(show) }, modifier = if (index == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
}

/** A square cover with the show's name under it. */
@Composable
fun ShowTile(show: TvShow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentSurface(onClick = onClick, modifier = Modifier.size(112.dp), shape = RoundedCornerShape(16.dp)) {
            Artwork(show.artworkUrl, Modifier.fillMaxSize(), RoundedCornerShape(16.dp))
        }
        Text(show.title, style = MaterialTheme.typography.labelMedium, color = TvColors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
