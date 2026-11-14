package cx.aswin.boxlore.wear.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.ui.components.Artwork
import cx.aswin.boxlore.wear.ui.components.NameWithTail
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.fit
import cx.aswin.boxlore.wear.ui.components.rememberArtworkSeed
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** What the player's buttons do. */
internal class PlayerActions(
    val onTogglePlay: () -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onUpNext: () -> Unit,
    val onMore: () -> Unit,
    /** Moves playback to the other device; null when there is no other device to move it to. */
    val onSwitchPlace: (() -> Unit)? = null,
)

/** Dark enough that white text on the busiest artwork keeps its contrast. */
private const val ARTWORK_SCRIM = 0.84f

/** The ring leaves this much of the circle open at the top, where the time sits. */
private const val RING_START = 300f
private const val RING_END = 240f

@Composable
internal fun PlayerScreen(nowPlaying: NowPlaying, actions: PlayerActions) {
    val seed by rememberArtworkSeed(nowPlaying.episode.artworkUrl)
    val colors = remember(seed) { PlayerPalette.fromSeed(seed) }
    val scale = rememberScreenScale()
    // Only a small round watch is short of room above the buttons: there the time sits inside the ring.
    val tight = ScreenFit.isCompact(scale) && LocalConfiguration.current.isScreenRound
    // Nothing scrolls under the time here, so it needs no patch behind it; it sits on the artwork.
    ScreenScaffold(timeText = { TimeText(backgroundColor = Color.Transparent) }) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The episode's artwork fills the face under a dark scrim, so the player looks like what is playing.
            if (nowPlaying.episode.artworkUrl != null) {
                Box(modifier = Modifier.align(Alignment.Center).padding(9.dp).aspectRatio(1f).clip(CircleShape)) {
                    Artwork(url = nowPlaying.episode.artworkUrl, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = ARTWORK_SCRIM)))
                }
            }
            CircularProgressIndicator(
                progress = { nowPlaying.progress },
                modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = RING_START,
                endAngle = RING_END,
                strokeWidth = 5.dp,
                colors =
                    ProgressIndicatorDefaults.colors(
                        indicatorColor = Color(colors.accent),
                        trackColor = Color(colors.quiet),
                    ),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp.fit(scale)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The episode's name is what the listener checks here, so it gets two lines; a small
                // watch has room for one between the time and the buttons.
                Text(
                    text = nowPlaying.episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (tight) 1 else 2,
                    overflow = if (tight) TextOverflow.Clip else TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.76f).then(if (tight) Modifier.basicMarquee() else Modifier),
                )
                TransportRow(nowPlaying, colors, actions, scale, Modifier.padding(top = 8.dp.fit(scale), bottom = 5.dp.fit(scale)))
                if (nowPlaying.failed) {
                    Text(
                        text = stringResource(R.string.wear_could_not_play),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    // The show's name is cut before the time left ever is.
                    NameWithTail(
                        // While the phone plays, where it plays matters more here than the show's name.
                        name = if (nowPlaying.place == PlaybackPlace.Phone) stringResource(R.string.wear_home_on_phone) else nowPlaying.episode.showTitle,
                        tail = stringResource(R.string.wear_minutes_left, nowPlaying.minutesLeft),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(colors.detail),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SmallControls(nowPlaying.place, colors, actions, scale, Modifier.padding(top = 6.dp.fit(scale)))
                MoreDots(colors, actions.onMore, scale)
            }
        }
    }
}

