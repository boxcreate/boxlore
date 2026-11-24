package cx.aswin.boxlore.tv.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cx.aswin.boxlore.tv.R
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import cx.aswin.boxlore.tv.ui.components.Artwork
import cx.aswin.boxlore.tv.ui.components.ContentSurface
import cx.aswin.boxlore.tv.ui.components.PillButton
import cx.aswin.boxlore.tv.ui.components.ProgressBar
import cx.aswin.boxlore.tv.ui.components.RowPadding
import cx.aswin.boxlore.tv.ui.components.SectionTitle
import cx.aswin.boxlore.tv.ui.components.bleedRow
import cx.aswin.boxlore.tv.ui.lengthText
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.timeLeftText

/**
 * Episode artwork corners. Even corners on purpose: the phone's sweep (two large, two small) clips
 * as a path, which older TV graphics chips redraw on every frame of a scroll.
 */
private val EpisodeArtShape = RoundedCornerShape(14.dp)
private val CoverSize = YourShowsRules.COVER.dp
private val CoverPillWidth = YourShowsRules.PILL.dp
private val CoverShrunk = YourShowsRules.SHRUNK.dp
private val CoverGap = YourShowsRules.GAP.dp
private const val COVER_MOTION_MS = 160

/** Two wide cards fit beside each other with a slice of the third showing, so the row reads as sliding. */
private val WideCardWidth = 340.dp

/** Episodes a Home row holds; the rest are in Library. */
const val HOME_ROW_LIMIT = 4

/**
 * One row of wide episode cards that slides sideways (Jump back in, New episodes), at most four.
 * OK plays; holding OK opens the page. Coming into the row lands on its first card.
 */
@Composable
fun EpisodeRowSection(
    title: String,
    episodes: List<TvEpisode>,
    onPlay: (TvEpisode) -> Unit,
    onOpen: (TvEpisode) -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionTitle(title, trailing = trailing)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = RowPadding, modifier = Modifier.bleedRow().focusRestorer(first)) {
            itemsIndexed(episodes.take(HOME_ROW_LIMIT), key = { _, episode -> episode.id }) { index, episode ->
                WideEpisodeCard(
                    episode,
                    if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onPlay(episode) },
                    onLongClick = { onOpen(episode) },
                )
            }
        }
    }
}

/** A trailing button that opens the full list, shaped like the phone's arrow. */
@Composable
fun SeeAllButton(label: String, onClick: () -> Unit) {
    PillButton(label, onClick, trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight)
}

