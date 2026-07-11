package cx.aswin.boxcast.feature.player.v2.chrome

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

fun playerSheetShape(
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
    smoothness: Int = PlayerChromeGeometry.SquircleSmoothnessPercent,
): Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = topStart,
    smoothnessAsPercentTL = smoothness,
    cornerRadiusTR = topEnd,
    smoothnessAsPercentTR = smoothness,
    cornerRadiusBL = bottomStart,
    smoothnessAsPercentBL = smoothness,
    cornerRadiusBR = bottomEnd,
    smoothnessAsPercentBR = smoothness,
)

fun artworkSquircleShape(corner: Dp = PlayerChromeGeometry.ArtworkCornerFull): Shape =
    playerSheetShape(corner, corner, corner, corner)
