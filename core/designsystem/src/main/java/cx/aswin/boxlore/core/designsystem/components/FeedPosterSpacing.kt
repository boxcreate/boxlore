package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing for discovery poster cards (Home For You, Explore For You, rails). */
object FeedPosterSpacing {
    val GridGap = 16.dp
    val CardTextPadding = 12.dp

    /** 18dp line height reserved per title line in equal-height poster feet. */
    val TitleLineHeight = 18.dp

    fun textFootHeight(titleMaxLines: Int): Dp =
        TitleLineHeight * titleMaxLines.coerceAtLeast(1)
}
