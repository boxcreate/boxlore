package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.floatingChromeSideOffsets

/**
 * How far the phone's mini player (and the floating bar's blur) sits in from the window's left and
 * right edges, beyond its own side gutter. The floating bar keeps clear of side system buttons and
 * the camera cutout and centres at most 560dp wide; Classic clears the same insets without a cap.
 * Portrait phones get (0, 0), so their chrome is unchanged.
 */
@Composable
internal fun phoneChromeSideOffsets(style: NavigationStyle, windowWidth: Dp): Pair<Dp, Dp> {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val left = with(density) { insets.getLeft(density, direction).toDp() }
    val right = with(density) { insets.getRight(density, direction).toDp() }
    return if (style == NavigationStyle.Floating) floatingChromeSideOffsets(windowWidth, left, right) else left to right
}
