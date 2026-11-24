package cx.aswin.boxlore.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.LocalRailFocus
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.ProgressBar
import cx.aswin.boxlore.tv.ui.components.RoundButton
import cx.aswin.boxlore.tv.ui.components.entersOn
import cx.aswin.boxlore.tv.ui.leftOpens
import cx.aswin.boxlore.tv.ui.timeLeftText

/** The card's fill: boxlore's iris, deepened so white text reads on it. */
private val CardColor = Color(0xFF2B2370)
private val CardOnColor = Color(0xFFECE7FF)
private val CardSoftColor = Color(0xFFCFC7FF)
private val CardButtonColor = Color(0xFF463D8F)
private val CardHeight = 210.dp

/**
 * The top of Home. With nothing in the player it is the Resume card for the last episode. Once an
 * episode is in the player it becomes its remote: Pause or Play, back 10 and ahead 30 seconds,
 * and Open player, so the listener never has to leave Home to stop the sound. Resume starts the
 * episode right here, and the card turns into that remote with the main button where it was.
 */
@Composable
fun NowPlayingCard(episode: TvEpisode, playback: TvPlayback, first: Modifier, onOpenPlayer: () -> Unit, onOpenEpisode: () -> Unit) {
    // Read here, not in HomeScreen, so the once-a-second ticks redraw only this card.
    val now by playback.nowPlaying.collectAsState()
    val mode = NowPlayingCardRules.mode(episode.id, now)
    val shown = now?.takeIf { it.episode.id == episode.id }
    val minutesLeft = shown?.minutesLeft ?: episode.minutesLeft ?: episode.durationMinutes
    val eyebrow =
        when (mode) {
            CardMode.Playing -> R.string.tv_now_playing_eyebrow
            CardMode.Paused -> R.string.tv_paused_eyebrow
            CardMode.Resting -> R.string.tv_resume_eyebrow
        }
    val shape = RoundedCornerShape(28.dp)
    // At least as tall as the artwork, taller when a long title needs it, so the buttons always keep their room.
    Row(Modifier.fillMaxWidth().heightIn(min = CardHeight).height(IntrinsicSize.Min).clip(shape).background(CardColor)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 30.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(eyebrow, timeLeftText(minutesLeft)), style = MaterialTheme.typography.bodySmall, color = CardSoftColor)
            Text(
                episode.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.2f),
                color = CardOnColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(episode.showTitle, style = MaterialTheme.typography.bodyLarge, color = CardSoftColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressBar(shown?.progress ?: episode.progress, Modifier.width(300.dp).padding(top = 4.dp), track = CardButtonColor, fill = CardOnColor)
            Spacer(Modifier.weight(1f))
            CardButtons(mode, first, onMain = { NowPlayingCardRules.press(mode, playback, episode.id) }, playback, onOpenPlayer, onOpenEpisode)
        }
        // The card's own clip rounds the art's outer corners; a plain inner edge avoids a second, path-shaped clip.
        Artwork(episode.artworkUrl, Modifier.width(CardHeight).fillMaxHeight(), RectangleShape)
    }
}

@Composable
@Suppress("LongParameterList")
private fun CardButtons(mode: CardMode, first: Modifier, onMain: () -> Unit, playback: TvPlayback, onOpenPlayer: () -> Unit, onOpenEpisode: () -> Unit) {
    val main = remember { FocusRequester() }
    // Coming up from the rows below always lands on Play or Pause, never on a skip button.
    Row(Modifier.entersOn(main), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Always the first button, so the remote stays on it when the card changes mode.
        PillButton(
            label = stringResource(mainLabel(mode)),
            onClick = onMain,
            icon = if (mode == CardMode.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            modifier = first.focusRequester(main).leftOpens(LocalRailFocus.current),
            restingColor = CardButtonColor,
            restingContentColor = CardOnColor,
        )
        if (mode == CardMode.Resting) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.tv_open_episode), onOpenEpisode, restingColor = CardButtonColor)
        } else {
            RoundButton(Icons.Rounded.Replay10, stringResource(R.string.tv_back_10), playback::seekBack, restingColor = CardButtonColor)
            RoundButton(Icons.Rounded.Forward30, stringResource(R.string.tv_forward_30), playback::seekForward, restingColor = CardButtonColor)
            PillButton(
                label = stringResource(R.string.tv_open_player),
                onClick = onOpenPlayer,
                icon = Icons.Rounded.OpenInFull,
                restingColor = CardButtonColor,
                restingContentColor = CardOnColor,
            )
        }
    }
}

private fun mainLabel(mode: CardMode): Int = when (mode) {
    CardMode.Playing -> R.string.tv_pause
    CardMode.Paused -> R.string.tv_play
    CardMode.Resting -> R.string.tv_resume
}

/** What the card is: the last episode at rest, or the remote for the episode in the player. */
enum class CardMode { Resting, Playing, Paused }

object NowPlayingCardRules {
    /** The card is the remote only for the episode actually in the player. */
    fun mode(cardEpisodeId: String, now: NowPlaying?): CardMode = when {
        now == null || !now.loaded || now.episode.id != cardEpisodeId -> CardMode.Resting
        now.isPlaying -> CardMode.Playing
        else -> CardMode.Paused
    }

    /** The main button: Resume starts the episode here, then it pauses and plays in place. */
    fun press(mode: CardMode, playback: TvPlayback, episodeId: String) {
        if (mode == CardMode.Resting) playback.play(episodeId) else playback.togglePlayPause()
    }
}
