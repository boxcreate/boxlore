package cx.aswin.boxlore.feature.info.branded.waveform

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedSpotlight
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.formatEpisodeDuration
import cx.aswin.boxlore.feature.info.components.formatEpisodeRelativeDate
import cx.aswin.boxlore.feature.info.components.rememberPodcastEpisodeControlClick
import cx.aswin.boxlore.feature.info.sections.stripHtml
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)

/**
 * The latest episode on a dark card. The episode's waveform runs across the top like its cover,
 * bright up to where you are; below it "LATEST" in red, the title, two lines of the episode's own
 * description and the actions. The bars move while it plays.
 */
@Composable
internal fun WaveformSpotlight(spotlight: BrandedSpotlight, modifier: Modifier) {
    val episode = spotlight.episode
    val selectLabel = stringResource(R.string.podcast_episode_select)
    Surface(
        shape = CardShape,
        color = WaveformColors.Card,
        contentColor = WaveformColors.Text,
        border = if (spotlight.isSelected) BorderStroke(2.dp, WaveformColors.Text) else BorderStroke(1.dp, WaveformColors.CardBorder),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).expressiveClickable(
            shape = CardShape,
            onLongClickLabel = selectLabel,
            onLongClick = spotlight.onLongPress,
            onClick = spotlight.onOpen,
        ),
    ) {
        Column {
            WaveformBand(
                progress = spotlight.progress ?: if (spotlight.isCompleted) 1f else 0f,
                playing = spotlight.actions.isPlaying,
            )
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.waveform_latest).uppercase(Locale.getDefault()),
                        style = brandedTextStyle(rememberWaveformBarlow(), 11.sp, 14.sp, letterSpacing = 2.4.sp).copy(fontWeight = FontWeight.Bold),
                        color = WaveformColors.RedText,
                        maxLines = 1,
                        modifier = Modifier.offset(x = LetterSpacingLead),
                    )
                    Text(
                        spotlightDetails(spotlight),
                        style = brandedSansStyle(13.sp, 16.sp),
                        color = WaveformColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(episode.title, style = brandedSansStyle(19.sp, 24.sp, weight = 600), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val description = remember(episode.description) { stripHtml(episode.description) }
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = brandedSansStyle(14.sp, 19.sp),
                        color = WaveformColors.Description,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                SpotlightActions(spotlight)
            }
        }
    }
}

/** When it came out and how long it is; how long is left while you are partway; or that you played it. */
@Composable
private fun spotlightDetails(spotlight: BrandedSpotlight): String {
    val episode = spotlight.episode
    val length = when {
        spotlight.isCompleted -> stringResource(R.string.episode_info_played)
        spotlight.progress != null && !spotlight.timeLeft.isNullOrBlank() -> spotlight.timeLeft
        else -> formatEpisodeDuration(episode.duration)
    }
    return listOf(formatEpisodeRelativeDate(episode.publishedDate), length).filter(String::isNotBlank).joinToString(" · ")
}

/**
 * The band across the top of the card: forty bars, white up to where you are and grey after,
 * with nothing else marking the spot. While the episode plays the bars breathe on the logo's
 * four beats.
 */
