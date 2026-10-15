package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics

/** Screens already own system insets; reserve only the playback chrome above them. */
@Composable
fun boxloreContentBottomPadding(compact: Dp): Dp {
    if (!LocalBoxloreWindowLayout.current.usesTabletLayout) return compact
    return 24.dp + if (LocalBoxloreMiniPlayerVisible.current) navigationChromeMetrics(LocalNavigationStyle.current).miniPlayerHeight else 0.dp
}
