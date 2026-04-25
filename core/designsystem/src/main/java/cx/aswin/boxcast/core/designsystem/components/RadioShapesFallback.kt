package cx.aswin.boxcast.core.designsystem.components

import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Radio station fallback image — same visual language as AnimatedShapesFallback
 * but with a Radio icon instead of a Podcasts icon.
 */
@Composable
fun RadioShapesFallback() {
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
        
        val placedShapes = mutableListOf<Triple<Float, Float, androidx.compose.ui.graphics.Shape>>()
        val availableShapes = allShapes.toMutableList()
        
        // Try to place up to 6 shapes without overlapping
        for (i in 0 until 100) {
            if (placedShapes.size >= 6 || availableShapes.isEmpty()) break
            
            val x = Random.nextFloat() * 350f
            val y = Random.nextFloat() * 600f
            
            var overlaps = false
            for (placed in placedShapes) {
                val px = placed.first
                val py = placed.second
                val dist = kotlin.math.sqrt((x - px) * (x - px) + (y - py) * (y - py))
                if (dist < 200f) {
                    overlaps = true
                    break
                }
            }
            
            if (!overlaps) {
                placedShapes.add(Triple(x, y, availableShapes.removeAt(0)))
            }
        }
        placedShapes
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .graphicsLayer { clip = true }
    ) {
        shapes.forEach { (dx, dy, shape) ->
            val size = (180 + Random.nextInt(170)).dp 
            
            Box(
                modifier = Modifier
                    .size(size)
                    .offset(
                        x = dx.dp - (size / 2), 
                        y = dy.dp - (size / 2)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
                        shape = shape
                    )
            )
        }
        
        // Centered Radio Icon
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
