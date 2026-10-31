package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun Modifier.shimmerEffect(): Modifier = m3Shimmer(Color(0xFFE0E0E0), Color(0xFFF0F0F0))

/**
 * A loading block with a moving highlight. Each block is a draw-only node, so a screen full of
 * skeletons adds no composition work. Inside a [ShimmerScope] every block follows the shared clock;
 * outside one, the block runs its own.
 */
fun Modifier.m3Shimmer(
    baseColor: Color = Color.Gray.copy(alpha = 0.2f),
    highlightColor: Color = Color.Gray.copy(alpha = 0.4f),
    shape: Shape = RectangleShape,
): Modifier = this.clip(shape).then(ShimmerElement(baseColor, highlightColor))

private data class ShimmerElement(val base: Color, val highlight: Color) : ModifierNodeElement<ShimmerNode>() {
    override fun create() = ShimmerNode(base, highlight)

    override fun update(node: ShimmerNode) = node.update(base, highlight)

    override fun InspectorInfo.inspectableProperties() {
        name = "m3Shimmer"
        properties["baseColor"] = base
        properties["highlightColor"] = highlight
    }
}

private class ShimmerNode(private var base: Color, private var highlight: Color) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val ownPhase = mutableFloatStateOf(0f)
    private var ownClock: Job? = null
    private var brush: Brush? = null
    private var brushWidth = Float.NaN

    fun update(base: Color, highlight: Color) {
        if (base == this.base && highlight == this.highlight) return
        this.base = base
        this.highlight = highlight
        brush = null
        invalidateDraw()
    }

    /**
     * Follows the [ShimmerScope]'s shared clock when there is one and runs its own otherwise. It is
     * checked while drawing, which runs again when the shared clock comes or goes, so a block attached
     * before or after its scope switches clocks instead of keeping the one it was attached with.
     */
    private fun followPhase(): State<Float>? {
        val shared = currentValueOf(LocalShimmerPhase)
        if (shared != null) {
            ownClock?.cancel()
            ownClock = null
        } else if (ownClock == null) {
            ownClock = runOwnClock()
        }
        return shared
    }

    private fun runOwnClock(): Job {
        // An infinite-animation frame, like an infinite transition, so tests can still go idle. It
        // follows the system animation speed and holds still while animations are off.
        return coroutineScope.launch {
            val motionScale = coroutineContext[MotionDurationScale]
            var start = withInfiniteAnimationFrameMillis { it }
            var previousScale = motionScale?.scaleFactor ?: 1f
            while (isActive) {
                withInfiniteAnimationFrameMillis { now ->
                    val scale = motionScale?.scaleFactor ?: 1f
                    if (scale != previousScale) {
                        start = now
                        previousScale = scale
                    }
                    ownPhase.floatValue = if (scale == 0f) 0f else (((now - start) / scale) % ShimmerCycleMillis) / ShimmerCycleMillis
                }
                if (previousScale == 0f) {
                    snapshotFlow { motionScale?.scaleFactor ?: 1f }.first { it > 0f }
                }
            }
        }
    }

    override fun onDetach() {
        // The node's coroutine scope is cancelled on detach; a later attach starts a fresh clock.
        ownClock = null
    }

    override fun ContentDrawScope.draw() {
        val width = size.width
        val sweep = brush?.takeIf { brushWidth == width } ?: sweepBrush(width).also {
            brush = it
            brushWidth = width
        }
        drawRect(color = base)
        // Start with a visible band, including short cache-loading sessions.
        val phase = followPhase()?.value ?: ownPhase.floatValue
        val offset = -width + ((phase + 0.25f) % 1f) * (2f * width)
        translate(left = offset) {
            // Move the shader, keeping its drawing rectangle over the entire
            // block. Moving both exposes a hard edge in tall placeholders.
            drawRect(brush = sweep, topLeft = Offset(-offset, 0f), size = size)
        }
        drawContent()
    }

    private fun sweepBrush(width: Float): Brush {
        val glow = if (base.alpha >= 1f) highlight else shimmerOverlayColor(base, highlight)
        return Brush.linearGradient(
            colors = listOf(glow.copy(alpha = 0f), glow, glow.copy(alpha = 0f)),
            start = Offset.Zero,
            end = Offset(width, 0f),
        )
    }
}
