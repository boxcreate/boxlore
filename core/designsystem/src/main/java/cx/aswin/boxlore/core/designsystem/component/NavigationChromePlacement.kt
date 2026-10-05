package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Overlay coordinates include system navigation even when the bar handles its own insets. */
fun appMiniPlayerTopOffset(style: NavigationStyle, windowHeight: Dp, systemNavigationInset: Dp): Dp =
    (
        windowHeight - appBottomChromeContentPadding(style, isMiniPlayerVisible = true, systemNavigationInset = systemNavigationInset)
        ).coerceAtLeast(0.dp)
