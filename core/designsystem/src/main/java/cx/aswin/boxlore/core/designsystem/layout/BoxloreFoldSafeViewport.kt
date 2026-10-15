package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Keeps portal/overlay content in the same contiguous region as hidden-navigation shell routes. */
@Composable
fun Modifier.boxloreFoldSafeViewport(): Modifier {
    val window = LocalBoxloreWindowLayout.current
    if (!window.usesTabletLayout || window.foldSafeRegions().size == 1) return this
    val spec = boxlorePaneSpec(window, false)
    val bounds = spec.primary.copy(left = spec.primary.left - spec.railWidthDp)
    return layout { measurable, constraints ->
        val left = (bounds.left * density).roundToInt().coerceIn(0, constraints.maxWidth)
        val top = (bounds.top * density).roundToInt().coerceIn(0, constraints.maxHeight)
        val width = (bounds.width * density).roundToInt().coerceIn(0, constraints.maxWidth - left)
        val height = (bounds.height * density).roundToInt().coerceIn(0, constraints.maxHeight - top)
        val child = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) { child.place(left, top) }
    }
}
