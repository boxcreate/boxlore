package cx.aswin.boxlore.wear.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.ui.components.ArtworkWithPlaying
import cx.aswin.boxlore.wear.ui.components.PlayingEpisode
import cx.aswin.boxlore.wear.ui.components.ScreenFit
import cx.aswin.boxlore.wear.ui.components.fit
import cx.aswin.boxlore.wear.ui.components.rememberDomeShape
import cx.aswin.boxlore.wear.ui.components.rememberScreenScale

/** How Home's top block arranges itself around what it has to show. */
internal object HomeDomeRules {
    /**
     * The progress arc runs along the top of the screen in two parts, one each side of the time,
     * as the player's ring leaves the top open. Angles are clockwise from three o'clock.
     */
    const val ARC_START = 212f
    const val ARC_PART = 36f
    const val ARC_GAP = 44f

    /** How much of each part of the arc is filled at [progress]: the left part fills first. */
    fun arcFill(progress: Float): Pair<Float, Float> {
        val filled = progress.coerceIn(0f, 1f) * 2f
        return filled.coerceAtMost(1f) to (filled - 1f).coerceAtLeast(0f)
    }

    /** The episode's name is why the listener looks here: three lines, two on a small watch. */
    fun nameLines(compact: Boolean): Int = if (compact) 2 else 3

    /**
     * Whether the show's name, the time left and their icons fit on one row. When they do the dome
     * is shorter; when they do not the show's name gets a row to itself instead of being cut to a
     * few letters.
     */
    fun sharesOneRow(showNameWidth: Int, timeWidth: Int, fixedWidth: Int, available: Int): Boolean =
        showNameWidth == 0 || fits(showNameWidth + timeWidth + fixedWidth, available)

    /** Whether something [needed] wide can be shown whole in [available]. */
    fun fits(needed: Int, available: Int): Boolean = needed <= available
}

private val ChipSize = 22.dp
private val StateIconSize = 12.dp
private val RowGap = 4.dp
private val ArcInset = 9.dp
private val ArcWidth = 3.dp

/**
 * Home's top block on a round watch: a dome that follows the top of the screen and is as tall as
 * what it holds. The episode's name comes first, then the show, then where it plays and how much
 * is left; progress runs along the edge of the screen above them.
 */
@Composable
internal fun NowPlayingDome(nowPlaying: NowPlaying, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale = rememberScreenScale()
    val openPlayer = stringResource(R.string.wear_open_player)
    val round = LocalConfiguration.current.isScreenRound
    // boxlore's own colour, whatever is playing: artwork colours are for the player. A dark yellow or
    // green cover would turn the first thing on Home muddy.
    val ground = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val track = content.copy(alpha = 0.26f)
    val progress = nowPlaying.progress.coerceIn(0f, 1f)
    DomeBlock(
        onClick = onClick,
        label = "${nowPlaying.episode.title}. ${nowPlaying.episode.showTitle}. $openPlayer",
        modifier = modifier,
        ground = ground,
        // Drawn on the dome's colour and under its text.
        decoration = { if (round) progressArc(progress, track, content) },
    ) {
        Text(
            text = nowPlaying.episode.title,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            textAlign = TextAlign.Center,
            // Only a small round watch is short of room: a rectangular one has the height.
            maxLines = HomeDomeRules.nameLines(compact = round && ScreenFit.isCompact(scale)),
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(nameWidth(round)),
        )
        DomeDetails(nowPlaying, content)
    }
}

/** Shown when nothing is playing and no earlier state is known; the phone's widgets say the same. */
@Composable
internal fun ReadyDome(modifier: Modifier = Modifier) {
    DomeBlock(onClick = null, label = stringResource(R.string.wear_home_ready), modifier = modifier, ground = MaterialTheme.colorScheme.primaryContainer) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = stringResource(R.string.wear_home_ready),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(nameWidth(LocalConfiguration.current.isScreenRound)),
        )
    }
}

