package cx.aswin.boxlore.feature.info.branded.waveform

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Path
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
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)

/**
 * The latest episode on a dark card: "LATEST" in red, the title in boxlore's font, and a waveform
 * for its progress, with a red marker where you are. Its bars move while it plays.
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
        Column(Modifier.padding(19.dp)) {
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
            Spacer(Modifier.height(8.dp))
            Text(episode.title, style = brandedSansStyle(20.sp, 26.sp, weight = 500), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            WaveformProgress(
                progress = spotlight.progress ?: if (spotlight.isCompleted) 1f else 0f,
                playing = spotlight.actions.isPlaying,
            )
            Spacer(Modifier.height(10.dp))
            SpotlightActions(spotlight)
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
 * Forty bars, lit up to where you are with a slanted red marker there. While the episode plays
 * the bars breathe on the logo's four beats.
 */
@Composable
private fun WaveformProgress(progress: Float, playing: Boolean) {
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
            .height(ProgressHeight)
            .progressSemantics(progress)
            .drawBehind {
                val unit = 1.dp.toPx()
                val barWidth = 3 * unit
                val step = (size.width - barWidth - 4 * unit) / (ProgressBars.size - 1)
                val markerWidth = 6 * unit
                val markerX = animated.value * (size.width - markerWidth)
                val middle = size.height / 2f
                ProgressBars.forEachIndexed { i, height ->
                    val x = 2 * unit + i * step
                    val scale = 1f + (WaveformMotion.liveScale(clock.longValue, i % 4) - 1f) * live.value
                    val h = height * scale * unit
                    val lit = x + barWidth / 2f <= markerX + markerWidth / 2f && animated.value > 0f
                    drawRect(if (lit) WaveformColors.Text else WaveformColors.Track, Offset(x, middle - h / 2f), Size(barWidth, h))
                }
                // The logo's slant: 6dp wide, its right side 3.2dp higher.
                val marker = Path().apply {
                    moveTo(markerX, middle - 2 * unit)
                    lineTo(markerX + markerWidth, middle - 5.2f * unit)
                    lineTo(markerX + markerWidth, middle + 4 * unit)
                    lineTo(markerX, middle + 7.2f * unit)
                    close()
                }
                drawPath(marker, WaveformColors.Red)
            },
    )
}

private val ProgressHeight = 36.dp

/** The bar heights from the design, in dp. */
private val ProgressBars = listOf(
    8f, 12f, 8f, 18f, 10f, 24f, 14f, 30f, 20f, 34f, 26f, 36f, 30f, 22f, 32f, 36f, 28f, 18f, 26f, 34f,
    30f, 20f, 14f, 24f, 16f, 28f, 12f, 20f, 10f, 16f, 8f, 14f, 10f, 18f, 8f, 12f, 6f, 10f, 6f, 8f,
)

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
