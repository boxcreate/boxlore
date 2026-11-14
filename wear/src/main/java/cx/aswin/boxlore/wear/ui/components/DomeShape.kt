package cx.aswin.boxlore.wear.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/** The measurements of a block that follows the top of a round screen. All values are in one unit. */
internal object DomeGeometry {
    /**
     * Half the width the dome has [y] below the top of a round screen of [radius], staying [gap]
     * inside the screen's edge. Past the middle of the screen the edge narrows again, but a dome
     * keeps its widest width from there down.
     */
    fun halfWidth(radius: Float, gap: Float, y: Float): Float {
        val reach = radius - gap
        if (reach <= 0f) return 0f
        val fromCentre = (radius - y).coerceIn(0f, reach)
        return sqrt(reach * reach - fromCentre * fromCentre)
    }
}

/**
 * The outline of a block that fills the top of a round screen, the way an edge button fills the
 * bottom: its top and sides follow the screen at an even gap, and its lower corners are rounded.
 * It grows downwards with its content. The block must span the screen's width and start at the
 * screen's top; the outline is fixed there, so the block keeps its shape while it scrolls.
 */
internal class DomeShape(
    private val screenRadius: Dp,
    private val gap: Dp = 4.dp,
    private val corner: Dp = 30.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { screenRadius.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val cornerPx = with(density) { corner.toPx() }.coerceAtMost(size.height / 2f)
        val centreX = size.width / 2f
        // The lower corners start where the dome is as wide as it gets above them.
        val half = DomeGeometry.halfWidth(radius, gapPx, size.height - cornerPx)
        val screen = Path().apply { addOval(Rect(Offset(centreX, radius), radius - gapPx)) }
        val body =
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(centreX - half, 0f, centreX + half, size.height),
                        topLeft = CornerRadius.Zero,
                        topRight = CornerRadius.Zero,
                        bottomRight = CornerRadius(cornerPx),
                        bottomLeft = CornerRadius(cornerPx),
                    ),
                )
            }
        return Outline.Generic(Path.combine(PathOperation.Intersect, screen, body))
    }
}

/** A dome on a round watch; on any other screen, an ordinary rounded card. */
@Composable
internal fun rememberDomeShape(): Shape {
    val configuration = LocalConfiguration.current
    val round = configuration.isScreenRound
    val screenRadius = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp / 2
    return remember(round, screenRadius) { if (round) DomeShape(screenRadius) else RoundedCornerShape(26.dp) }
}
