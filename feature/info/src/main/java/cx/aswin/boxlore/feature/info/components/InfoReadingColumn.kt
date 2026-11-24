package cx.aswin.boxlore.feature.info.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

/** The widest the show page's text, cards and controls get on a tablet pane. */
internal val InfoReadingMaxWidth = 720.dp

/**
 * The space each side of the show page's centred reading column on a tablet pane wider than
 * [InfoReadingMaxWidth]; 0 on compact windows and narrower panes. The cover backdrop and the top
 * bar's colour still span the whole pane: only controls, text and cards keep to the column.
 */
@Composable
internal fun infoReadingSidePadding(paneWidth: Dp): Dp {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) return 0.dp
    return infoReadingSide(paneWidth)
}

/** [infoReadingSidePadding]'s rule without the window check. */
internal fun infoReadingSide(paneWidth: Dp): Dp = ((paneWidth - InfoReadingMaxWidth) / 2).coerceAtLeast(0.dp)