@Composable
private fun WaveformBand(progress: Float, playing: Boolean) {
    val animated = animateFloatAsState(progress, label = "waveform_spotlight_progress")
    val moving = playing && remember { ValueAnimator.areAnimatorsEnabled() }
    val live = animateFloatAsState(if (moving) 1f else 0f, tween(400), label = "waveform_spotlight_live")
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(moving) {
        while (moving || live.value > 0f) withFrameMillis { clock.longValue = it }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(WaveformBand.HEIGHT.dp)
            .background(WaveformColors.Band)
            .drawBehind {
                drawRect(WaveformColors.BandDivider, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 14.dp)
            .progressSemantics(progress)
            .drawBehind {
                val unit = 1.dp.toPx()
                val barWidth = WaveformBand.BAR_WIDTH * unit
                val middle = (size.height - unit) / 2f
                WaveformBand.Heights.forEachIndexed { i, height ->
                    val x = WaveformBand.barLeft(i, size.width, barWidth)
                    val scale = 1f + (WaveformMotion.liveScale(clock.longValue, i % 4) - 1f) * live.value
                    val h = height * WaveformBand.HEIGHT_SCALE * scale * unit
                    val played = WaveformBand.isPlayed(x + barWidth / 2f, size.width, animated.value)
                    drawRect(if (played) WaveformColors.Text else WaveformColors.BandTrack, Offset(x, middle - h / 2f), Size(barWidth, h))
                }
            },
    )
}

/** The band's geometry from the approved design: 34dp tall, forty 3dp bars across its width. */
internal object WaveformBand {
    const val HEIGHT = 34
    const val BAR_WIDTH = 3f

    /** The bars are the design's heights at 64%, so the tallest stays inside the band. */
    const val HEIGHT_SCALE = 0.64f

    /** Each bar's height in dp before scaling. */
    val Heights = listOf(
        8f, 12f, 8f, 18f, 10f, 24f, 14f, 30f, 20f, 34f, 26f, 36f, 30f, 22f, 32f, 36f, 28f, 18f, 26f, 34f,
        30f, 20f, 14f, 24f, 16f, 28f, 12f, 20f, 10f, 16f, 8f, 14f, 10f, 18f, 8f, 12f, 6f, 10f, 6f, 8f,
    )

    /** The left edge of bar [index], spreading the bars from edge to edge of [width]. */
    fun barLeft(index: Int, width: Float, barWidth: Float): Float = index * (width - barWidth) / (Heights.size - 1)

    /** A bar is played once the listening point reaches its centre; nothing is played at 0. */
    fun isPlayed(centre: Float, width: Float, progress: Float): Boolean = progress > 0f && width > 0f && centre / width <= progress
}

@Composable
private fun SpotlightActions(spotlight: BrandedSpotlight) {
    val state = spotlight.actions
    val callbacks = spotlight.callbacks
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpotlightToggle(
            active = state.isLiked,
            icon = if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = stringResource(if (state.isLiked) R.string.episode_info_unlike else R.string.episode_info_like),
            onClick = callbacks.onLike,
        )
        SpotlightToggle(
            active = state.isDownloaded,
            icon = if (state.isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
            label = stringResource(if (state.isDownloaded) R.string.episode_info_remove_download else R.string.episode_info_download),
            onClick = callbacks.onDownload,
            loading = state.isDownloading,
        )
        SpotlightToggle(
            active = state.isQueued,
            icon = if (state.isQueued) Icons.AutoMirrored.Rounded.PlaylistAddCheck else Icons.AutoMirrored.Rounded.PlaylistAdd,
            label = stringResource(if (state.isQueued) R.string.episode_info_remove_queue else R.string.episode_info_add_queue),
            onClick = callbacks.onQueue,
        )
        val playLabel = stringResource(
            when {
                state.isPlaying -> R.string.episode_info_pause
                state.isResume -> R.string.episode_info_resume
                else -> R.string.episode_info_play
            },
        )
        Surface(
            onClick = callbacks.onPlay,
            shape = RoundedCornerShape(23.dp),
            color = WaveformColors.Text,
            contentColor = WaveformColors.Page,
            modifier = Modifier.weight(1f).height(46.dp).semantics { contentDescription = playLabel },
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(playLabel, style = brandedSansStyle(16.sp, 20.sp, weight = 500), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SpotlightToggle(active: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, loading: Boolean = false) {
    val debouncedClick = rememberPodcastEpisodeControlClick(onClick)
    val downloading = stringResource(R.string.episode_info_downloading)
    Surface(
        onClick = debouncedClick,
        enabled = !loading,
        shape = RoundedCornerShape(23.dp),
        color = if (active) WaveformColors.Text else WaveformColors.CardControl,
        contentColor = if (active) WaveformColors.Page else WaveformColors.Text,
        modifier = Modifier.size(width = 48.dp, height = 46.dp).semantics {
            contentDescription = label
            if (loading) stateDescription = downloading
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                BoxLoreLoader.CircularWavy(size = 20.dp, color = WaveformColors.Text)
            } else {
                Icon(icon, null, Modifier.size(22.dp))
            }
        }
    }
}
