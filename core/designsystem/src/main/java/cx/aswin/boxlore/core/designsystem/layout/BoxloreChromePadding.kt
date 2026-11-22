package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics

/** Screens already own system insets; reserve only the playback chrome above them. */
@Composable
fun boxloreContentBottomPadding(compact: Dp): Dp {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) return compact
    return 24.dp + if (LocalBoxloreMiniPlayerVisible.current) navigationChromeMetrics(LocalNavigationStyle.current).miniPlayerHeight else 0.dp
}

/**
 * How far above the bottom of this pane a snackbar sits: clear of the bottom bar, the mini player
 * where it covers this pane and the system navigation, with a gap, plus [extra] for screen controls
 * such as Play all. Measured from the pane's bottom edge, so the host goes at the bottom of a
 * full-height container, not in a `Scaffold` snackbar slot (which adds the system bar again).
 */
@Composable
fun boxloreSnackbarBottomPadding(extra: Dp = 0.dp): Dp {
    val chrome = appBottomChromeContentPadding(LocalBoxloreMiniPlayerVisible.current, boxlorePaneBottomInset())
    val gap = if (LocalBoxloreWindowLayout.current.usesTabletLayout) TABLET_SNACKBAR_GAP else PHONE_SNACKBAR_GAP
    return chrome + gap + extra.coerceAtLeast(0.dp)
}

private val PHONE_SNACKBAR_GAP = 8.dp
private val TABLET_SNACKBAR_GAP = 24.dp
