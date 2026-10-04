package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private const val AuroraSweepDurationMillis = 20_000
private val LoreAuroraColours = listOf(
    Color(0xFF4285F4),
    Color(0xFF8AB4F8),
    Color(0xFF9B72CB),
    Color(0xFFEA4335),
    Color(0xFFFBBC04),
    Color(0xFF34A853),
    Color(0xFF4285F4),
).map { it.copy(alpha = 0.44f) }

/** The original multicolour sweep, clipped by the single solid selection Surface. */
@Composable
internal fun FloatingNavigationIndicatorAurora(indicatorIndex: State<Float>, active: Boolean) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (isActive) {
                val duration = ((1f - phase.value) * AuroraSweepDurationMillis).roundToInt().coerceAtLeast(1)
                phase.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val sweep = Brush.linearGradient(
                colors = LoreAuroraColours,
                start = Offset(-size.width, size.height),
                end = Offset(size.width, 0f),
            )
            val bloom = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                center = Offset.Zero,
                radius = size.minDimension * 0.95f,
            )
            onDrawBehind {
                val blend = floatingNavigationLoreAuroraBlend(indicatorIndex.value)
                if (blend > 0f) {
                    // Ease the original back-and-forth sweep through its turnarounds.
                    val progress = (1f - cos(phase.value * 2f * PI.toFloat())) * 0.5f
                    val shift = progress * size.width
                    translate(left = shift) {
                        drawRect(sweep, topLeft = Offset(-shift, 0f), size = size, alpha = blend)
                    }
                    val bloomX = size.width * (0.22f + progress * 0.56f)
                    val bloomY = size.height * 0.5f
                    translate(left = bloomX, top = bloomY) {
                        drawRect(bloom, topLeft = Offset(-bloomX, -bloomY), size = size, alpha = blend)
                    }
                }
            }
        },
    )
}
