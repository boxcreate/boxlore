package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.dp

/** Shared loaded and loading geometry prevents the mix rail growing during reveal. */
internal object HomeMixLayout {
    val VerticalPadding = 16.dp
    val HeaderHeight = 48.dp
    val HeaderGap = 12.dp
    val CardWidth = 176.dp
    val CardHeight = 224.dp
    val RailHeight = CardHeight + 8.dp
    val totalHeight = VerticalPadding * 2 + HeaderHeight + HeaderGap + RailHeight
}
