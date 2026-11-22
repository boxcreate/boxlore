package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Legacy configuration geometry, retained for screens whose phone layout used it. */
@Composable
fun boxloreConfigurationWindowSize(): DpSize {
    val window = LocalBoxloreWindowLayout.current
    if (window.isSpecified) return DpSize(window.configurationWidthDp.dp, window.configurationHeightDp.dp)
    // Isolated previews/tests have no app shell. Production reads only the shared model above.
    val configuration = LocalConfiguration.current
    return DpSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
}

/** Edge-to-edge geometry; the shell owns the measurement in the running app. */
@Composable
fun boxloreVisibleWindowSize(): DpSize {
    val window = LocalBoxloreWindowLayout.current
    if (window.isSpecified) return DpSize(window.widthDp.dp, window.heightDp.dp)
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

/**
 * The height this content has: the shell's pane when it supplied one (a tabletop half, the context
 * pane), otherwise the window. Use it, not the window, to fit content to a half-open fold.
 */
@Composable
fun boxloreContentHeightDp(): Float = LocalBoxlorePaneBounds.current?.height ?: boxloreVisibleWindowSize().height.value

/**
 * True in a short space, such as a sideways phone, a phone split-screen half or the lower half of a
 * half-open fold, so screens switch to their compact layouts at the same point. Portrait phones
 * and the Galaxy Z Fold 8 cover are not short.
 */
@Composable
fun boxloreIsShortPane(thresholdDp: Float = SHORT_PANE_HEIGHT_DP): Boolean = boxloreContentHeightDp() < thresholdDp

/**
 * The height a dialog or panel can use: the fold-safe region it opens in on a half-open fold, the
 * window otherwise. System bars and the panel's own margins still come off it.
 */
@Composable
fun boxloreAvailableHeightDp(): Float {
    val window = LocalBoxloreWindowLayout.current
    return foldSafeViewportBounds(window, LocalBoxlorePaneBounds.current)?.height ?: boxloreVisibleWindowSize().height.value
}

private const val SHORT_PANE_HEIGHT_DP = 480f
