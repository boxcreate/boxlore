package cx.aswin.boxcast.core.designsystem.components

import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Radio station fallback image — same visual language as AnimatedShapesFallback
 * but with a Radio icon instead of a Podcasts icon.
 */
@Composable
fun RadioShapesFallback() {
    LogRecomposition(name = "RadioShapesFallback")

    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val radioIcon = Icons.Rounded.Radio
    val iconColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)

    // Random positions calculated once per composition
    val shapes = remember {
        val allShapes = listOf(
            ExpressiveShapes.Sunny, ExpressiveShapes.VerySunny, 
            ExpressiveShapes.Cookie4, ExpressiveShapes.Cookie6, ExpressiveShapes.Cookie9, ExpressiveShapes.Cookie12,
            ExpressiveShapes.Burst, ExpressiveShapes.SoftBurst, ExpressiveShapes.Boom, ExpressiveShapes.SoftBoom,
            ExpressiveShapes.Flower, ExpressiveShapes.Puffy, ExpressiveShapes.PuffyDiamond,
            ExpressiveShapes.Heart, ExpressiveShapes.Bun, ExpressiveShapes.GhostIsh,
            ExpressiveShapes.Diamond, ExpressiveShapes.Gem, ExpressiveShapes.Pentagon
        ).shuffled()
        
        val placedShapes = mutableListOf<PlacedShape>()
        val availableShapes = allShapes.toMutableList()
        
        // Try to place up to 6 shapes without overlapping
        for (i in 0 until 100) {
            if (placedShapes.size >= 6 || availableShapes.isEmpty()) break
            
            val x = Random.nextFloat() * 350f
            val y = Random.nextFloat() * 600f
            
            var overlaps = false
            for (placed in placedShapes) {
                val px = placed.x
                val py = placed.y
                val dist = kotlin.math.sqrt((x - px) * (x - px) + (y - py) * (y - py))
                if (dist < 200f) {
                    overlaps = true
                    break
                }
            }
            
            if (!overlaps) {
                val size = 180 + Random.nextInt(170)
                placedShapes.add(PlacedShape(x, y, size, availableShapes.removeAt(0)))
            }
        }
        placedShapes
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .graphicsLayer { clip = true }
            .logDrawTime("RadioShapesFallbackDraw")
            .logLayoutTime("RadioShapesFallbackLayout")
            .drawWithCache {
                val cachedOutlines = shapes.map { placed ->
                    val sizePx = placed.size.dp.toPx()
                    val outline = placed.shape.createOutline(
                        size = Size(sizePx, sizePx),
                        layoutDirection = layoutDirection,
                        density = this
                    )
                    Triple(
                        placed.x.dp.toPx() - (sizePx / 2f),
                        placed.y.dp.toPx() - (sizePx / 2f),
                        outline
                    )
                }
                
                onDrawBehind {
                    cachedOutlines.forEach { (xPx, yPx, outline) ->
                        translate(left = xPx, top = yPx) {
                            drawOutline(
                                outline = outline,
                                color = tertiaryColor,
                                alpha = 0.05f
                            )
                        }
                    }
                }
            }
    ) {
        // Centered Radio Icon (Only layout child)
        Icon(
            imageVector = radioIcon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.Center)
        )
    }
}
