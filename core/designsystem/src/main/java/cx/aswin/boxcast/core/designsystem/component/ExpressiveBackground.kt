package cx.aswin.boxcast.core.designsystem.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes

/**
 * Material 3 Expressive Background with Static Floating Shapes.
 * Uses clearly defined design system shapes with premium floating motion.
 */
@Composable
fun ExpressiveAnimatedBackground(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_bg")
    
    // Slow, smooth floating motion
    val floatX by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX"
    )
    
    val floatY by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Vibrant but subtle Material 3 container colors
    val surfaceColor = backgroundColor
    val shapeColor1 = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    val shapeColor2 = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
    val shapeColor3 = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // 1. Top Right - Sunny Shape
        FloatingBox(
            shape = ExpressiveShapes.Sunny,
            rotation = rotation,
            color = shapeColor1,
            size = 200.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = 40.dp + floatX.dp, 
                    y = 60.dp + floatY.dp
                )
        )

        // 2. Center Left - Diamond Shape
        FloatingBox(
            shape = ExpressiveShapes.Diamond,
            rotation = -rotation * 0.5f,
            color = shapeColor2,
            size = 180.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(
                    x = (-20).dp - floatX.dp, 
                    y = (-40).dp + floatY.dp
                )
        )

        // 3. Bottom Right - Flower Shape
        FloatingBox(
            shape = ExpressiveShapes.Flower,
            rotation = rotation * 0.3f,
            color = shapeColor3,
            size = 240.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = 20.dp + (floatX * 0.5f).dp, 
                    y = 40.dp - floatY.dp
                )
        )
    }
}

@Composable
private fun FloatingBox(
    shape: Shape,
    rotation: Float,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
            .background(
                color = color,
                shape = shape
            )
    )
}