@Composable
private fun TransportRow(nowPlaying: NowPlaying, colors: PlayerColors, actions: PlayerActions, scale: Float, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp.fit(scale))) {
        RoundControl(
            icon = seekBackIcon(nowPlaying.seekBackSeconds),
            label = pluralStringResource(R.plurals.wear_seek_back, nowPlaying.seekBackSeconds, nowPlaying.seekBackSeconds),
            size = 48.dp.fit(scale),
            container = colors.container,
            content = colors.onContainer,
            onClick = actions.onSeekBack,
        )
        // A rounded square while playing, the way the phone's pause button is drawn.
        FilledIconButton(
            onClick = actions.onTogglePlay,
            modifier = Modifier.size(68.dp.fit(scale)),
            shapes = IconButtonDefaults.shapes(if (nowPlaying.isPlaying) RoundedCornerShape(23.dp.fit(scale)) else CircleShape),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(colors.accent),
                    contentColor = Color(colors.onAccent),
                ),
        ) {
            Icon(
                imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (nowPlaying.isPlaying) R.string.wear_pause else R.string.wear_play),
                modifier = Modifier.size(38.dp.fit(scale)),
            )
        }
        RoundControl(
            icon = seekForwardIcon(nowPlaying.seekForwardSeconds),
            label = pluralStringResource(R.plurals.wear_seek_forward, nowPlaying.seekForwardSeconds, nowPlaying.seekForwardSeconds),
            size = 48.dp.fit(scale),
            container = colors.container,
            content = colors.onContainer,
            onClick = actions.onSeekForward,
        )
    }
}

/** Where it plays, volume and Up next. The first is only there when playback can move to the other device. */
@Composable
private fun SmallControls(place: PlaybackPlace, colors: PlayerColors, actions: PlayerActions, scale: Float, modifier: Modifier) {
    val context = LocalContext.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.onSwitchPlace?.let { PlaceControl(place, colors, scale, it) }
        RoundControl(
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            label = stringResource(R.string.wear_volume),
            size = 30.dp.fit(scale),
            container = colors.quiet,
            content = colors.onContainer,
            onClick = { showSystemVolume(context) },
        )
        RoundControl(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = stringResource(R.string.wear_up_next),
            size = 30.dp.fit(scale),
            container = colors.quiet,
            content = colors.onContainer,
            onClick = actions.onUpNext,
        )
    }
}

/**
 * Shows the device that is playing and moves playback to the other one. It is the brightest of the
 * small controls: which device the big button starts is the first thing to know on this screen.
 */
@Composable
private fun PlaceControl(place: PlaybackPlace, colors: PlayerColors, scale: Float, onSwitch: () -> Unit) {
    val onPhone = place == PlaybackPlace.Phone
    val label = stringResource(if (onPhone) R.string.wear_play_on_watch else R.string.wear_play_on_phone)
    Row(
        modifier =
            Modifier
                .height(30.dp.fit(scale))
                .clip(CircleShape)
                .background(Color(colors.accent))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onSwitch)
                .semantics { contentDescription = label }
                .padding(start = 9.dp.fit(scale), end = 5.dp.fit(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (onPhone) Icons.Rounded.Smartphone else Icons.Rounded.Watch,
            contentDescription = null,
            tint = Color(colors.onAccent),
            modifier = Modifier.size(16.dp.fit(scale)),
        )
        Icon(imageVector = Icons.Rounded.UnfoldMore, contentDescription = null, tint = Color(colors.onAccent), modifier = Modifier.size(13.dp.fit(scale)))
    }
}

/** The three dots, alone under the row: speed and the sleep timer live behind them. */
@Composable
private fun MoreDots(colors: PlayerColors, onMore: () -> Unit, scale: Float) {
    val label = stringResource(R.string.wear_more_controls)
    Box(
        modifier =
            Modifier
                .size(width = 56.dp.fit(scale), height = 24.dp.fit(scale))
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onMore)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Rounded.MoreHoriz, contentDescription = null, tint = Color(colors.detail), modifier = Modifier.size(22.dp.fit(scale)))
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    label: String,
    size: Dp,
    container: Int,
    content: Int,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(container), contentColor = Color(content)),
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(size * 0.54f))
    }
}

private fun seekBackIcon(seconds: Int): ImageVector =
    when (seconds) {
        5 -> Icons.Rounded.Replay5
        10 -> Icons.Rounded.Replay10
        30 -> Icons.Rounded.Replay30
        else -> Icons.Rounded.Replay
    }

private fun seekForwardIcon(seconds: Int): ImageVector =
    when (seconds) {
        5 -> Icons.Rounded.Forward5
        10 -> Icons.Rounded.Forward10
        30 -> Icons.Rounded.Forward30
        else -> Icons.Rounded.Redo
    }

/** Opens the watch's own volume control for media. */
private fun showSystemVolume(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
}
