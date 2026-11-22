package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Overlay coordinates include system navigation even when the bar handles its own insets. */
fun appMiniPlayerTopOffset(style: NavigationStyle, windowHeight: Dp, systemNavigationInset: Dp): Dp =
    (
        windowHeight - appBottomChromeContentPadding(style, isMiniPlayerVisible = true, systemNavigationInset = systemNavigationInset)
        ).coerceAtLeast(0.dp)

/** The widest the floating bar and the mini player get, so they stay a centred bar on wide, short windows. */
val AppFloatingChromeMaxWidth = 560.dp

/**
 * How far the floating bar and the mini player sit in from each physical side, beyond their own
 * 16dp gutter: the side system inset (camera cutout, 3-button navigation), plus half of any width
 * past [AppFloatingChromeMaxWidth], so both centre in the safe area. Portrait phones get (0, 0).
 */
fun floatingChromeSideOffsets(windowWidth: Dp, leftInset: Dp, rightInset: Dp): Pair<Dp, Dp> {
    val left = leftInset.coerceAtLeast(0.dp)
    val right = rightInset.coerceAtLeast(0.dp)
    val available = windowWidth - left - right - AppNavigationBarHorizontalInset * 2
    val extra = ((available - AppFloatingChromeMaxWidth) / 2).coerceAtLeast(0.dp)
    return (left + extra) to (right + extra)
}
