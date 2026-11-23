package cx.aswin.boxlore.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.LocalRailFocus
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.TvRoutes
import cx.aswin.boxlore.tv.ui.components.ListTop
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.RevealOnFocus
import cx.aswin.boxlore.tv.ui.leftOpens
import cx.aswin.boxlore.tv.ui.library.LibraryTab
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/**
 * Home, as the canvas draws it: the Resume card, Jump back in as a 2 × 2 grid, Your shows in rows
 * of six, then Daily Mix with its episodes. Opens with the remote on Resume.
 */
@Composable
fun HomeScreen(container: TvContainer, go: TvNavigator) {
    val library = container.library
    val playback = container.playback
    // Only which episode is in the player: the card reads the ticks itself, so Home is not redrawn every second.
    val now = playback.nowPlaying.collectAsState()
    val playing by remember { derivedStateOf { now.value?.takeIf { it.loaded }?.episode } }
    val jumpBackIn by library.jumpBackIn.collectAsState()
    val shows by library.shows.collectAsState()
    val mix by library.dailyMix.collectAsState()
    val fresh by library.newEpisodes.collectAsState()
    val resume = playing ?: jumpBackIn.firstOrNull()
    val firstFocus = remember { FocusRequester() }
    var firstHasFocus by remember { mutableStateOf(false) }
    // Only the first visit puts the remote on Resume; coming back to Home returns it where it was.
    var placedFirstFocus by rememberSaveable { mutableStateOf(false) }
    val first = Modifier.focusRequester(firstFocus).onFocusChanged { firstHasFocus = it.isFocused }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        // Coming back from the rail returns to the row the remote left; each row returns to its item.
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(TvSpace.sectionGap),
    ) {
        if (resume != null) {
            item(key = "resume") {
                // Anywhere on the top card, Home scrolls right to the top, so the card is never left half cut off.
                ListTop(listState) {
                    NowPlayingCard(resume, playback, first, onOpenPlayer = { go.player() }, onOpenEpisode = { go.episode(resume.id) })
                }
            }
        } else {
            item(key = "first-show") { ListTop(listState) { FirstShowCard(first, onSearch = { go.tab(TvRoutes.SEARCH) }) } }
        }
        val play = { episode: TvEpisode ->
            playback.play(episode.id)
            go.player()
        }
        val rest = jumpBackIn.filter { it.id != resume?.id }
        if (rest.isNotEmpty()) {
            item(key = "jump-back-in") { RevealOnFocus { EpisodeRowSection(stringResource(R.string.tv_jump_back_in), rest, onPlay = play, onOpen = { go.episode(it.id) }) } }
        }
        if (fresh.isNotEmpty()) {
            item(key = "new-episodes") {
                RevealOnFocus {
                    EpisodeRowSection(stringResource(R.string.tv_new_episodes), fresh, onPlay = play, onOpen = { go.episode(it.id) }) {
                        SeeAllButton(stringResource(R.string.tv_see_all)) { go.library(LibraryTab.NewEpisodes) }
                    }
                }
            }
        }
        if (shows.isNotEmpty()) {
            item(key = "your-shows") {
                RevealOnFocus { YourShowsSection(shows, onShow = { go.show(it.id) }, onAllShows = { go.library(LibraryTab.YourShows) }) }
            }
        }
        if (mix.isNotEmpty()) {
            item(key = "daily-mix") {
                RevealOnFocus {
                    DailyMixSection(mix, onPlayAll = {
                        playback.playDailyMix()
                        go.player()
                    }, onPlay = play, onOpen = { go.episode(it.id) })
                }
            }
        }
        chartItems(library, go)
    }
    LaunchedEffect(Unit) {
        if (placedFirstFocus) return@LaunchedEffect
        // The first card can arrive a frame after the list, and the TV may hand focus to whatever it
        // finds first; keep asking until Resume itself has it.
        repeat(FOCUS_ATTEMPTS) {
            if (firstHasFocus) {
                placedFirstFocus = true
                return@LaunchedEffect
            }
            runCatching { firstFocus.requestFocus() }
            withFrameNanos { }
        }
    }
}

private const val FOCUS_ATTEMPTS = 30

/** What Home shows before the listener follows anything. */
@Composable
private fun FirstShowCard(first: Modifier, onSearch: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(TvColors.tertiaryContainer).padding(30.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.tv_first_show_title), style = MaterialTheme.typography.headlineMedium, color = TvColors.onTertiaryContainer)
            Text(stringResource(R.string.tv_first_show_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.onTertiaryContainer)
            PillButton(
                label = stringResource(R.string.tv_nav_search),
                onClick = onSearch,
                icon = Icons.Rounded.Search,
                modifier = Modifier.padding(top = 6.dp).then(first).leftOpens(LocalRailFocus.current),
            )
        }
    }
}
