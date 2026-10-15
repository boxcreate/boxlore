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
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.designsystem.theme.ArtworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.artworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.artworkPlayerColorScheme
import cx.aswin.boxlore.core.designsystem.theme.artworkSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.cachedArtworkPalette
import cx.aswin.boxlore.core.designsystem.theme.loadArtworkPalette

/**
 * The player uses a Material 3 tonal scheme seeded from the cover's accent ([artworkPlayerColorScheme],
 * with a lifted dark surface), read by the
 * same shared extractor as podcast and episode pages. Pages wear the cover's two colours as a tinted
 * ground; the player is denser, so it keeps Material's matched contrast pairs instead, and every
 * control reads the same on any cover. A cover already read colours the player from the first frame,
 * and switching light/dark only rebuilds the scheme. Falls back to the ambient
 * [MaterialTheme.colorScheme] until the palette is ready or when the art has no meaningful colour.
 *
 * The art loads through Coil with its disk cache, so after process death the player can recolour
 * without waiting on the network.
 */
@Composable
fun rememberPlayerColorScheme(imageUrl: String?): ColorScheme = rememberPlayerColors(imageUrl).scheme

/** The full player's scheme and the mini player's colours, from one read of the cover. */
data class PlayerColors(val scheme: ColorScheme, val mini: ArtworkMiniPlayerColors)

/**
 * [rememberPlayerColorScheme] plus the mini player's colours ([artworkMiniPlayerColors]): the bar
 * follows the cover's main colour, since it sits right next to the cover, and Play the accent.
 */
@Composable
fun rememberPlayerColors(imageUrl: String?): PlayerColors {
    val context = LocalContext.current
    val surfaceStyle = artworkSurfaceStyle(LocalSurfaceStyle.current)
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    val ambientScheme = MaterialTheme.colorScheme
    val url = imageUrl?.trim().orEmpty()
    var palette by remember(url) { mutableStateOf(url.takeIf(String::isNotEmpty)?.let(::cachedArtworkPalette)) }
    LaunchedEffect(url) {
        if (url.isNotEmpty() && palette == null) palette = runCatching { loadArtworkPalette(context, url) }.getOrNull()
    }
    return remember(palette, effectiveDarkTheme, surfaceStyle, ambientScheme) {
        val scheme = palette?.let { artworkPlayerColorScheme(it, effectiveDarkTheme, surfaceStyle) } ?: ambientScheme
        PlayerColors(scheme, palette?.let { artworkMiniPlayerColors(it, effectiveDarkTheme) } ?: ArtworkMiniPlayerColors.fromScheme(scheme))
    }
}

/**
 * Full-player background: the scheme's flat surface, so the controls sit on one known tone and keep
 * Material's contrast pairs, with only a faint primary glow behind the artwork.
 */
fun Modifier.playerCanvas(scheme: ColorScheme): Modifier = drawBehind {
    drawRect(scheme.surface)
    val glowCentre = Offset(size.width / 2f, size.height * 0.28f)
    val glowRadius = size.width * 0.85f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(scheme.primary.copy(alpha = 0.10f), Color.Transparent),
            center = glowCentre,
            radius = glowRadius,
        ),
        center = glowCentre,
        radius = glowRadius,
    )
}
