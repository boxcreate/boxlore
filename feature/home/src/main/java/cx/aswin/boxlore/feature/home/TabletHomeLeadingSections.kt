package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** The same module owners keep their composition positions as the available pane changes. */
@Composable
internal fun TabletHomeLeadingSections(showLibrary: Boolean, library: @Composable () -> Unit, briefing: @Composable () -> Unit) {
    Layout(content = {
        Box(Modifier.testTag("tablet_home_library_section")) { if (showLibrary) library() }
        Box(Modifier.testTag("tablet_home_briefing_section")) { briefing() }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 24.dp.roundToPx()
        val available = constraints.maxWidth
        val wide = showLibrary && available >= 900.dp.roundToPx()
        val libraryWidth = minOf(720.dp.roundToPx(), if (wide) (available - gap) * 2 / 5 else available)
        val briefingWidth = minOf(720.dp.roundToPx(), if (wide) available - gap - libraryWidth else available)
        val children = measurables.mapIndexed { index, measurable -> measurable.measure(Constraints(maxWidth = if (index == 0) libraryWidth else briefingWidth)) }
        val height = if (wide) maxOf(children[0].height, children[1].height) else children.sumOf { it.height } + if (showLibrary) gap else 0
        layout(available, constraints.constrainHeight(height)) {
            if (wide) {
                val left = (available - libraryWidth - gap - briefingWidth) / 2
                children[0].placeRelative(left, 0)
                children[1].placeRelative(left + libraryWidth + gap, 0)
            } else {
                children[0].placeRelative((available - children[0].width) / 2, 0)
                children[1].placeRelative((available - children[1].width) / 2, children[0].height + if (showLibrary) gap else 0)
            }
        }
    }
}
