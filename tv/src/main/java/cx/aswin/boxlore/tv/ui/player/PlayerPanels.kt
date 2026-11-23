package cx.aswin.boxlore.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.PlaybackSpeeds
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.sleepTimerText
import cx.aswin.boxlore.tv.ui.theme.TvSpace
import cx.aswin.boxlore.tv.ui.timeLeftText

/** The side panels the player's bottom row opens. */
enum class PlayerPanel { None, UpNext, SleepTimer, Speed }

private val PanelWidth = 420.dp
private const val SCRIM_ALPHA = 0.5f

/**
 * A panel from the right, in the cover's colours, over a dimmed player. The remote stays inside it
 * until a choice or Back closes it.
 */
@Composable
fun SidePanel(panel: PlayerPanel, now: NowPlaying, upNext: List<TvEpisode>, palette: PlayerPalette, playback: TvPlayback, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.width(PanelWidth).fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                .background(Color(palette.panel))
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .padding(start = 24.dp, end = TvSpace.safeSide, top = TvSpace.safeTop),
        ) {
            // The heading stays put; only the choices under it scroll.
            when (panel) {
                PlayerPanel.UpNext -> PanelTitle(stringResource(R.string.tv_up_next_title), pluralStringResource(R.plurals.tv_episode_count, upNext.size, upNext.size), palette)
                PlayerPanel.SleepTimer -> PanelTitle(stringResource(R.string.tv_sleep_timer), null, palette)
                PlayerPanel.Speed -> PanelTitle(stringResource(R.string.tv_playback_speed), null, palette)
                PlayerPanel.None -> Unit
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = TvSpace.safeTop),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PanelChoices(panel, now, upNext, palette, playback, first, onClose)
            }
        }
    }
    LaunchedEffect(panel) { runCatching { first.requestFocus() } }
}

/** The panel's choices. The remote opens on the current one (the first episode in Up Next). */
@Composable
@Suppress("LongParameterList")
private fun PanelChoices(
    panel: PlayerPanel,
    now: NowPlaying,
    upNext: List<TvEpisode>,
    palette: PlayerPalette,
    playback: TvPlayback,
    first: FocusRequester,
    onClose: () -> Unit,
) {
    val landing = { landsHere: Boolean -> if (landsHere) Modifier.focusRequester(first) else Modifier }
    when (panel) {
        PlayerPanel.UpNext -> upNext.forEachIndexed { index, episode ->
            val left = timeLeftText(episode.minutesLeft ?: episode.durationMinutes)
            PanelChoice(episode.title, stringResource(R.string.tv_show_and_time, episode.showTitle, left), false, palette, landing(index == 0), episode.artworkUrl) {
                playback.play(episode.id)
                onClose()
            }
        }
        PlayerPanel.SleepTimer -> SleepTimerChoice.entries.forEach { choice ->
            PanelChoice(sleepTimerText(choice), null, choice == now.sleepTimer, palette, landing(choice == now.sleepTimer)) {
                playback.setSleepTimer(choice)
                onClose()
            }
        }
        PlayerPanel.Speed -> {
            val current = PlaybackSpeeds.all.indexOf(now.speed).coerceAtLeast(0)
            PlaybackSpeeds.all.forEachIndexed { index, speed ->
                PanelChoice(stringResource(R.string.tv_speed_value, PlaybackSpeeds.label(speed)), null, speed == now.speed, palette, landing(index == current)) {
                    playback.setSpeed(speed)
                    onClose()
                }
            }
        }
        PlayerPanel.None -> Unit
    }
}

@Composable
private fun PanelTitle(title: String, detail: String?, palette: PlayerPalette) {
    Column(Modifier.padding(start = 12.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color(palette.title))
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = Color(palette.soft))
    }
}

/** One choice in a panel: a full-width row that turns near-white when the remote is on it. */
@Composable
@Suppress("LongParameterList")
private fun PanelChoice(
    title: String,
    subtitle: String?,
    selected: Boolean,
    palette: PlayerPalette,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = Color(if (selected) palette.container else palette.panel),
            contentColor = Color(palette.title),
            focusedContainerColor = Color(palette.focus),
            focusedContentColor = Color(palette.onFocus),
            pressedContainerColor = Color(palette.focus),
            pressedContentColor = Color(palette.onFocus),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (artworkUrl != null) Artwork(artworkUrl, Modifier.size(52.dp), RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
