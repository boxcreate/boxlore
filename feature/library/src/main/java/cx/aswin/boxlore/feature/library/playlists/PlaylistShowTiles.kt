package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val ShowTileSize = 100.dp
internal val ShowTileGap = 12.dp

/** How many show covers fit across [width] on a playlist's page (at least one). */
internal fun playlistShowColumns(width: Dp): Int = ((width + ShowTileGap) / (ShowTileSize + ShowTileGap)).toInt().coerceAtLeast(1)

/**
 * A show cover's size on a playlist's page: 100dp on phones (windows up to 430dp); on wider
 * windows the covers grow (up to 140dp) to fill the row instead of leaving an empty strip.
 */
internal fun playlistShowTileSize(rowWidth: Dp, windowWidthDp: Float): Dp {
    if (windowWidthDp <= PHONE_MAX_WIDTH_DP) return ShowTileSize
    val columns = playlistShowColumns(rowWidth)
    return ((rowWidth - ShowTileGap * (columns - 1)) / columns).coerceIn(ShowTileSize, ShowTileMaxSize)
}

private const val PHONE_MAX_WIDTH_DP = 430f
private val ShowTileMaxSize = 140.dp
