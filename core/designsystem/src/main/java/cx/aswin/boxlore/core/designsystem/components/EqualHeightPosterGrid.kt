package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.ceil
import kotlin.math.max

/**
 * Two-column poster grid with equal intrinsic card heights (fixed title feet).
 * Does not stretch cells — pair with [FeedMediaCard] fixed 3-line feet for alignment.
 */
@Composable
fun EqualHeightPosterGrid(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    horizontalSpacing: Dp = FeedPosterSpacing.GridGap,
    verticalSpacing: Dp = FeedPosterSpacing.GridGap,
    content: @Composable () -> Unit,
) {
    val colCount = columns.coerceAtLeast(1)
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        if (constraints.maxWidth == 0 || measurables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val columnWidth =
            if (colCount == 1) {
                maxWidth
            } else {
                ((maxWidth - hGap * (colCount - 1)) / colCount).coerceAtLeast(0)
            }
        val childConstraints =
            Constraints(
                minWidth = columnWidth,
                maxWidth = columnWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
        val placeables = measurables.map { it.measure(childConstraints) }
        val itemHeights = placeables.map { it.height }
        val totalHeight = equalHeightPosterGridExtent(itemHeights, colCount, vGap)

        val width = maxWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            totalHeight.coerceIn(
                constraints.minHeight,
                if (constraints.maxHeight == Constraints.Infinity) totalHeight else constraints.maxHeight,
            )

        layout(width, height) {
            var y = 0
            val rowCount = ceil(placeables.size / colCount.toFloat()).toInt()
            for (row in 0 until rowCount) {
                var rowHeight = 0
                for (col in 0 until colCount) {
                    val index = row * colCount + col
                    if (index < placeables.size) {
                        rowHeight = max(rowHeight, placeables[index].height)
                    }
                }
                placeables.fastForEachIndexed { index, placeable ->
                    if (index / colCount == row) {
                        val col = index % colCount
                        val x = col * (columnWidth + hGap)
                        placeable.placeRelative(x, y)
                    }
                }
                y += rowHeight + vGap
            }
        }
    }
}

/** Total height of a packed [columns]-wide grid given measured item heights and vertical gap. */
fun equalHeightPosterGridExtent(
    itemHeights: List<Int>,
    columns: Int,
    verticalGap: Int,
): Int {
    if (itemHeights.isEmpty()) return 0
    val colCount = columns.coerceAtLeast(1)
    val rowCount = ceil(itemHeights.size / colCount.toFloat()).toInt()
    var total = 0
    for (row in 0 until rowCount) {
        var rowHeight = 0
        for (col in 0 until colCount) {
            val index = row * colCount + col
            if (index < itemHeights.size) {
                rowHeight = max(rowHeight, itemHeights[index])
            }
        }
        if (row > 0) total += verticalGap
        total += rowHeight
    }
    return total
}
