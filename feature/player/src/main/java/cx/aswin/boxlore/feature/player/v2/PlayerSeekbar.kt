package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.feature.player.formatTime
import cx.aswin.boxlore.feature.player.v2.logic.chapterAtPosition
import cx.aswin.boxlore.feature.player.v2.logic.playbackFraction
import cx.aswin.boxlore.feature.player.v2.logic.seekPosition
import cx.aswin.boxlore.feature.player.v2.logic.seekPreviewText
import cx.aswin.boxlore.feature.player.v2.logic.seekbarThumbBounds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * M3 Expressive wavy seekbar:
 * - The played portion renders as an animated wave while playing; it flattens when paused/dragging.
 * - Chapter boundaries render as tick marks on the unplayed track.
 * - Dragging shows a floating preview pill with the target time and chapter title.
 * - The remaining-time label toggles between remaining and total duration on tap.
 */
data class PlayerProgressFlows(
    val position: kotlinx.coroutines.flow.Flow<Long>,
    val bufferedPosition: kotlinx.coroutines.flow.Flow<Long>
)

@Composable
fun PlayerSeekbar(
    progressFlows: PlayerProgressFlows,
    durationMs: Long,
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    chapters: List<Chapter> = emptyList(),
    modifier: Modifier = Modifier,
) = PlayerSeekbarContent(progressFlows, SeekbarPresentation(durationMs, isPlaying, colorScheme, chapters), onSeek, modifier)

@Composable
internal fun MinimalPlayerSeekbar(
    progressFlows: PlayerProgressFlows,
    durationMs: Long,
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    chapters: List<Chapter>,
) = PlayerSeekbarContent(progressFlows, SeekbarPresentation(durationMs, isPlaying, colorScheme, chapters, minimal = true), onSeek, Modifier)

private data class SeekbarPresentation(val durationMs: Long, val isPlaying: Boolean, val colors: ColorScheme, val chapters: List<Chapter>, val minimal: Boolean = false)

@Composable
private fun PlayerSeekbarContent(progressFlows: PlayerProgressFlows, model: SeekbarPresentation, onSeek: (Long) -> Unit, modifier: Modifier) {
    val durationMs = model.durationMs
    val isPlaying = model.isPlaying
    val colorScheme = model.colors
    val chapters = model.chapters
    val minimal = model.minimal
    val position by progressFlows.position.collectAsStateWithLifecycle(initialValue = 0L)
    val bufferedPosition by progressFlows.bufferedPosition.collectAsStateWithLifecycle(initialValue = 0L)
    val haptics = LocalHapticFeedback.current
    val seekDescription = stringResource(cx.aswin.boxlore.feature.player.R.string.player_seek)

    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction = dragFraction ?: playbackFraction(position, duration)
    val bufferedFraction = playbackFraction(bufferedPosition, duration)

    // Wave amplitude: full while playing, flat when paused or scrubbing
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && dragFraction == null) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "waveAmplitude"
    )
    val waveVisible = isPlaying && dragFraction == null
    val phaseAnimation = remember { Animatable(0f) }
    LaunchedEffect(waveVisible) {
        if (!waveVisible) return@LaunchedEffect
        while (isActive) {
            phaseAnimation.snapTo(0f)
            phaseAnimation.animateTo(
                targetValue = (2 * PI).toFloat(),
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
            )
        }
    }
    val phase = phaseAnimation.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SeekbarPreview(dragFraction, duration, chapters, colorScheme)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics {
                    contentDescription = seekDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(playedFraction, 0f..1f)
                    setProgress { fraction ->
                        if (durationMs <= 0L) {
                            false
                        } else {
                            onSeek(seekPosition(fraction, duration))
                            true
                        }
                    }
                }
                .seekbarGestures(durationMs, haptics, { dragFraction = it }, onSeek)
        ) {
            SeekbarTrack(
                state = SeekbarDrawState(
                    playedFraction = playedFraction,
                    bufferedFraction = bufferedFraction,
                    amplitudeFactor = amplitudeFactor,
                    phase = phase,
                    duration = duration,
                    chapters = chapters,
                    minimal = minimal,
                ),
                colorScheme = colorScheme
            )
        }

        if (!minimal) {
            SeekbarTimeLabels(dragFraction?.let { (it * duration).toLong() } ?: position, duration, colorScheme)
        }
    }
}

