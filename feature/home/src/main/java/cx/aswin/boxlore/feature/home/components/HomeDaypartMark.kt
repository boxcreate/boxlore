package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import kotlin.math.cos
import kotlin.math.sin

/** Decorative daypart identity. The greeting remains the accessible heading. */
@Composable
internal fun HomeDaypartMark(daypart: ContentDaypart, fullyVisible: Boolean) {
    var hasAnimated by rememberSaveable(daypart) { mutableStateOf(false) }
    val progress = remember(daypart) { Animatable(if (hasAnimated) 1f else 0f) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = fullyVisible && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val ambientPhase = if (active && hasAnimated) rememberDaypartAmbientPhase(daypart) else remember { mutableStateOf(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val horizon = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(daypart, active) {
        if (active && !hasAnimated) {
            // If scrolling interrupted the reveal, save it for the next fully visible entry.
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1_000, easing = FastOutSlowInEasing))
            hasAnimated = true
        }
    }

    Box(
        modifier = Modifier.size(32.dp).testTag("home_daypart_mark").drawWithCache {
            val moon = crescentPath(size.width)
            val star = starPath(size.width)
            onDrawBehind {
                // Read the clock during drawing, without recomposing the heading.
                val amount = progress.value
                val phase = ambientPhase.value
                when (daypart) {
                    ContentDaypart.MORNING -> drawMorningMark(amount, phase * 28f, accent, horizon)
                    ContentDaypart.AFTERNOON -> drawAfternoonMark(amount, phase * 28f, accent)
                    ContentDaypart.EVENING -> drawEveningMark(amount, phase * 28f, accent, horizon)
                    ContentDaypart.LATE_NIGHT -> {
                        clipRect(right = size.width * amount) { drawPath(moon, accent) }
                        val reveal = ((amount - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        scale(1.25f - 0.55f * phase, pivot = Offset(size.width * 0.8f, size.width * 0.23f)) {
                            drawPath(star, accent, alpha = reveal * (1f - 0.8f * phase))
                        }
                        translate(left = size.width * 0.04f, top = size.width * 0.28f) {
                            scale(0.5f + 0.35f * phase, pivot = Offset(size.width * 0.8f, size.width * 0.23f)) {
                                drawPath(star, accent, alpha = reveal * (0.2f + 0.8f * phase))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun rememberDaypartAmbientPhase(daypart: ContentDaypart): State<Float> {
    val transition = rememberInfiniteTransition(label = "Daypart ambient motion")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (daypart == ContentDaypart.LATE_NIGHT) 1_400 else 2_400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "Sun rays or star brightness",
    )
}

private fun DrawScope.drawMorningMark(progress: Float, rotation: Float, accent: Color, horizon: Color) {
    val unit = size.width
    val center = Offset(unit * 0.5f, unit * (0.76f - 0.32f * progress))
    clipRect(bottom = unit * 0.73f) {
        drawCircle(accent, radius = unit * 0.17f, center = center)
        rotate(rotation, pivot = center) {
            drawSunRays(center, unit * 0.24f, unit * 0.31f, accent, ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f))
        }
    }
    drawHorizon(unit * 0.75f, horizon)
}

private fun DrawScope.drawAfternoonMark(progress: Float, rotation: Float, accent: Color) {
    val unit = size.width
    drawCircle(accent, radius = unit * 0.17f, center = center)
    rotate(rotation) {
        drawSunRays(center, unit * 0.24f, unit * (0.26f + 0.1f * progress), accent, 0.5f + 0.5f * progress)
    }
}

private fun DrawScope.drawEveningMark(progress: Float, rotation: Float, accent: Color, horizon: Color) {
    val unit = size.width
    val sun = Offset(unit * 0.5f, unit * (0.43f + 0.27f * progress))
    clipRect(bottom = unit * 0.67f) {
        drawCircle(accent, radius = unit * 0.19f, center = sun)
        rotate(rotation, pivot = sun) {
            drawSunRays(sun, unit * 0.26f, unit * 0.33f, accent, 1f - 0.4f * progress)
        }
    }
    drawHorizon(unit * 0.7f, horizon)
}

private fun DrawScope.drawSunRays(center: Offset, inner: Float, outer: Float, color: Color, alpha: Float) {
    repeat(8) { index ->
        val angle = index * Math.PI / 4.0
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(
            color = color,
            start = center + direction * inner,
            end = center + direction * outer,
            strokeWidth = size.width * 0.047f,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawHorizon(y: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(size.width * 0.2f, y),
        end = Offset(size.width * 0.8f, y),
        strokeWidth = size.width * 0.047f,
        cap = StrokeCap.Round,
        alpha = 0.6f,
    )
}

private fun crescentPath(unit: Float): Path {
    val disk = Path().apply { addOval(Rect(unit * 0.14f, unit * 0.2f, unit * 0.74f, unit * 0.8f)) }
    val cutout = Path().apply { addOval(Rect(unit * 0.33f, unit * 0.1f, unit * 0.85f, unit * 0.62f)) }
    return Path.combine(PathOperation.Difference, disk, cutout)
}

private fun starPath(unit: Float): Path = Path().apply {
    moveTo(unit * 0.8f, unit * 0.15f)
    lineTo(unit * 0.82f, unit * 0.21f)
    lineTo(unit * 0.88f, unit * 0.23f)
    lineTo(unit * 0.82f, unit * 0.25f)
    lineTo(unit * 0.8f, unit * 0.31f)
    lineTo(unit * 0.78f, unit * 0.25f)
    lineTo(unit * 0.72f, unit * 0.23f)
    lineTo(unit * 0.78f, unit * 0.21f)
    close()
}
