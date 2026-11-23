package cx.aswin.boxlore.tv.ui.show

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.ui.LocalRailFocus
import cx.aswin.boxlore.tv.ui.TvNavigator
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.ListTop
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.RoundButton
import cx.aswin.boxlore.tv.ui.components.entersOn
import cx.aswin.boxlore.tv.ui.dateAndLength
import cx.aswin.boxlore.tv.ui.leftOpens
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/**
 * An episode's page. Opens on Play. Links in the notes can't open a browser on a TV (Play's rule
 * TV-WB), so OK on one shows it for the phone instead.
 */
@Composable
fun EpisodeScreen(episodeId: String, container: TvContainer, go: TvNavigator) {
    val library = container.library
    val episode by remember(episodeId) { library.episode(episodeId) }.collectAsState(initial = null)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var openLink by rememberSaveable { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val current = episode ?: return

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = TvSpace.contentStart, end = TvSpace.safeSide, top = TvSpace.safeTop, bottom = TvSpace.safeTop),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "header") {
            ListTop(listState) {
            EpisodeHeader(
                episode = current,
                playFocus = playFocus,
                onPlay = {
                    container.playback.play(current.id)
                    go.player()
                },
                onShow = { go.show(current.showId) },
                onUpNext = { library.addToUpNext(current.id) },
                onLike = { library.setLiked(current.id, !current.liked) },
                onMarkPlayed = { library.markPlayed(current.id) },
            )
            }
        }
        if (current.description.isNotEmpty()) {
            item(key = "about") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TvColors.surface).padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.tv_about_episode), style = MaterialTheme.typography.titleMedium)
                    Text(
                        current.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColors.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PillButton(stringResource(if (expanded) R.string.tv_show_less else R.string.tv_read_more), onClick = { expanded = !expanded })
                }
            }
        }
        if (current.links.isNotEmpty()) {
            item(key = "links") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tv_episode_links), style = MaterialTheme.typography.titleMedium)
                    current.links.forEach { link -> PillButton(link, onClick = { openLink = link }, icon = Icons.Rounded.Link) }
                }
            }
        }
    }
    openLink?.let { link -> OpenOnPhoneDialog(link, onDone = { openLink = null }) }
    LaunchedEffect(current.id) { runCatching { playFocus.requestFocus() } }
}

@Composable
@Suppress("LongParameterList")
private fun EpisodeHeader(
    episode: TvEpisode,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onShow: () -> Unit,
    onUpNext: () -> Unit,
    onLike: () -> Unit,
    onMarkPlayed: () -> Unit,
) {
    // The notes below start further left, so Left from these buttons goes to the rail, not down.
    val rail = LocalRailFocus.current
    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        Artwork(episode.artworkUrl, Modifier.size(176.dp), RoundedCornerShape(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The show's name opens its page; up from Play reaches it.
            PillButton(
                label = episode.showTitle,
                onClick = onShow,
                restingColor = Color.Transparent,
                restingContentColor = TvColors.onSurfaceVariant,
                modifier = Modifier.leftOpens(rail),
            )
            Text(episode.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dateAndLength(episode.releasedAtMillis, episode.durationMinutes), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant)
            Row(Modifier.padding(top = 14.dp).entersOn(playFocus), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    label = stringResource(if (episode.minutesLeft != null) R.string.tv_resume else R.string.tv_play),
                    onClick = onPlay,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.focusRequester(playFocus).leftOpens(rail),
                )
                PillButton(stringResource(R.string.tv_add_to_up_next), onUpNext, icon = Icons.AutoMirrored.Rounded.PlaylistAdd)
                RoundButton(if (episode.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.tv_like), onLike)
                if (!episode.played) PillButton(stringResource(R.string.tv_mark_played), onMarkPlayed, icon = Icons.Rounded.CheckCircle)
            }
        }
    }
}

/** A link from the notes, to open on the phone. The QR code joins it with the TV's sign-in work. */
@Composable
private fun OpenOnPhoneDialog(link: String, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone) {
        Box(Modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(TvColors.surfaceHigh).padding(30.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.tv_open_on_phone), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.tv_open_on_phone_body), style = MaterialTheme.typography.bodyMedium, color = TvColors.onSurfaceVariant)
                Text(link, style = MaterialTheme.typography.titleMedium)
                val done = remember { FocusRequester() }
                PillButton(stringResource(R.string.tv_done), onDone, modifier = Modifier.padding(top = 10.dp).focusRequester(done))
                LaunchedEffect(Unit) { runCatching { done.requestFocus() } }
            }
        }
    }
}
