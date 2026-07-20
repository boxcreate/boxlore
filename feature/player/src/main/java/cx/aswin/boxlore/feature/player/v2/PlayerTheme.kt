package cx.aswin.boxlore.feature.player.v2

import android.graphics.drawable.BitmapDrawable
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
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.generateBrandColorScheme
import cx.aswin.boxlore.feature.player.extractSeedColor

/**
 * Extracts an artwork-seeded Material color scheme for the player.
 * Falls back to the ambient [MaterialTheme.colorScheme] until extraction completes.
 *
 * Uses Coil [coil.ImageLoader.execute] (not an undisplayed AsyncImagePainter) so palette
 * extraction still runs after process death when the memory cache is cold. Disk cache
 * is enabled so a kill-from-recents reopen can reseed from disk without waiting on
 * network — matching how Explore extracts card accents.
 */
@Composable
fun rememberPlayerColorScheme(imageUrl: String?): ColorScheme {
    val context = LocalContext.current
    val surfaceStyle = LocalSurfaceStyle.current
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current
    val ambientScheme = MaterialTheme.colorScheme

    var extracted by remember(imageUrl, effectiveDarkTheme, surfaceStyle) {
        mutableStateOf<ColorScheme?>(null)
    }

    LaunchedEffect(imageUrl, effectiveDarkTheme, surfaceStyle) {
        if (imageUrl.isNullOrBlank()) {
            extracted = null
            return@LaunchedEffect
        }
        extracted =
            runCatching {
                val request =
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(Size(100, 100))
                        .allowHardware(false) // Required for Palette pixel reads
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                val result =
                    context.imageLoader.execute(request) as? SuccessResult
                        ?: return@runCatching null
                val bitmap =
                    (result.drawable as? BitmapDrawable)?.bitmap
                        ?: return@runCatching null
                val seedColor = extractSeedColor(bitmap)
                generateBrandColorScheme(seedColor, effectiveDarkTheme, surfaceStyle)
            }.getOrNull()
    }

    return extracted ?: ambientScheme
}

/**
 * Immersive full-player background: a vertical artwork-tinted gradient with a soft
 * radial glow behind the hero area. Respects AMOLED/surface styles because the
 * scheme itself is produced by [generateBrandColorScheme].
 */
fun Modifier.playerCanvas(scheme: ColorScheme): Modifier = drawBehind {
    val top = scheme.primaryContainer.copy(alpha = 0.65f).compositeOver(scheme.surface)
    val mid = scheme.primaryContainer.copy(alpha = 0.35f).compositeOver(scheme.surface)
    val bottom = scheme.primaryContainer.copy(alpha = 0.12f).compositeOver(scheme.surface)

    drawRect(
        brush = Brush.verticalGradient(
            0f to top,
            0.55f to mid,
            1f to bottom
        )
    )

    // Soft glow behind the hero (upper third of the canvas)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                scheme.primary.copy(alpha = 0.20f),
                Color.Transparent
            ),
            center = Offset(size.width / 2f, size.height * 0.28f),
            radius = size.width * 0.85f
        ),
        center = Offset(size.width / 2f, size.height * 0.28f),
        radius = size.width * 0.85f
    )
}

/** Flat sheet color used while collapsed (mini player) — matches the classic pill look. */
fun miniSheetColor(scheme: ColorScheme): Color = scheme.primaryContainer
