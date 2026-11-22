package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * System-bar space measured from the pane a screen is in, not from the window. In the app shell a
 * pane edge that doesn't touch the window edge (the lower half in tabletop, a detail beside the
 * browsing pane) reserves nothing there; the shell consumes those insets too, so `windowInsetsPadding`
 * and `statusBarsPadding` agree. Without shell pane bounds (phones, previews) these are the window's.
 */

/**
 * True while the detail pane sits above chrome the shell already reserves: the phone bottom bar
 * and the system navigation below it. Bottom padding then leaves both out.
 */
val LocalBoxloreBottomChromeReserved = staticCompositionLocalOf { false }

/** The status bar and camera cutout at the top of this pane, or 0 when the pane starts below the top of the window. */
@Composable
fun boxlorePaneTopInset(): Dp {
    // Read the insets on every path, so the window keeps reporting them while this pane is away from the edge.
    val inset = paneTopInsets().asPaddingValues().calculateTopPadding()
    return if (paneStartsBelowWindowTop()) 0.dp else inset
}

/** The system navigation at the bottom of this pane, or 0 when the pane ends above the bottom of the window. */
@Composable
fun boxlorePaneBottomInset(): Dp {
    val inset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val pane = LocalBoxlorePaneBounds.current
    val window = LocalBoxloreWindowLayout.current
    val endsAboveBottom = pane != null && window.isSpecified && pane.bottom < window.heightDp - EDGE_TOLERANCE_DP
    return if (endsAboveBottom) 0.dp else inset
}

/** Status-bar padding for the top of this pane; nothing in a pane that starts below the top of the window. */
@Composable
fun Modifier.boxlorePaneStatusBarsPadding(): Modifier {
    val insets = paneTopInsets()
    return windowInsetsPadding(if (paneStartsBelowWindowTop()) NoInsets else insets)
}

@Composable
private fun paneTopInsets(): WindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)

@Composable
private fun paneStartsBelowWindowTop(): Boolean = (LocalBoxlorePaneBounds.current?.top ?: 0f) > EDGE_TOLERANCE_DP

private val NoInsets = WindowInsets(0, 0, 0, 0)

/** Pane bounds within this of a window edge count as touching it. */
internal const val EDGE_TOLERANCE_DP = 0.5f
