package cx.aswin.boxlore.tv.ui.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.home.TimeRules

private val SeekHeight = 40.dp
private val ControlHeight = 80.dp
private val PlayWidth = 156.dp
private val SkipWidth = 100.dp
private val SkipShape = RoundedCornerShape(28.dp)
private val TrayHeight = 56.dp
private val UpNextWidth = 320.dp
private const val CONTROL_FOCUS_SCALE = 1.06f

/** The player's control colours: resting in the cover's tones, near-white where the remote is. */
@Composable
private fun PlayerPalette.controlColors(main: Boolean = false): ClickableSurfaceColors =
    ClickableSurfaceDefaults.colors(
        containerColor = Color(if (main) primary else container),
        contentColor = Color(if (main) onPrimary else onContainer),
        focusedContainerColor = Color(if (main) primary else focus),
        focusedContentColor = Color(if (main) onPrimary else onFocus),
        pressedContainerColor = Color(if (main) primary else focus),
        pressedContentColor = Color(if (main) onPrimary else onFocus),
    )

/**
 * The phone's seek bar: a played track, a tall handle and an end stop. With the remote on it,
 * Left and Right skip back 10 and ahead 30 seconds, the phone's skip lengths.
 */
@Composable
fun SeekBar(now: NowPlaying, palette: PlayerPalette, onSeekBack: () -> Unit, onSeekForward: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val total = now.episode.durationMinutes * SECONDS_PER_MINUTE
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(SeekHeight)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { event ->
                    val skip =
                        when (event.key) {
                            Key.DirectionLeft -> onSeekBack
                            Key.DirectionRight -> onSeekForward
                            else -> null
                        }
                    if (skip != null && event.type == KeyEventType.KeyDown) skip()
                    skip != null
                }
                .focusable()
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(now.progress, 0f..1f) }
                .drawBehind { drawSeekTrack(now.progress, focused, palette) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(TimeRules.clock(now.elapsedSeconds), style = MaterialTheme.typography.labelLarge, color = Color(palette.soft))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (focused) Text(stringResource(R.string.tv_seek_hint), style = MaterialTheme.typography.labelMedium, color = Color(palette.title))
            }
            Text(
                stringResource(R.string.tv_remaining_clock, TimeRules.clock(total - now.elapsedSeconds)),
                style = MaterialTheme.typography.labelLarge,
                color = Color(palette.soft),
            )
        }
    }
}

private fun DrawScope.drawSeekTrack(progress: Float, focused: Boolean, palette: PlayerPalette) {
    val trackHeight = (if (focused) 10.dp else 6.dp).toPx()
    val handleWidth = (if (focused) 6.dp else 4.dp).toPx()
    val handleHeight = (if (focused) 34.dp else 24.dp).toPx()
    val gap = 5.dp.toPx()
    val y = size.height / 2f
    val x = (size.width * progress.coerceIn(0f, 1f)).coerceIn(handleWidth / 2f, size.width - handleWidth / 2f)
    val corner = CornerRadius(trackHeight / 2f)
    val top = y - trackHeight / 2f
    val playedEnd = x - handleWidth / 2f - gap
    if (playedEnd > 0f) drawRoundRect(Color(palette.primary), Offset(0f, top), Size(playedEnd, trackHeight), corner)
    val restStart = x + handleWidth / 2f + gap
    if (restStart < size.width) {
        drawRoundRect(Color(palette.track), Offset(restStart, top), Size(size.width - restStart, trackHeight), corner)
        if (restStart < size.width - trackHeight) {
            drawCircle(Color(palette.primary), trackHeight / 4f, Offset(size.width - trackHeight / 2f, y))
        }
    }
    drawRoundRect(
        Color(if (focused) palette.focus else palette.primary),
        Offset(x - handleWidth / 2f, y - handleHeight / 2f),
        Size(handleWidth, handleHeight),
        CornerRadius(handleWidth / 2f),
    )
}

/**
 * The phone's three big controls: Back 10 and Forward 30 as rounded squares around a wide Play.
 * Play keeps its colour with the remote on it and takes a light ring; the others turn near-white.
 */
@Composable
fun PlayerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    palette: PlayerPalette,
    modifier: Modifier = Modifier,
    main: Boolean = false,
) {
    val shape = if (main) CircleShape else SkipShape
    Surface(
        onClick = onClick,
        modifier = modifier.width(if (main) PlayWidth else SkipWidth).height(ControlHeight),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = palette.controlColors(main),
        scale = ClickableSurfaceDefaults.scale(focusedScale = CONTROL_FOCUS_SCALE),
        border =
        ClickableSurfaceDefaults.border(
            focusedBorder = if (main) Border(BorderStroke(3.dp, Color(palette.focus)), shape = shape) else Border.None,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(if (main) 40.dp else 34.dp))
        }
    }
}

/**
 * A round button in the bottom row. The one the remote is on widens to show its name and pushes
 * the ones after it along, so none is covered. [value] (a speed, a timer) shows at all times.
 */
@Composable
fun TrayButton(icon: ImageVector, label: String, palette: PlayerPalette, onClick: () -> Unit, modifier: Modifier = Modifier, value: String? = null) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(TrayHeight).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = palette.controlColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxHeight().animateContentSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            val text = value ?: label.takeIf { focused }
            if (text != null) Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** The next episode, at the bottom right. OK opens the whole Up Next list. */
@Composable
fun UpNextCard(next: TvEpisode, palette: PlayerPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(UpNextWidth),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = palette.controlColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(next.artworkUrl, Modifier.size(48.dp), RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(R.string.tv_up_next_title), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60
