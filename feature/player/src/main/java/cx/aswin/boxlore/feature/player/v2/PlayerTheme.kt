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
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.generateBrandColorScheme
import cx.aswin.boxlore.feature.player.extractSeedColor

/**
 * Extracts an artwork-seeded Material color scheme for the player.
 * Falls back to the ambient [MaterialTheme.colorScheme] until extraction completes.
 */
@Composable
fun rememberPlayerColorScheme(imageUrl: String?): ColorScheme {
    val context = LocalContext.current
    val surfaceStyle = LocalSurfaceStyle.current
    val effectiveDarkTheme = LocalEffectiveDarkTheme.current

    var extracted by remember { mutableStateOf<ColorScheme?>(null) }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(100, 100))
            .allowHardware(false) // Required for Palette
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    )

    LaunchedEffect(imageUrl, painter.state, effectiveDarkTheme, surfaceStyle) {
        val painterState = painter.state
        if (painterState is AsyncImagePainter.State.Success) {
            val bitmap = (painterState.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val seedColor = extractSeedColor(bitmap)
                extracted = generateBrandColorScheme(seedColor, effectiveDarkTheme, surfaceStyle)
            }
        }
    }

    return extracted ?: MaterialTheme.colorScheme
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
