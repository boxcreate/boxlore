package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Two slots that sit above and below the hinge of a fold half-open like a laptop: [top] fills the
 * upper half and [bottom] the lower half, and neither crosses the hinge. On every other window it is
 * one column, [top] then [bottom]. Both slots keep their place in composition, so what they hold
 * (scroll, drafts, picks) stays when the device folds or unfolds.
 *
 * The split needs a bounded height; without one it stays a column.
 */
@Composable
fun BoxloreTabletopSplit(top: @Composable () -> Unit, bottom: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val window = LocalBoxloreWindowLayout.current
    val paneTop = LocalBoxlorePaneBounds.current?.top ?: 0f
    // Where this layout starts in the window, in px; the pane's top is the first guess.
    var originY by remember { mutableFloatStateOf(Float.NaN) }
    val hinge = window.tabletopFold?.bounds
    Layout(
        contents = listOf(top, bottom),
        modifier = modifier.onGloballyPositioned { originY = it.positionInWindow().y },
    ) { (topSlot, bottomSlot), constraints ->
        // The hinge's top and bottom in this layout's px, when it runs across the layout. The origin is
        // read only with a hinge, so moving this layout remeasures it only on a half-open fold.
        val split = hinge
            ?.let {
                val origin = if (originY.isNaN()) paneTop * density else originY
                (it.top * density - origin).roundToInt() to (it.bottom * density - origin).roundToInt()
            }
            ?.takeIf { (hingeTop, hingeBottom) -> constraints.hasBoundedHeight && hingeTop > 0 && hingeBottom < constraints.maxHeight }
        if (split != null) {
            placeAroundHinge(topSlot, bottomSlot, constraints, split.first, split.second)
        } else {
            placeInColumn(topSlot, bottomSlot, constraints)
        }
    }
}

private fun MeasureScope.placeAroundHinge(
    topSlot: List<Measurable>,
    bottomSlot: List<Measurable>,
    constraints: Constraints,
    hingeTop: Int,
    hingeBottom: Int,
): MeasureResult {
    val width = constraints.maxWidth
    val upper = topSlot.map { it.measure(Constraints(0, width, 0, hingeTop)) }
    val lower = bottomSlot.map { it.measure(Constraints(0, width, 0, constraints.maxHeight - hingeBottom)) }
    return layout(width, constraints.maxHeight) {
        upper.forEach { it.place(0, 0) }
        lower.forEach { it.place(0, hingeBottom) }
    }
}

private fun MeasureScope.placeInColumn(topSlot: List<Measurable>, bottomSlot: List<Measurable>, constraints: Constraints): MeasureResult {
    val loose = constraints.copy(minHeight = 0)
    val upper = topSlot.map { it.measure(loose) }
    val upperHeight = upper.maxOfOrNull { it.height } ?: 0
    val remaining = if (constraints.hasBoundedHeight) (constraints.maxHeight - upperHeight).coerceAtLeast(0) else Constraints.Infinity
    val lower = bottomSlot.map { it.measure(loose.copy(maxHeight = remaining)) }
    val lowerHeight = lower.maxOfOrNull { it.height } ?: 0
    val width = (upper + lower).maxOfOrNull { it.width }?.coerceIn(constraints.minWidth, constraints.maxWidth) ?: constraints.minWidth
    val height = (upperHeight + lowerHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width, height) {
        upper.forEach { it.place(0, 0) }
        lower.forEach { it.place(0, upperHeight) }
    }
}
