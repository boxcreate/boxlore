package cx.aswin.boxcast.feature.player.v2

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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.feature.player.formatTime
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * M3 Expressive wavy seekbar:
 * - The played portion renders as an animated wave while playing; it flattens when paused/dragging.
 * - Chapter boundaries render as tick marks on the unplayed track.
 * - Dragging shows a floating preview pill with the target time and chapter title.
 * - The remaining-time label toggles between remaining and total duration on tap.
 */
@Composable
@Suppress("kotlin:S107", "kotlin:S3776")
fun PlayerSeekbar(
    positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    bufferedPositionFlow: kotlinx.coroutines.flow.Flow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    chapters: List<Chapter> = emptyList(),
    modifier: Modifier = Modifier
) {
    val position by positionFlow.collectAsState(initial = 0L)
    val bufferedPosition by bufferedPositionFlow.collectAsState(initial = 0L)
    val haptics = LocalHapticFeedback.current

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var showTotalDuration by rememberSaveable { mutableStateOf(false) }

    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction = (dragFraction ?: (position.toFloat() / duration)).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)

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
        // Floating seek/chapter preview pill
        AnimatedVisibility(
            visible = dragFraction != null,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            val fraction = dragFraction ?: 0f
            val seekTime = (fraction * duration).toLong()
            val matchingChapter = chapters.lastOrNull { (it.startTime * 1000).toLong() <= seekTime }
            val previewText = if (matchingChapter != null) {
                "${formatTime(seekTime)} • ${matchingChapter.title}"
            } else {
                formatTime(seekTime)
            }
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(duration) {
                    detectTapGestures(onTap = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((fraction * duration).toLong())
                    })
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { fraction ->
                                onSeek((fraction * duration).toLong())
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            val activeColor = colorScheme.primary
            val inactiveColor = colorScheme.primary.copy(alpha = 0.18f)
            val bufferColor = colorScheme.primary.copy(alpha = 0.34f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val trackStroke = 5.dp.toPx()
                val waveAmplitude = 3.dp.toPx() * amplitudeFactor
                val wavelength = 36.dp.toPx()
                val thumbWidth = 5.dp.toPx()
                val thumbHeight = 24.dp.toPx()
                val gap = 7.dp.toPx()

                val thumbX = size.width * playedFraction
                val activeEnd = (thumbX - gap).coerceAtLeast(0f)
                val inactiveStart = (thumbX + gap).coerceAtMost(size.width)

                // Inactive (remaining) track
                if (inactiveStart < size.width) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(inactiveStart, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round
                    )
                    // Buffered overlay on the remaining track
                    val bufferEnd = size.width * bufferedFraction
                    if (bufferEnd > inactiveStart) {
                        drawLine(
                            color = bufferColor,
                            start = Offset(inactiveStart, centerY),
                            end = Offset(bufferEnd.coerceAtMost(size.width), centerY),
                            strokeWidth = trackStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Stop indicator dot at track end
                drawCircle(
                    color = activeColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(size.width - 2.5.dp.toPx(), centerY)
                )

                // Active (played) track — wavy while playing
                if (activeEnd > 0f) {
                    if (waveAmplitude > 0.15f) {
                        val path = Path()
                        var x = 0f
                        val step = 3f
                        path.moveTo(0f, centerY + waveAmplitude * sin(phase))
                        while (x < activeEnd) {
                            x = (x + step).coerceAtMost(activeEnd)
                            val y = centerY + waveAmplitude * sin((x / wavelength) * 2f * PI.toFloat() + phase)
                            path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = activeColor,
                            style = Stroke(width = trackStroke, cap = StrokeCap.Round)
                        )
                    } else {
                        drawLine(
                            color = activeColor,
                            start = Offset(0f, centerY),
                            end = Offset(activeEnd, centerY),
                            strokeWidth = trackStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // M3 discrete-slider chapter markers. Rounded dots sit directly on
                // the flat or wavy track and adapt contrast across played/unplayed areas.
                if (chapters.isNotEmpty()) {
                    chapters.forEach { chapter ->
                        val startTimeMs = (chapter.startTime * 1000).toLong()
                        if (startTimeMs > 0 && startTimeMs < duration + 3000) {
                            val pct = (startTimeMs.toFloat() / duration).coerceAtMost(0.985f)
                            if (abs(pct - playedFraction) > 0.015f) {
                                val x = size.width * pct
                                val isPlayedMarker = pct < playedFraction
                                val markerY = if (isPlayedMarker && waveAmplitude > 0.15f) {
                                    centerY + waveAmplitude *
                                        sin((x / wavelength) * 2f * PI.toFloat() + phase)
                                } else {
                                    centerY
                                }
                                drawCircle(
                                    color = if (isPlayedMarker) {
                                        colorScheme.onPrimary.copy(alpha = 0.92f)
                                    } else {
                                        colorScheme.primary.copy(alpha = 0.72f)
                                    },
                                    radius = 2.25.dp.toPx(),
                                    center = Offset(x, markerY)
                                )
                            }
                        }
                    }
                }

                // Thumb: M3 expressive vertical handle bar
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(
                        (thumbX - thumbWidth / 2f).coerceIn(0f, size.width - thumbWidth),
                        centerY - thumbHeight / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbWidth / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val previewPosition = dragFraction?.let { (it * duration).toLong() } ?: position
            Text(
                text = formatTime(previewPosition),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.primary.copy(alpha = 0.85f)
            )
            Text(
                text = if (showTotalDuration) {
                    formatTime(duration)
                } else {
                    "-" + formatTime((duration - previewPosition).coerceAtLeast(0))
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { showTotalDuration = !showTotalDuration }
                }
            )
        }
    }
}
