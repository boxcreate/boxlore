package cx.aswin.boxlore.core.designsystem.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/**
 * The sweep across the Update segment, and across Feedback while a reply waits: it runs
 * [UpdateShimmer.SWEEPS] times when the segment appears, then rests off the segment so the filled
 * container holds still. With animations off it never runs. The phase is read in draw only.
 */
@Composable
internal fun updateShimmerModifier(): Modifier {
    val sweep = rememberUpdateShimmerSweep()
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

/** The sweep's position across the segment, in segment widths; both ends sit off the segment. */
@Composable
internal fun rememberUpdateShimmerSweep(): Animatable<Float, AnimationVector1D> {
    val context = LocalContext.current
    val sweep = remember { Animatable(UpdateShimmer.START) }
    LaunchedEffect(Unit) {
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale == 0f) return@LaunchedEffect
        repeat(UpdateShimmer.SWEEPS) {
            sweep.snapTo(UpdateShimmer.START)
            sweep.animateTo(UpdateShimmer.END, tween(UpdateShimmer.DURATION_MILLIS, easing = LinearEasing))
        }
    }
    return sweep
}

internal object UpdateShimmer {
    const val SWEEPS = 3
    const val DURATION_MILLIS = 2_600
    const val START = -1f
    const val END = 3f
}
