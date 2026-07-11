package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.feature.player.formatTime
import kotlinx.coroutines.flow.Flow
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs

private val previewPillShape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 16.dp,
    smoothnessAsPercentTL = 60,
    cornerRadiusTR = 16.dp,
    smoothnessAsPercentTR = 60,
    cornerRadiusBL = 16.dp,
    smoothnessAsPercentBL = 60,
    cornerRadiusBR = 16.dp,
    smoothnessAsPercentBR = 60,
)

@Composable
fun PlayerSeekBar(
    positionFlow: Flow<Long>,
    durationMs: Long,
    bufferedPositionFlow: Flow<Long>,
    onSeek: (Long) -> Unit,
    color: Color,
    chapters: List<Chapter> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return

    val position by positionFlow.collectAsState(initial = 0L)
    val bufferedPosition by bufferedPositionFlow.collectAsState(initial = 0L)
    val bufferedPercentage = (bufferedPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var dragValue by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = dragValue != null,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            dragValue?.let { value ->
                val seekTime = value.toLong()
                val matchingChapter = chapters.lastOrNull { (it.startTime * 1000).toLong() <= seekTime }
                val previewText = if (matchingChapter != null) {
                    "${formatTime(seekTime)} • ${matchingChapter.title}"
                } else {
                    formatTime(seekTime)
                }

                Surface(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .wrapContentSize(),
                    shape = previewPillShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedPercentage)
                        .fillMaxHeight()
                        .background(
                            color.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }

            Slider(
                value = dragValue ?: position.toFloat(),
                onValueChange = {
                    dragValue = it
                    onSeek(it.toLong())
                },
                onValueChangeFinished = {
                    dragValue = null
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = Color.Transparent,
                ),
            )

            if (chapters.isNotEmpty()) {
                val tickColor = MaterialTheme.colorScheme.surface
                val thumbTime = dragValue ?: position.toFloat()
                val thumbPct = thumbTime / durationMs.toFloat().coerceAtLeast(1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        chapters.forEach { chapter ->
                            val startTimeMs = (chapter.startTime * 1000).toLong()
                            if (startTimeMs > 0 && startTimeMs < durationMs + 3000) {
                                val pct = (startTimeMs.toFloat() / durationMs.toFloat()).coerceAtMost(0.985f)
                                if (abs(pct - thumbPct) > 0.015f) {
                                    val x = size.width * pct
                                    drawLine(
                                        color = tickColor,
                                        start = androidx.compose.ui.geometry.Offset(x = x, y = 0f),
                                        end = androidx.compose.ui.geometry.Offset(x = x, y = size.height),
                                        strokeWidth = 1.5.dp.toPx(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color.copy(alpha = 0.85f),
            )
            Text(
                text = "-" + formatTime((durationMs - position).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color.copy(alpha = 0.85f),
            )
        }
    }
}
