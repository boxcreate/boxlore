package cx.aswin.boxcast.feature.player.v2.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared geometry for player sheet, mini player, and bottom navigation bar. */
object PlayerChromeGeometry {
    val NavBarTopCornerRadius: Dp = 28.dp
    val NavBarContentHeight: Dp = 62.dp
    val MiniPlayerHeight: Dp = 72.dp
    val MiniPlayerCollapsedCorner: Dp = 32.dp
    val MiniPlayerDockedBottomCorner: Dp = 12.dp
    val MiniPlayerHorizontalInset: Dp = 12.dp
    val MiniPlayerBottomSpacer: Dp = 8.dp
    val SheetTopCornerExpanded: Dp = 0.dp
    val ArtworkCornerFull: Dp = 28.dp
    val QueueRowCorner: Dp = 16.dp
    val SheetOverlayTopCorner: Dp = 28.dp
    const val SquircleSmoothnessPercent: Int = 60
    const val SheetAnimationDurationMs: Int = 255
}
