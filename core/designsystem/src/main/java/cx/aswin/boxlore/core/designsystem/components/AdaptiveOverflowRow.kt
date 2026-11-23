package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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

/**
 * What an [AdaptiveOverflowRow] could not fit. [remaining] holds the item indices behind More, in
 * their original order, and updates on every measure, so a More sheet that reads it stays open and
 * follows the row when the window changes size.
 */
@Stable
class AdaptiveOverflowState internal constructor() {
    var remaining: List<Int> by mutableStateOf(emptyList())
        internal set
}

/** Keeps one [AdaptiveOverflowState] for the row across window changes. */
@Composable
fun rememberAdaptiveOverflowState(): AdaptiveOverflowState = remember { AdaptiveOverflowState() }

/**
 * A chip row packed to its width, with More for the items that don't fit and a scroll fallback only
 * when the required chips (the first and the selected one) can't fit.
 *
 * A More sheet should show [AdaptiveOverflowState.remaining] while the row overflows and the full
 * list on compact windows or once nothing overflows, and stay open while the window changes size.
 * [onOverflow] gets the items behind More when it is tapped; [onOverflowChanged] gets them whenever
 * the row repacks.
 */
@Composable
@Suppress("LongParameterList") // Optional state and repack callback sit beside the existing parameters.
fun AdaptiveOverflowRow(
    itemCount: Int,
    selectedIndex: Int?,
    onOverflow: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    state: AdaptiveOverflowState = rememberAdaptiveOverflowState(),
    onOverflowChanged: (List<Int>) -> Unit = {},
    item: @Composable (Int) -> Unit,
    more: @Composable (() -> Unit) -> Unit,
) {
    OverflowRow(itemCount, selectedIndex, onOverflow, modifier, state, onOverflowChanged, item, more, onWidthChanged = {})
}

/** The previous signature, which closed the More sheet on every width change. */
@Deprecated(
    "Read rememberAdaptiveOverflowState().remaining and keep the More sheet open across resizes.",
    ReplaceWith("AdaptiveOverflowRow(itemCount, selectedIndex, onOverflow, modifier, item = item, more = more)"),
)
@Composable
fun AdaptiveOverflowRow(
    itemCount: Int,
    selectedIndex: Int?,
    onOverflow: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (Int) -> Unit,
    more: @Composable (() -> Unit) -> Unit,
    onWidthChanged: () -> Unit,
) {
    OverflowRow(itemCount, selectedIndex, onOverflow, modifier, rememberAdaptiveOverflowState(), {}, item, more, onWidthChanged)
}

@Composable
@Suppress("LongParameterList") // Shared body of the current and the deprecated signature.
private fun OverflowRow(
    itemCount: Int,
    selectedIndex: Int?,
    onOverflow: (List<Int>) -> Unit,
    modifier: Modifier,
    state: AdaptiveOverflowState,
    onOverflowChanged: (List<Int>) -> Unit,
    item: @Composable (Int) -> Unit,
    more: @Composable (() -> Unit) -> Unit,
    onWidthChanged: () -> Unit,
) {
    val currentOnOverflowChanged by rememberUpdatedState(onOverflowChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.remaining }.collect { currentOnOverflowChanged(it) }
    }
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
