package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.designsystem.theme.ArtworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.artworkPlayerColorScheme
import cx.aswin.boxlore.core.designsystem.theme.cachedArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.loadArtworkPalette

/**
 * The player's Material scheme, built from the cover's seed ([artworkPlayerColorScheme]) that the
 * shared artwork picker also gives podcast and episode pages and Home's hero cards. A cover already
 * read colours the player from the first frame, and switching light/dark only rebuilds the scheme.
 * Falls back to the ambient [MaterialTheme.colorScheme] until the palette is ready or when the art
 * has no meaningful colour.
 *
 * The art loads through Coil with its disk cache, so after process death the player can recolour
 * without waiting on the network.
 */
@Composable
fun rememberPlayerColorScheme(imageUrl: String?): ColorScheme = rememberPlayerColors(imageUrl).scheme

/** The full player's scheme and the mini player's colours, from one read of the cover. */
data class PlayerColors(val scheme: ColorScheme, val mini: ArtworkMiniPlayerColors)

/** [rememberPlayerColorScheme] plus the mini player's colours, taken from the same scheme. */
@Composable
fun rememberPlayerColors(imageUrl: String?): PlayerColors {
    val context = LocalContext.current
    val surfaceStyle = LocalSurfaceStyle.current
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    val ambientScheme = MaterialTheme.colorScheme
    val url = imageUrl?.trim().orEmpty()
    var palette by remember(url) { mutableStateOf(url.takeIf(String::isNotEmpty)?.let(::cachedArtworkPalette)) }
    LaunchedEffect(url) {
        if (url.isNotEmpty() && palette == null) palette = runCatching { loadArtworkPalette(context, url) }.getOrNull()
    }
    return remember(palette, effectiveDarkTheme, surfaceStyle, ambientScheme) {
        val scheme = palette?.let { artworkPlayerColorScheme(it, effectiveDarkTheme, surfaceStyle) } ?: ambientScheme
        PlayerColors(scheme, ArtworkMiniPlayerColors.fromScheme(scheme))
    }
}

/**
 * Immersive full-player background: a vertical artwork-tinted gradient with a soft radial glow
 * behind the hero area. Respects AMOLED/surface styles because the scheme itself is produced by
 * the brand scheme generator.
 */
fun Modifier.playerCanvas(scheme: ColorScheme): Modifier = drawBehind {
    val top = scheme.primaryContainer.copy(alpha = 0.65f).compositeOver(scheme.surface)
    val mid = scheme.primaryContainer.copy(alpha = 0.35f).compositeOver(scheme.surface)
    val bottom = scheme.primaryContainer.copy(alpha = 0.12f).compositeOver(scheme.surface)

    drawRect(
        brush = Brush.verticalGradient(
            0f to top,
            0.55f to mid,
            1f to bottom,
        ),
    )

    // Soft glow behind the hero (upper third of the canvas)
    val glowCentre = Offset(size.width / 2f, size.height * 0.28f)
    val glowRadius = size.width * 0.85f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(scheme.primary.copy(alpha = 0.20f), Color.Transparent),
            center = glowCentre,
            radius = glowRadius,
        ),
        center = glowCentre,
        radius = glowRadius,
    )
}