private fun Modifier.seekbarGestures(
    durationMs: Long,
    haptics: HapticFeedback,
    onPreview: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
): Modifier {
    if (durationMs <= 0L) return this
    return pointerInput(durationMs) {
        detectTapGestures { offset ->
            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSeek(seekPosition(fraction, durationMs))
        }
    }.pointerInput(durationMs) {
        var dragFraction: Float? = null
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                onPreview(dragFraction)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onHorizontalDrag = { change, _ ->
                change.consume()
                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                onPreview(dragFraction)
            },
            onDragEnd = {
                dragFraction?.let { fraction ->
                    onSeek(seekPosition(fraction, durationMs))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                dragFraction = null
                onPreview(null)
            },
            onDragCancel = {
                dragFraction = null
                onPreview(null)
            },
        )
    }
}

@Composable
private fun SeekbarPreview(dragFraction: Float?, duration: Long, chapters: List<Chapter>, colorScheme: ColorScheme) {
    AnimatedVisibility(
        visible = dragFraction != null,
        enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        val fraction = dragFraction ?: 0f
        val seekTime = seekPosition(fraction, duration)
        val previewText = seekPreviewText(seekTime, chapterAtPosition(chapters, seekTime))
        Surface(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .wrapContentSize(),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.primaryContainer,
            border = BorderStroke(1.dp, colorScheme.primary),
            shadowElevation = 6.dp
        ) {
            Text(
                text = previewText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
                color = colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeekbarTimeLabels(previewPosition: Long, duration: Long, colorScheme: ColorScheme) {
    var showTotalDuration by rememberSaveable { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(2.dp))

    // Time labels
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(previewPosition),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.semiBold),
            color = colorScheme.primary.copy(alpha = 0.85f)
        )
        Text(
            text = if (showTotalDuration) {
                formatTime(duration)
            } else {
                "-" + formatTime((duration - previewPosition).coerceAtLeast(0))
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.semiBold),
            color = colorScheme.primary.copy(alpha = 0.85f),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { showTotalDuration = !showTotalDuration }
            }
        )
    }
}

private data class SeekbarDrawState(
    val playedFraction: Float,
    val bufferedFraction: Float,
    val amplitudeFactor: Float,
    val phase: Float,
    val duration: Long,
    val chapters: List<Chapter>,
    val minimal: Boolean,
)

private data class SeekbarDrawMetrics(
    val centerY: Float,
    val trackStroke: Float,
    val waveAmplitude: Float,
    val wavelength: Float,
    val thumbWidth: Float,
    val thumbHeight: Float,
    val thumbX: Float,
    val activeEnd: Float,
    val inactiveStart: Float
)

@Composable
private fun SeekbarTrack(
    state: SeekbarDrawState,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val metrics = seekbarMetrics(state)
        drawInactiveTrack(state, metrics, colorScheme)
        drawStopIndicator(metrics, colorScheme)
        drawActiveTrack(state, metrics, colorScheme)
        drawChapterMarkers(state, metrics, colorScheme)
        drawThumb(metrics, colorScheme)
    }
}

private fun DrawScope.seekbarMetrics(state: SeekbarDrawState): SeekbarDrawMetrics {
    val centerY = size.height / 2f
    val thumbX = size.width * state.playedFraction
    val gap = 7.dp.toPx()
    return SeekbarDrawMetrics(
        centerY = centerY,
        trackStroke = (if (state.minimal) 2.5.dp else 5.dp).toPx(),
        waveAmplitude = 3.dp.toPx() * state.amplitudeFactor,
        wavelength = 36.dp.toPx(),
        thumbWidth = (if (state.minimal) 8.dp else 5.dp).toPx(),
        thumbHeight = (if (state.minimal) 8.dp else 24.dp).toPx(),
        thumbX = thumbX,
        activeEnd = (thumbX - gap).coerceAtLeast(0f),
        inactiveStart = (thumbX + gap).coerceAtMost(size.width)
    )
}