@Composable
private fun WideEpisodeCard(episode: TvEpisode, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    ContentSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(WideCardWidth).height(96.dp), focusScale = 1.03f) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode.artworkUrl, Modifier.size(72.dp), EpisodeArtShape)
            Column(Modifier.weight(1f)) {
                Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TimeLeftRow(episode, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun TimeLeftRow(episode: TvEpisode, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val left = episode.minutesLeft
        if (left != null) {
            ProgressBar(episode.progress, Modifier.width(84.dp))
            Text(timeLeftText(left), style = MaterialTheme.typography.labelSmall, color = TvColors.onSurfaceVariant)
        } else {
            Text(lengthText(episode.durationMinutes), style = MaterialTheme.typography.labelSmall, color = TvColors.onSurfaceVariant)
        }
    }
}

/**
 * Your shows in up to three rows of eight covers, filling the width. The cover the remote is on
 * widens into a pill with the show's name and the other covers in its row shrink to make room, so
 * the whole row stays on screen and none is covered. Coming into the covers lands on the first
 * one, or the one the remote was on last. All shows, beside the heading, opens every show in Library.
 */
@Composable
fun YourShowsSection(shows: List<TvShow>, onShow: (TvShow) -> Unit, onAllShows: () -> Unit) {
    val rows = remember(shows) { YourShowsRules.arrange(shows) }
    // One requester per cover, so Up and Down keep the column: the cover in focus is a wide pill,
    // and the nearest cover below its middle would sit a column to the right.
    val covers = remember(rows) { rows.map { row -> row.map { FocusRequester() } } }
    val first = remember(covers) { covers.firstOrNull()?.firstOrNull() ?: FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(stringResource(R.string.tv_your_shows), trailing = { SeeAllButton(stringResource(R.string.tv_all_shows), onAllShows) })
        // A focus group, so coming into the covers lands on the first one (or the last one the remote was on).
        Column(Modifier.focusRestorer(first).focusGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                // Which cover in this row the remote is on, or -1; the row's sizes follow it.
                var focusedAt by remember(row) { mutableIntStateOf(-1) }
                Row(Modifier.height(CoverSize), horizontalArrangement = Arrangement.spacedBy(CoverGap), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { index, show ->
                        ShowCover(
                            show = show,
                            modifier =
                            Modifier.focusRequester(covers[rowIndex][index]).focusProperties {
                                YourShowsRules.sameColumn(rows.map { it.size }, rowIndex - 1, index)?.let { up = covers[rowIndex - 1][it] }
                                YourShowsRules.sameColumn(rows.map { it.size }, rowIndex + 1, index)?.let { down = covers[rowIndex + 1][it] }
                            },
                            focused = focusedAt == index,
                            rowFocused = focusedAt >= 0,
                            onFocus = { has ->
                                if (has) {
                                    focusedAt = index
                                } else if (focusedAt == index) {
                                focusedAt = -1
                            }
                            },
                            onClick = { onShow(show) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ShowCover(show: TvShow, modifier: Modifier, focused: Boolean, rowFocused: Boolean, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    val motion = tween<Dp>(COVER_MOTION_MS)
    val width by animateDpAsState(
        if (focused) {
            CoverPillWidth
        } else if (rowFocused) {
        CoverShrunk
    } else {
        CoverSize
    },
        motion,
        label = "cover width"
    )
    val size by animateDpAsState(if (rowFocused && !focused) CoverShrunk else CoverSize, motion, label = "cover size")
    ContentSurface(
        onClick = onClick,
        modifier = modifier.width(width).height(size).onFocusChanged { onFocus(it.hasFocus) },
        shape = CircleShape,
        restingColor = Color.Transparent,
        focusScale = 1f,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(show.artworkUrl, Modifier.padding(if (focused) 6.dp else 0.dp).size(if (focused) 68.dp else size), CircleShape)
            if (focused) {
                Column(Modifier.padding(start = 12.dp, end = 16.dp)) {
                    Text(show.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(show.publisher, style = MaterialTheme.typography.labelSmall, color = TvColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Daily Mix: the heading with its length and Play, then one episode per show, as on the phone. */
@Composable
fun DailyMixSection(mix: List<TvEpisode>, onPlayAll: () -> Unit, onPlay: (TvEpisode) -> Unit, onOpen: (TvEpisode) -> Unit) {
    val total = mix.sumOf { it.minutesLeft ?: it.durationMinutes }
    val first = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.tv_daily_mix), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.tv_mix_summary, pluralStringResource(R.plurals.tv_episode_count, mix.size, mix.size), lengthText(total)),
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PillButton(stringResource(R.string.tv_play), onPlayAll, icon = Icons.Rounded.PlayArrow)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = RowPadding, modifier = Modifier.bleedRow().focusRestorer(first)) {
            itemsIndexed(mix, key = { _, episode -> episode.id }) { index, episode ->
                MixCard(episode, if (index == 0) Modifier.focusRequester(first) else Modifier, onClick = { onPlay(episode) }, onLongClick = { onOpen(episode) })
            }
        }
    }
}

@Composable
private fun MixCard(episode: TvEpisode, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    ContentSurface(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(300.dp).height(116.dp), shape = RoundedCornerShape(24.dp), focusScale = 1.03f) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode.artworkUrl, Modifier.size(88.dp), RoundedCornerShape(18.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(episode.showTitle, style = MaterialTheme.typography.labelSmall, color = TvColors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TimeLeftRow(episode, Modifier.padding(top = 4.dp))
            }
        }
    }
}
