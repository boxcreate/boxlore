package cx.aswin.boxlore.feature.library

import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardSpec
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp

/**
 * How wide a Library grid screen's content is: the pane on compact windows, and on tablets the
 * pane up to the shared Grid width, so a grid's columns, its header and its bars share one width.
 */
@Composable
internal fun libraryGridWidthDp(): Float {
    val spec = boxloreCardSpec(LocalBoxloreWindowLayout.current, BoxloreCardFamily.Grid)
    return minOf(boxloreContentWidthDp(), spec.maximumWidthDp)
}