private fun DrawScope.drawInactiveTrack(
    state: SeekbarDrawState,
    metrics: SeekbarDrawMetrics,
    colorScheme: ColorScheme
) {
    if (metrics.inactiveStart >= size.width) return
    drawLine(
        color = colorScheme.primary.copy(alpha = 0.18f),
        start = Offset(metrics.inactiveStart, metrics.centerY),
        end = Offset(size.width, metrics.centerY),
        strokeWidth = metrics.trackStroke,
        cap = StrokeCap.Round
    )
    val bufferEnd = size.width * state.bufferedFraction
    if (bufferEnd <= metrics.inactiveStart) return
    drawLine(
        color = colorScheme.primary.copy(alpha = 0.34f),
        start = Offset(metrics.inactiveStart, metrics.centerY),
        end = Offset(bufferEnd.coerceAtMost(size.width), metrics.centerY),
        strokeWidth = metrics.trackStroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawStopIndicator(metrics: SeekbarDrawMetrics, colorScheme: ColorScheme) {
    val radius = 2.5.dp.toPx()
    drawCircle(
        color = colorScheme.primary,
        radius = radius,
        center = Offset(size.width - radius, metrics.centerY)
    )
}

private fun DrawScope.drawActiveTrack(
    state: SeekbarDrawState,
    metrics: SeekbarDrawMetrics,
    colorScheme: ColorScheme
) {
    if (metrics.activeEnd <= 0f) return
    if (metrics.waveAmplitude <= 0.15f) {
        drawLine(
            color = colorScheme.primary,
            start = Offset(0f, metrics.centerY),
            end = Offset(metrics.activeEnd, metrics.centerY),
            strokeWidth = metrics.trackStroke,
            cap = StrokeCap.Round
        )
        return
    }
    val path = Path()
    var x = 0f
    path.moveTo(0f, metrics.centerY + metrics.waveAmplitude * sin(state.phase))
    while (x < metrics.activeEnd) {
        x = (x + 3f).coerceAtMost(metrics.activeEnd)
        val y = metrics.centerY + metrics.waveAmplitude *
            sin((x / metrics.wavelength) * 2f * PI.toFloat() + state.phase)
        path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = colorScheme.primary,
        style = Stroke(width = metrics.trackStroke, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawChapterMarkers(
    state: SeekbarDrawState,
    metrics: SeekbarDrawMetrics,
    colorScheme: ColorScheme
) {
    state.chapters.forEach { chapter ->
        val startTimeMs = (chapter.startTime * 1000).toLong()
        if (startTimeMs <= 0 || startTimeMs >= state.duration + 3000) return@forEach
        val fraction = (startTimeMs.toFloat() / state.duration).coerceAtMost(0.985f)
        if (abs(fraction - state.playedFraction) <= 0.015f) return@forEach
        drawChapterMarker(fraction, state, metrics, colorScheme)
    }
}

private fun DrawScope.drawChapterMarker(
    fraction: Float,
    state: SeekbarDrawState,
    metrics: SeekbarDrawMetrics,
    colorScheme: ColorScheme
) {
    val x = size.width * fraction
    val isPlayed = fraction < state.playedFraction
    val markerY = if (isPlayed && metrics.waveAmplitude > 0.15f) {
        metrics.centerY + metrics.waveAmplitude *
            sin((x / metrics.wavelength) * 2f * PI.toFloat() + state.phase)
    } else {
        metrics.centerY
    }
    drawCircle(
        color = if (isPlayed) {
            colorScheme.onPrimary.copy(alpha = 0.92f)
        } else {
            colorScheme.primary.copy(alpha = 0.72f)
        },
        radius = 2.25.dp.toPx(),
        center = Offset(x, markerY)
    )
}

private fun DrawScope.drawThumb(metrics: SeekbarDrawMetrics, colorScheme: ColorScheme) {
    val thumb = seekbarThumbBounds(size.width, metrics.thumbWidth, metrics.thumbX)
    if (thumb.width <= 0f) return
    drawRoundRect(
        color = colorScheme.primary,
        topLeft = Offset(
            thumb.left,
            metrics.centerY - metrics.thumbHeight / 2f
        ),
        size = androidx.compose.ui.geometry.Size(thumb.width, metrics.thumbHeight),
        cornerRadius = CornerRadius(thumb.width / 2f)
    )
}
