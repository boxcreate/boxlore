package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

/**
 * A list with its Play All button. On tablets the button gets its own row under the list, so it
 * can't cover episode controls; on phones it floats over the list's bottom end. Both slots keep
 * their place in one layout either way, so the list's scroll and anything open inside it survive a
 * fold, an unfold or a resize across the tablet size.
 */
@Composable
internal fun LibraryPlaybackLayout(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit, playbackAction: @Composable BoxScope.() -> Unit) {
    val reserveRow = LocalBoxloreWindowLayout.current.usesTabletLayout
    Layout(
        contents = listOf({ Box(content = content) }, { Box(content = playbackAction) }),
        modifier = if (reserveRow) modifier.fillMaxSize() else modifier,
    ) { (contentSlot, actionSlot), constraints ->
        if (reserveRow) {
            val width = constraints.maxWidth
            val action = actionSlot.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
            val listHeight = (constraints.maxHeight - action.height).coerceAtLeast(0)
            val list = contentSlot.single().measure(Constraints.fixed(width, listHeight))
            layout(width, constraints.maxHeight) {
                list.place(0, 0)
                action.place(0, listHeight)
            }
        } else {
            val list = contentSlot.single().measure(constraints)
            val width = list.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = list.height.coerceIn(constraints.minHeight, constraints.maxHeight)
            // The button is drawn over the list, aligned within the whole area.
            val action = actionSlot.single().measure(Constraints.fixed(width, height))
            layout(width, height) {
                list.place(0, 0)
                action.place(0, 0)
            }
        }
    }
}
