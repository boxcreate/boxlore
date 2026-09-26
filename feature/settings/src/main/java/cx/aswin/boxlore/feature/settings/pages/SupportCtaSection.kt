package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal fun getSupportButtonContentColor(backgroundColor: Color): Color =
    if (backgroundColor.luminance() > 0.45f) Color.Black else Color.White

@Composable
internal fun SupportCtaSection(
    tier: SupportTierCardData,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val buttonContentColor = getSupportButtonContentColor(activeColor)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SupportDeployButton(
            tier = tier,
            activeColor = activeColor,
            contentColor = buttonContentColor,
        )

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = "One-time contribution • No recurring subscription",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.medium),
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SupportDeployButton(
    tier: SupportTierCardData,
    activeColor: Color,
    contentColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
        label = "button_press_scale",
    )

    val backgroundGradient = remember(activeColor) {
        Brush.horizontalGradient(
            colors = listOf(
                activeColor,
                activeColor.copy(alpha = 0.88f),
                activeColor,
            ),
        )
    }

    Surface(
        onClick = {},
        interactionSource = interactionSource,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundGradient),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (tier.isFeatured) Icons.Rounded.Favorite else Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            DeployButtonMeterRoll(tier = tier, contentColor = contentColor)
        }
    }
}

@Composable
private fun DeployButtonMeterRoll(
    tier: SupportTierCardData,
    contentColor: Color,
) {
    AnimatedContent(
        targetState = tier,
        transitionSpec = {
            (slideInVertically(tween(220, easing = FastOutSlowInEasing)) { height -> height / 2 } + fadeIn(tween(180)))
                .togetherWith(
                    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { height -> -height / 2 } + fadeOut(tween(160)),
                )
        },
        label = "button_meter_roll",
    ) { currentTier ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Deploy ${currentTier.title}",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = GoogleSansWeight.bold,
                    letterSpacing = 0.2.sp,
                ),
                color = contentColor,
                maxLines = 1,
            )
            Text(
                text = " • ",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = GoogleSansWeight.medium,
                ),
                color = contentColor.copy(alpha = 0.70f),
            )
            Text(
                text = currentTier.cost,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = GoogleSansWeight.bold,
                    letterSpacing = 0.3.sp,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
