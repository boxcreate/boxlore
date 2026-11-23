package cx.aswin.boxlore.tv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.components.EpisodeRow
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.TvTextField
import cx.aswin.boxlore.tv.ui.search.ShowTile
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/** The three pages of Library, in the phone's order, minus Downloads (the TV streams). */
enum class LibraryTab(val label: Int) { YourShows(R.string.tv_your_shows), NewEpisodes(R.string.tv_new_episodes), Liked(R.string.tv_liked_episodes), History(R.string.tv_listening_history) }

private const val COLUMNS = 6

/** Library: every subscription, six to a row, with search and the phone's sort; Liked; History. */
@Composable
fun LibraryScreen(container: TvContainer, go: TvNavigator, openOn: LibraryTab? = null) {
    var tab by rememberSaveable { mutableStateOf(openOn ?: LibraryTab.YourShows) }
    Column(
        Modifier.fillMaxSize().padding(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tv_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(TvColors.surface).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryTab.entries.forEach { entry ->
                    PillButton(
                        label = stringResource(entry.label),
                        onClick = { tab = entry },
                        restingColor = if (entry == tab) TvColors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        restingContentColor = if (entry == tab) TvColors.onPrimaryContainer else TvColors.onSurfaceVariant,
                    )
                }
            }
        }
        when (tab) {
            LibraryTab.YourShows -> YourShowsPage(container, go)
            LibraryTab.NewEpisodes -> EpisodeList(container.library.newEpisodes.collectAsState().value, R.string.tv_no_new_title, R.string.tv_no_new_body, container, go)
            LibraryTab.Liked -> EpisodeList(container.library.liked.collectAsState().value, R.string.tv_no_liked_title, R.string.tv_no_liked_body, container, go)
            LibraryTab.History -> EpisodeList(container.library.history.collectAsState().value, R.string.tv_no_history_title, R.string.tv_no_history_body, container, go)
        }
    }
}

@Composable
private fun YourShowsPage(container: TvContainer, go: TvNavigator) {
    var sort by rememberSaveable { mutableStateOf(ShowSort.Smart) }
    var filter by rememberSaveable { mutableStateOf("") }
    val shows by remember(sort) { container.library.sortedShows(sort) }.collectAsState(initial = emptyList())
    val shown = if (filter.isBlank()) shows else shows.filter { it.title.contains(filter.trim(), ignoreCase = true) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = TvSpace.safeTop),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(COLUMNS) }, key = "tools") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.tv_show_count, shows.size, shows.size), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant)
                FilterField(filter, onFilter = { filter = it }, modifier = Modifier.weight(1f))
                PillButton(stringResource(sortLabel(sort)), onClick = { sort = ShowSort.entries[(sort.ordinal + 1) % ShowSort.entries.size] }, icon = Icons.AutoMirrored.Rounded.Sort)
            }
        }
        items(shown, key = { it.id }) { show -> ShowTile(show, onClick = { go.show(show.id) }) }
    }
}

private fun sortLabel(sort: ShowSort): Int = when (sort) {
    ShowSort.Smart -> R.string.tv_sort_smart
    ShowSort.RecentlyUpdated -> R.string.tv_sort_recently_updated
    ShowSort.AToZ -> R.string.tv_sort_a_to_z
    ShowSort.MostListened -> R.string.tv_sort_most_listened
    ShowSort.Manual -> R.string.tv_sort_manual
}

@Composable
private fun FilterField(value: String, onFilter: (String) -> Unit, modifier: Modifier = Modifier) {
    TvTextField(
        value = value,
        onValueChange = onFilter,
        placeholder = stringResource(R.string.tv_search_your_shows),
        icon = Icons.Rounded.Search,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        height = 44.dp,
    )
}

@Composable
private fun EpisodeList(episodes: List<TvEpisode>, emptyTitle: Int, emptyBody: Int, container: TvContainer, go: TvNavigator) {
    if (episodes.isEmpty()) {
        Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(emptyTitle), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(emptyBody), style = MaterialTheme.typography.bodyLarge, color = TvColors.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = TvSpace.safeTop), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(episode, onPlay = {
                container.playback.play(episode.id)
                go.player()
            }, onOpen = { go.episode(episode.id) })
        }
    }
}
