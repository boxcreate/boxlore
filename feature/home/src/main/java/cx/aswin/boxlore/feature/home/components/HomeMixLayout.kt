package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Loaded cards and skeletons reserve the same space, including enlarged text. */
internal object HomeMixLayout {
    val VerticalPadding = 12.dp
    val HeaderHeight = 48.dp
    val HeaderGap = 12.dp
    val CardWidth = 310.dp
    val CardHeight = 142.dp
    val RailHeight = CardHeight
    val totalHeight = VerticalPadding * 2 + HeaderHeight + HeaderGap + RailHeight
}

@Composable
internal fun homeMixCardHeight() = HomeMixLayout.CardHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
