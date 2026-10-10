package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.designsystem.R

/** App shell supplies this UI action only after confirming a compatible newer update. */
val LocalUpdateAvailableAction = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun UpdateAvailableIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.SystemUpdateAlt,
        contentDescription = stringResource(R.string.update_available_action),
        tint = LocalContentColor.current,
        modifier = modifier,
    )
}

@Composable
internal fun updateShimmerModifier(): Modifier {
    val transition = rememberInfiniteTransition(label = "updateAvailable")
    val sweep = transition.animateFloat(
        initialValue = -1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "updateShimmer",
    )
    return Modifier.drawWithCache {
        onDrawWithContent {
            drawContent()
            val x = size.width * sweep.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                    start = Offset(x - size.width / 2, 0f),
                    end = Offset(x + size.width / 2, size.height),
                ),
            )
        }
    }
}
