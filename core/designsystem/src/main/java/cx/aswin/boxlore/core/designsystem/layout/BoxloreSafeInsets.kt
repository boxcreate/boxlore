package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/**
 * Pads only the start and end of this content by [insets]: the camera cutout and side system
 * buttons on a sideways phone. Use it on controls and text; full-bleed artwork keeps the edge.
 */
@Composable
fun Modifier.boxloreHorizontalSafePadding(insets: WindowInsets = WindowInsets.safeDrawing): Modifier =
    windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))

/**
 * The start and end safe-drawing insets of this pane, in layout direction. A pane side that doesn't
 * touch the window edge reports 0. Portrait phones report (0, 0).
 */
@Composable
fun boxloreHorizontalSafeInsetsDp(): Pair<Dp, Dp> {
    val direction = LocalLayoutDirection.current
    val padding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val pane = LocalBoxlorePaneBounds.current
    val window = LocalBoxloreWindowLayout.current
    val leftTouches = pane == null || !window.isSpecified || pane.left <= EDGE_TOLERANCE_DP
    val rightTouches = pane == null || !window.isSpecified || pane.right >= window.widthDp - EDGE_TOLERANCE_DP
    val startTouches = if (direction == LayoutDirection.Ltr) leftTouches else rightTouches
    val endTouches = if (direction == LayoutDirection.Ltr) rightTouches else leftTouches
    val start = if (startTouches) padding.calculateStartPadding(direction) else 0.dp
    val end = if (endTouches) padding.calculateEndPadding(direction) else 0.dp
    return start to end
}

/** [base] with this pane's start and end safe insets added, for a lazy list's or grid's `contentPadding`. */
@Composable
fun boxloreHorizontalSafeContentPadding(base: PaddingValues = PaddingValues()): PaddingValues {
    val direction = LocalLayoutDirection.current
    val (start, end) = boxloreHorizontalSafeInsetsDp()
    return PaddingValues(
        start = base.calculateStartPadding(direction) + start,
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(direction) + end,
        bottom = base.calculateBottomPadding(),
    )
}

/**
 * Treats the system bars, camera cutout and keyboard between [bounds] and each window edge as
 * already cleared, as padding filling that gap would. Descendants that pad by insets
 * (`windowInsetsPadding`, `imePadding`, `statusBarsPadding`) then pad only by the part that reaches
 * into [bounds]: nothing below a tabletop hinge for the status bar, the keyboard minus the space
 * under a pane that ends above the bottom. Edges on the window edge keep all of their insets.
 */
fun Modifier.consumeInsetsOutside(bounds: BoxloreLayoutBounds, window: BoxloreWindowLayout): Modifier {
    val right = if (window.isSpecified) window.widthDp - bounds.right else 0f
    val bottom = if (window.isSpecified) window.heightDp - bounds.bottom else 0f
    val gaps = listOf(bounds.left, bounds.top, right, bottom).map { gap -> if (gap > EDGE_TOLERANCE_DP) gap.dp else 0.dp }
    if (gaps.all { it == 0.dp }) return this
    return consumeWindowInsets(WindowInsets(left = gaps[0], top = gaps[1], right = gaps[2], bottom = gaps[3]))
}

/** Pane bounds within this of a window edge count as touching it. */
internal const val EDGE_TOLERANCE_DP = 0.5f
