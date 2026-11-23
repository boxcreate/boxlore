package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable

private const val OWNER_EDGE_TOLERANCE_DP = 1f

/**
 * True when the content here fills the top of the screen, so it may set the status bar's icon
 * colour: there are no shell pane bounds (phones), or the pane starts at the top of the window and
 * spans its width beside the navigation rail. A pane beside another one, or the lower half in
 * tabletop, leaves the status bar to the app.
 */
@Composable
fun boxlorePaneOwnsStatusBar(): Boolean = paneOwnsStatusBar(LocalBoxlorePaneBounds.current, LocalBoxloreWindowLayout.current, RAIL_WIDTH_DP)

/** [boxlorePaneOwnsStatusBar] for [pane] in [window] with a rail [railWidthDp] wide. */
fun paneOwnsStatusBar(pane: BoxloreLayoutBounds?, window: BoxloreWindowLayout, railWidthDp: Float): Boolean {
    if (pane == null || !window.isSpecified) return true
    val atTop = pane.top <= OWNER_EDGE_TOLERANCE_DP
    val fromRail = pane.left <= railWidthDp + OWNER_EDGE_TOLERANCE_DP
    val toEnd = pane.right >= window.widthDp - OWNER_EDGE_TOLERANCE_DP
    return atTop && fromRail && toEnd
}
