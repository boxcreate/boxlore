package cx.aswin.boxlore.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.LocalRailFocus
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.TvRoutes
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.ProgressBar
import cx.aswin.boxlore.tv.ui.components.RoundButton
import cx.aswin.boxlore.tv.ui.leftOpens
import cx.aswin.boxlore.tv.ui.library.LibraryTab
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace
import cx.aswin.boxlore.tv.ui.timeLeftText

/** The Resume card's fill: boxlore's iris, deepened so white text reads on it. */
private val ResumeCardColor = Color(0xFF2B2370)
private val ResumeCardOnColor = Color(0xFFECE7FF)
private val ResumeCardSoftColor = Color(0xFFCFC7FF)
private val ResumeCardButtonColor = Color(0xFF463D8F)
private val ResumeCardHeight = 210.dp

/**
 * Home, as the canvas draws it: the Resume card, Jump back in as a 2 × 2 grid, Your shows in rows
 * of six, then Daily Mix with its episodes. Opens with the remote on Resume.
 */
@Composable
fun HomeScreen(container: TvContainer, go: TvNavigator) {
    val library = container.library
    val playback = container.playback
    val nowPlaying by playback.nowPlaying.collectAsState()
    val jumpBackIn by library.jumpBackIn.collectAsState()
    val shows by library.shows.collectAsState()
    val mix by library.dailyMix.collectAsState()
    val fresh by library.newEpisodes.collectAsState()
    val resume = nowPlaying?.episode ?: jumpBackIn.firstOrNull()
    val firstFocus = remember { FocusRequester() }
    var firstHasFocus by remember { mutableStateOf(false) }
    // Only the first visit puts the remote on Resume; coming back to Home returns it where it was.
    var placedFirstFocus by rememberSaveable { mutableStateOf(false) }
    val first = Modifier.focusRequester(firstFocus).onFocusChanged { firstHasFocus = it.isFocused }

    LazyColumn(
        // Coming back from the rail returns to the row the remote left; each row returns to its item.
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(TvSpace.sectionGap),
    ) {
        if (resume != null) {
            item(key = "resume") {
                ResumeCard(
                    episode = resume,
                    minutesLeft = nowPlaying?.minutesLeft ?: resume.minutesLeft ?: resume.durationMinutes,
                    progress = nowPlaying?.progress ?: resume.progress,
                    first = first,
                    onResume = {
                        if (nowPlaying?.episode?.id != resume.id) {
                            playback.play(resume.id)
                        } else if (nowPlaying?.isPlaying != true) {
                            playback.togglePlayPause()
                        }
                        go.player()
                    },
                    onOpen = { go.episode(resume.id) },
                )
            }
        } else {
            item(key = "first-show") { FirstShowCard(first, onSearch = { go.tab(TvRoutes.SEARCH) }) }
        }
        val play = { episode: TvEpisode ->
            playback.play(episode.id)
            go.player()
        }
        val rest = jumpBackIn.filter { it.id != resume?.id }
        if (rest.isNotEmpty()) {
            item(key = "jump-back-in") { EpisodeRowSection(stringResource(R.string.tv_jump_back_in), rest, onPlay = play, onOpen = { go.episode(it.id) }) }
        }
        if (fresh.isNotEmpty()) {
            item(key = "new-episodes") {
                EpisodeRowSection(stringResource(R.string.tv_new_episodes), fresh, onPlay = play, onOpen = { go.episode(it.id) }) {
                    SeeAllButton(stringResource(R.string.tv_see_all)) { go.library(LibraryTab.NewEpisodes) }
                }
            }
        }
        if (shows.isNotEmpty()) {
            item(key = "your-shows") {
                YourShowsSection(shows, onShow = { go.show(it.id) }, onAllShows = { go.library(LibraryTab.YourShows) })
            }
        }
        if (mix.isNotEmpty()) {
            item(key = "daily-mix") {
                DailyMixSection(mix, onPlayAll = {
                    playback.playDailyMix()
                    go.player()
                }, onPlay = play, onOpen = { go.episode(it.id) })
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

@Composable
private fun ResumeCard(
    episode: TvEpisode,
    minutesLeft: Int,
    progress: Float,
    first: Modifier,
    onResume: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    // At least as tall as the artwork, taller when a long title needs it, so the buttons always keep their room.
    Row(Modifier.fillMaxWidth().heightIn(min = ResumeCardHeight).height(IntrinsicSize.Min).clip(shape).background(ResumeCardColor)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 30.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.tv_resume_eyebrow, timeLeftText(minutesLeft)), style = MaterialTheme.typography.bodySmall, color = ResumeCardSoftColor)
            Text(
                episode.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.2f),
                color = ResumeCardOnColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(episode.showTitle, style = MaterialTheme.typography.bodyLarge, color = ResumeCardSoftColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressBar(progress, Modifier.width(300.dp).padding(top = 4.dp), track = ResumeCardButtonColor, fill = ResumeCardOnColor)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    label = stringResource(R.string.tv_resume),
                    onClick = onResume,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = first.leftOpens(LocalRailFocus.current),
                    restingColor = ResumeCardButtonColor,
                    restingContentColor = ResumeCardOnColor,
                )
                RoundButton(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.tv_open_episode), onOpen, restingColor = ResumeCardButtonColor)
            }
        }
        // The card's own clip rounds the art's outer corners; a plain inner edge avoids a second, path-shaped clip.
        Artwork(episode.artworkUrl, Modifier.width(ResumeCardHeight).fillMaxHeight(), RectangleShape)
    }
}

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
