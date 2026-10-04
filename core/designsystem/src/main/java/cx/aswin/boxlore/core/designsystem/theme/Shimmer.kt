package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate

fun Modifier.shimmerEffect(): Modifier = m3Shimmer(Color(0xFFE0E0E0), Color(0xFFF0F0F0))

fun Modifier.m3Shimmer(
    baseColor: Color = Color.Gray.copy(alpha = 0.2f),
    highlightColor: Color = Color.Gray.copy(alpha = 0.4f),
    shape: Shape = RectangleShape,
): Modifier = composed {
    val phase = LocalShimmerPhase.current ?: rememberShimmerPhase()
    val highlight = if (baseColor.alpha >= 1f) highlightColor else shimmerOverlayColor(baseColor, highlightColor)
    this.clip(shape).drawWithCache {
        val width = size.width
        val brush = Brush.linearGradient(
            colors = listOf(highlight.copy(alpha = 0f), highlight, highlight.copy(alpha = 0f)),
            start = Offset.Zero,
            end = Offset(width, 0f),
        )
        onDrawBehind {
            drawRect(color = baseColor)
            // Start with a visible band, including short cache-loading sessions.
            val offset = -width + ((phase.value + 0.25f) % 1f) * (2f * width)
            translate(left = offset) {
                // Move the shader, keeping its drawing rectangle over the entire
                // block. Moving both exposes a hard edge in tall placeholders.
                drawRect(brush = brush, topLeft = Offset(-offset, 0f), size = size)
            }
        }
    }
}
