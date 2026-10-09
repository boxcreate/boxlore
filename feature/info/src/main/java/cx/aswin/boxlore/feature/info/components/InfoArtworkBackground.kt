package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.feature.info.logic.artworkBackdropAlpha

/** The mask fades the already-blurred pixels to zero before the layer ends. */
@Composable
internal fun InfoArtworkBackground(
    imageUrl: String?,
    height: Dp,
    scrollOffset: Float,
    scrollFraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(height).graphicsLayer {
            translationY = -scrollOffset * 0.5f
            alpha = 0.48f * (1f - scrollFraction.coerceIn(0f, 1f))
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithCache {
            val fade = Brush.verticalGradient(
                colorStops = (0..24).map { step ->
                    val position = step / 24f
                    position to Color.White.copy(alpha = artworkBackdropAlpha(position))
                }.toTypedArray(),
            )
            onDrawWithContent {
                drawContent()
                drawRect(fade, blendMode = BlendMode.DstIn)
            }
        },
    ) {
        OptimizedImage(
            url = imageUrl,
            proxyWidth = 400,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                // Overscan keeps blur sampling away from the original image edges.
                scaleX = 1.15f
                scaleY = 1.3f
            }.blur(72.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            contentScale = ContentScale.Crop,
        )
    }
}
