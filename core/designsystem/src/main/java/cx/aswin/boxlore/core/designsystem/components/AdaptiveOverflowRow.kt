package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

internal data class OverflowPacking(val visible: List<Int>, val remaining: List<Int>)

/** Widths come from real chips, so icons, selection, font scale and density all participate. */
internal fun packOverflowItems(widths: List<Int>, available: Int, gap: Int, moreWidth: Int, selected: Int?): OverflowPacking {
    fun width(indices: List<Int>) = indices.sumOf { widths[it] } + gap * (indices.size - 1).coerceAtLeast(0)
    val all = widths.indices.toList()
    if (width(all) <= available) return OverflowPacking(all, emptyList())
    val mandatory = listOfNotNull(all.firstOrNull(), selected?.takeIf { it in all && it != 0 })
    val ordered = mandatory + (all - mandatory.toSet())
    val visible = mandatory.toMutableList()
    for (index in ordered.drop(mandatory.size)) {
        val candidate = visible + index
        if (width(candidate) + gap + moreWidth > available) break
        visible += index
    }
    return OverflowPacking(visible, all - visible.toSet())
}

private class OverflowRowState {
    var remaining: List<Int> = emptyList()
}

/** A bounded root with a scroll fallback only when the required chips cannot fit. */
@Composable
fun AdaptiveOverflowRow(
    itemCount: Int,
    selectedIndex: Int?,
    onOverflow: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (Int) -> Unit,
    more: @Composable (() -> Unit) -> Unit,
    onWidthChanged: () -> Unit = {},
) {
    val state = remember { OverflowRowState() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        LaunchedEffect(availableWidth) { onWidthChanged() }
        SubcomposeLayout(Modifier.horizontalScroll(rememberScrollState())) { constraints ->
            val loose = Constraints(maxHeight = constraints.maxHeight)
            val chips = (0 until itemCount).map { index ->
                subcompose(index) { Box { item(index) } }.single().measure(loose)
            }
            val gap = 8.dp.roundToPx()
            val available = availableWidth.roundToPx()
            val widths = chips.map { it.width }
            val total = widths.sum() + gap * (chips.size - 1).coerceAtLeast(0)
            val overflow = if (total > available) {
                subcompose("more") { Box { more { onOverflow(state.remaining) } } }.single().measure(loose)
            } else {
                null
            }
            val packing = packOverflowItems(widths, available, gap, overflow?.width ?: 0, selectedIndex)
            state.remaining = packing.remaining
            val visible = packing.visible.map { chips[it] } + listOfNotNull(overflow)
            val rowWidth = visible.sumOf { it.width } + gap * (visible.size - 1).coerceAtLeast(0)
            val height = visible.maxOfOrNull { it.height } ?: 0
            layout(maxOf(available, rowWidth), height) {
                var x = 0
                visible.forEach { chip ->
                    chip.placeRelative(x, (height - chip.height) / 2)
                    x += chip.width + gap
                }
            }
        }
    }
}
