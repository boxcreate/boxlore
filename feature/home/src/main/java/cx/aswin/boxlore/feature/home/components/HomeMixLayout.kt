package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One spacing grid for everything inside the Your shows surface, shared by loaded content and its
 * skeleton (including enlarged text). Headers, rows and the first mix card align to [ContentInset],
 * the same inset as the cover edges above them.
 */
internal object HomeMixLayout {
    val ContentInset = 16.dp
    val TopPadding = 12.dp
    val BottomPadding = 16.dp
    val HeaderHeight = 48.dp
    val HeaderGap = 12.dp
    val CardGap = 12.dp

    /** At narrow widths the card keeps its width and the next card shows at least this much. */
    val MinPeek = 8.dp
    val CardWidth = 310.dp
    val CardHeight = 142.dp
    val RailHeight = CardHeight
    val totalHeight = TopPadding + HeaderHeight + HeaderGap + RailHeight + BottomPadding

    /** The first card lines up with the header and the next one peeks past the edge. */
    fun railCardWidth(maxWidth: Dp): Dp = (maxWidth - ContentInset - CardGap - MinPeek).coerceAtMost(CardWidth)
}

@Composable
internal fun homeMixCardHeight() = HomeMixLayout.CardHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