/** The dome itself: shape, colour, the room it leaves for the time, and one spoken label. */
@Composable
private fun DomeBlock(
    onClick: (() -> Unit)?,
    label: String,
    modifier: Modifier,
    ground: Color,
    decoration: DrawScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scale = rememberScreenScale()
    val round = LocalConfiguration.current.isScreenRound
    val shape = rememberDomeShape()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(ground)
                .drawBehind(decoration)
                .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
                .clearAndSetSemantics { contentDescription = label }
                // On a round watch the time is drawn inside the dome, so the content starts below it.
                // The time is the same size on every watch, so the room for it is not scaled down.
                .padding(top = if (round) 31.dp else 12.dp, bottom = 13.dp.fit(scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        content()
    }
}

/** The show's name with its artwork, and where it plays with the time left: one row when they fit, two when they do not. */
@Composable
private fun DomeDetails(nowPlaying: NowPlaying, content: Color) {
    val detail = content.copy(alpha = 0.82f)
    val style = MaterialTheme.typography.bodySmall
    val left = stringResource(R.string.wear_minutes_left, nowPlaying.minutesLeft)
    val show = nowPlaying.episode.showTitle
    val (icon, state) =
        when {
            !nowPlaying.isPlaying -> Icons.Rounded.Pause to stringResource(R.string.wear_home_paused)
            nowPlaying.place == PlaybackPlace.Phone -> Icons.Rounded.Smartphone to stringResource(R.string.wear_home_on_phone)
            else -> Icons.AutoMirrored.Rounded.VolumeUp to null
        }
    val stateAndTime = if (state == null) left else stringResource(R.string.wear_home_state_and_time, state, left)
    val timeAfterShow = stringResource(R.string.wear_home_state_and_time, "", left)
    val measurer = rememberTextMeasurer()
    val round = LocalConfiguration.current.isScreenRound
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(if (round) DETAIL_WIDTH else CARD_WIDTH), contentAlignment = Alignment.Center) {
        val fixed = with(LocalDensity.current) { (ChipSize + StateIconSize + RowGap * 3).roundToPx() }
        val available = constraints.maxWidth
        val showWidth = remember(show, style) { if (show.isBlank()) 0 else measurer.measure(show, style, maxLines = 1).size.width }
        val timeWidth = remember(timeAfterShow, style) { measurer.measure(timeAfterShow, style, maxLines = 1).size.width }
        if (HomeDomeRules.sharesOneRow(showWidth, timeWidth, fixed, constraints.maxWidth)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                if (show.isNotBlank()) ShowChip(nowPlaying)
                StateIcon(icon, detail)
                Text(
                    text = if (show.isBlank()) stateAndTime else stringResource(R.string.wear_home_state_and_time, show, left),
                    style = style,
                    color = detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                    ShowChip(nowPlaying)
                    Text(
                        text = show,
                        style = style,
                        fontWeight = FontWeight.Medium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // On a narrow watch "On phone · 39 min left" may not fit. The icon already says where it
                // plays, so the words go and the time left stays whole.
                val stateWidth = remember(stateAndTime, style) { measurer.measure(stateAndTime, style, maxLines = 1).size.width }
                val iconWidth = with(LocalDensity.current) { (StateIconSize + RowGap).roundToPx() }
                val wholeState = HomeDomeRules.fits(stateWidth + iconWidth, available)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                    StateIcon(icon, detail)
                    Text(text = if (wholeState) stateAndTime else left, style = style, color = detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ShowChip(nowPlaying: NowPlaying) {
    // While it plays, the bars move on the artwork: the one sign on Home that sound is coming out.
    ArtworkWithPlaying(
        url = nowPlaying.episode.artworkUrl,
        playing = PlayingEpisode(nowPlaying.episode.id, isPlaying = true).takeIf { nowPlaying.isPlaying },
        modifier = Modifier.size(ChipSize),
        shape = CircleShape,
    )
}

@Composable
private fun StateIcon(icon: ImageVector, tint: Color) {
    Box(modifier = Modifier.size(StateIconSize), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(StateIconSize))
    }
}

/** Progress along the top edge of the screen, inside the dome. The block spans the screen and starts at its top. */
private fun DrawScope.progressArc(progress: Float, track: Color, fill: Color) {
    val radius = size.width / 2f - ArcInset.toPx()
    val topLeft = Offset(ArcInset.toPx(), ArcInset.toPx())
    val box = Size(radius * 2f, radius * 2f)
    val stroke = Stroke(width = ArcWidth.toPx(), cap = StrokeCap.Round)
    val (left, right) = HomeDomeRules.arcFill(progress)
    val rightStart = HomeDomeRules.ARC_START + HomeDomeRules.ARC_PART + HomeDomeRules.ARC_GAP
    for ((start, filled) in listOf(HomeDomeRules.ARC_START to left, rightStart to right)) {
        drawArc(track, start, HomeDomeRules.ARC_PART, useCenter = false, topLeft = topLeft, size = box, style = stroke)
        if (filled > 0f) drawArc(fill, start, HomeDomeRules.ARC_PART * filled, useCenter = false, topLeft = topLeft, size = box, style = stroke)
    }
}

/** The name stays inside the narrow top of the dome; the rows under it sit where the dome is wider. */
private const val NAME_WIDTH = 0.66f
private const val DETAIL_WIDTH = 0.76f

/** A card on a screen with no curve can use nearly all of its width. */
private const val CARD_WIDTH = 0.88f

private fun nameWidth(round: Boolean): Float = if (round) NAME_WIDTH else CARD_WIDTH
