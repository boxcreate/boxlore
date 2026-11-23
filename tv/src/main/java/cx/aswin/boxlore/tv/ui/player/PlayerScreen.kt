package cx.aswin.boxlore.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.data.TvLibrary
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.PlaybackSpeeds
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.sleepTimerText

private val ArtSize = 320.dp
private val ArtShape = RoundedCornerShape(40.dp)

/**
 * The player, full screen with the rail hidden, in the cover's colours as on the phone. OK on Play
 * pauses and plays; the remote's play/pause, rewind and fast-forward keys work here and everywhere
 * else. Back closes an open panel first, then returns to the page underneath while audio keeps playing.
 */
@Composable
fun PlayerScreen(container: TvContainer, go: TvNavigator) {
    val playback = container.playback
    val library = container.library
    val nowPlaying by playback.nowPlaying.collectAsState()
    val queue by library.upNext.collectAsState()
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.None) }
    val playFocus = remember { FocusRequester() }
    // Closing a panel returns the remote to the button that opened it.
    val openers = remember { PlayerPanel.entries.associateWith { FocusRequester() } }
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    val now = nowPlaying ?: return
    val upNext = queue.filter { it.id != now.episode.id }
    val palette = rememberPlayerPalette(now.episode.artworkUrl)
    val closePanel = {
        returnTo = openers[panel]
        panel = PlayerPanel.None
    }

    BackHandler { if (panel != PlayerPanel.None) closePanel() else go.back() }
    Box(Modifier.fillMaxSize().playerBackdrop(palette).onPreviewKeyEvent { event -> event.type == KeyEventType.KeyDown && handleMediaKey(event.key, playback) }) {
        CompositionLocalProvider(LocalContentColor provides Color(palette.title)) {
            Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 32.dp, bottom = 32.dp)) {
                Header(palette)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(now.episode.artworkUrl, Modifier.size(ArtSize), ArtShape)
                    Column(Modifier.weight(1f)) {
                        Text(now.episode.showTitle, style = MaterialTheme.typography.titleMedium, color = Color(palette.soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(now.episode.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        SeekBar(now, palette, playback::seekBack, playback::seekForward, Modifier.padding(top = 24.dp))
                        Controls(now, palette, playback, playFocus, Modifier.padding(top = 18.dp))
                    }
                }
                BottomRow(now, upNext, palette, library, openers, onPanel = { panel = it })
            }
        }
        if (panel != PlayerPanel.None) SidePanel(panel, now, upNext, palette, playback, onClose = closePanel)
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    LaunchedEffect(panel, returnTo) {
        val target = returnTo
        if (panel == PlayerPanel.None && target != null) {
            runCatching { target.requestFocus() }
            returnTo = null
        }
    }
}

/** The remote's media keys, which work whatever has focus (TV-PP). */
fun handleMediaKey(key: Key, playback: TvPlayback): Boolean = when (key) {
    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
        playback.togglePlayPause()
        true
    }
    Key.MediaRewind -> {
        playback.seekBack()
        true
    }
    Key.MediaFastForward -> {
        playback.seekForward()
        true
    }
    else -> false
}

@Composable
private fun Header(palette: PlayerPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_boxlore_mark), contentDescription = null, tint = Color(palette.soft), modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.tv_now_playing), style = MaterialTheme.typography.labelLarge, color = Color(palette.soft))
    }
}

@Composable
private fun Controls(now: NowPlaying, palette: PlayerPalette, playback: TvPlayback, playFocus: FocusRequester, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerButton(Icons.Rounded.Replay10, stringResource(R.string.tv_back_10), playback::seekBack, palette)
        PlayerButton(
            if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (now.isPlaying) R.string.tv_pause else R.string.tv_play),
            playback::togglePlayPause,
            palette,
            Modifier.focusRequester(playFocus),
            main = true,
        )
        PlayerButton(Icons.Rounded.Forward30, stringResource(R.string.tv_forward_30), playback::seekForward, palette)
    }
}

/** Speed, sleep timer, Like and Mark played on the left, as the phone's tray has them; the next episode on the right. */
@Composable
private fun BottomRow(
    now: NowPlaying,
    upNext: List<TvEpisode>,
    palette: PlayerPalette,
    library: TvLibrary,
    openers: Map<PlayerPanel, FocusRequester>,
    onPanel: (PlayerPanel) -> Unit,
) {
    val id = now.episode.id
    val saved by remember(id) { library.episode(id) }.collectAsState(initial = null)
    val episode = saved ?: now.episode
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TrayButton(
            Icons.Outlined.Speed,
            stringResource(R.string.tv_playback_speed),
            palette,
            onClick = { onPanel(PlayerPanel.Speed) },
            modifier = Modifier.focusRequester(openers.getValue(PlayerPanel.Speed)),
            value = if (now.speed == 1f) null else stringResource(R.string.tv_speed_value, PlaybackSpeeds.label(now.speed)),
        )
        TrayButton(
            Icons.Outlined.Bedtime,
            stringResource(R.string.tv_sleep_timer),
            palette,
            onClick = { onPanel(PlayerPanel.SleepTimer) },
            modifier = Modifier.focusRequester(openers.getValue(PlayerPanel.SleepTimer)),
            value = if (now.sleepTimer == SleepTimerChoice.Off) null else sleepTimerText(now.sleepTimer),
        )
        TrayButton(
            if (episode.liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            stringResource(if (episode.liked) R.string.tv_liked else R.string.tv_like),
            palette,
            onClick = { library.setLiked(id, !episode.liked) },
        )
        TrayButton(
            if (episode.played) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
            stringResource(if (episode.played) R.string.tv_played else R.string.tv_mark_played),
            palette,
            onClick = { library.markPlayed(id) },
        )
        Spacer(Modifier.weight(1f))
        val next = upNext.firstOrNull()
        if (next != null) {
            UpNextCard(next, palette, onClick = { onPanel(PlayerPanel.UpNext) }, Modifier.focusRequester(openers.getValue(PlayerPanel.UpNext)))
        } else {
            TrayButton(
                Icons.AutoMirrored.Outlined.QueueMusic,
                stringResource(R.string.tv_up_next_title),
                palette,
                onClick = { onPanel(PlayerPanel.UpNext) },
                modifier = Modifier.focusRequester(openers.getValue(PlayerPanel.UpNext)),
            )
        }
    }
}
