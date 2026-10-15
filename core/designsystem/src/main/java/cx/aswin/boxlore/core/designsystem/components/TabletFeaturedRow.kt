package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A featured card occupies two poster columns; subsequent cards start alongside it. */
@Composable
fun TabletFeaturedRow(columns: Int, featured: @Composable () -> Unit, posters: @Composable () -> Unit, horizontalSpacing: Dp = 16.dp) {
    Layout(content = {
        Box(Modifier.testTag("tablet_featured_card"), propagateMinConstraints = true) { featured() }
        Box(Modifier.testTag("tablet_featured_neighbors")) { posters() }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = horizontalSpacing.roundToPx()
        val count = columns.coerceAtLeast(3)
        val cell = ((constraints.maxWidth - gap * (count - 1)) / count).coerceAtLeast(1)
        val featuredWidth = cell * 2 + gap
        val neighborsWidth = (constraints.maxWidth - featuredWidth - gap).coerceAtLeast(1)
        val neighbors = measurables[1].measure(Constraints.fixedWidth(neighborsWidth))
        val featureConstraints = if (neighbors.height > 0) Constraints.fixed(featuredWidth, neighbors.height) else Constraints.fixedWidth(featuredWidth)
        val featuredCard = measurables[0].measure(featureConstraints)
        layout(constraints.maxWidth, maxOf(featuredCard.height, neighbors.height)) {
            featuredCard.placeRelative(0, 0)
            neighbors.placeRelative(featuredWidth + gap, 0)
        }
    }
}
