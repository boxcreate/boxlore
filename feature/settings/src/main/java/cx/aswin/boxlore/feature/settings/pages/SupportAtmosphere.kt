package cx.aswin.boxlore.feature.settings.pages

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun SupportLightningAtmosphere(
    tier: SupportTierCardData,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val tierPowerFraction = ((tier.energySegments - 1) / 4f).coerceIn(0f, 1f)
    val lightningScale by animateFloatAsState(
        targetValue = 0.78f + (0.54f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_scale",
    )
    val lightningSpeed by animateFloatAsState(
        targetValue = 0.80f + (0.65f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_speed",
    )
    val lightningAlpha by animateFloatAsState(
        targetValue = 0.20f + (0.32f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_alpha",
    )

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lightning_ambient),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        speed = lightningSpeed,
        iterations = LottieConstants.IterateForever,
    )
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(activeColor.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**"),
        ),
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer {
                scaleX = lightningScale
                scaleY = lightningScale
                alpha = lightningAlpha
            },
    )
}

@Composable
internal fun CosmicAtmosphereCanvas(
    auraColor: Color,
    pulse: Float,
    particleProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val baseRadius = size.width * 0.48f

        // Rich vertical atmospheric wash radiating into deep black from top
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.40f * pulse),
                    auraColor.copy(alpha = 0.18f * pulse),
                    auraColor.copy(alpha = 0.04f * pulse),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.85f,
            ),
        )

        // Focused radiant epicenter bloom behind stage
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.32f * pulse),
                    auraColor.copy(alpha = 0.08f * pulse),
                    Color.Transparent,
                ),
                center = Offset(centerX, size.height * 0.45f),
                radius = baseRadius * 1.30f,
            ),
            center = Offset(centerX, size.height * 0.45f),
            radius = baseRadius * 1.30f,
        )

        // Energy particles emerging from items and floating UPWARDS
        val particleCount = 20
        val startY = size.height * 0.65f
        val riseDistance = size.height * 0.55f

        for (i in 0 until particleCount) {
            val seed = (i * 73.17f) % 360f
            val rad = seed * (PI.toFloat() / 180f)
            val progress = (particleProgress + i.toFloat() / particleCount) % 1f
            val spreadFactor = 0.35f + (0.65f * progress)
            val spreadX = (baseRadius * 0.85f * spreadFactor) * cos(rad)
            val wobble = sin(progress * 2.5f * PI.toFloat() + seed) * 12.dp.toPx()
            val px = centerX + spreadX + wobble
            val py = startY - (progress * riseDistance)
            val alpha = sin(progress * PI.toFloat()).coerceIn(0f, 1f) * 0.85f

            if (alpha > 0.02f) {
                drawCircle(
                    color = auraColor.copy(alpha = alpha * 0.50f),
                    radius = (2.2.dp + (1.2.dp * (1f - progress))).toPx(),
                    center = Offset(px, py),
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.95f),
                    radius = (1.0.dp + (0.4.dp * (1f - progress))).toPx(),
                    center = Offset(px, py),
                )
            }
        }
    }
}
