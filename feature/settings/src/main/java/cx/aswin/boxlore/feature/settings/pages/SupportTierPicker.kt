package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/**
 * Cybernetic power cell dock hosting the 5 energy tiers with micro charge pips,
 * luminous gradient activation, and tactile haptic feedback.
 */
@Composable
internal fun SupportTierPicker(
    selectedIndex: Int,
    tiers: List<SupportTierCardData>,
    activeColor: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0C0C14),
        border = BorderStroke(1.dp, Color(0xFF1E1E2C)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tiers.forEachIndexed { index, tier ->
                Box(modifier = Modifier.weight(1f)) {
                    SupportTierPill(
                        tier = tier,
                        isSelected = index == selectedIndex,
                        activeColor = activeColor,
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportTierPill(
    tier: SupportTierCardData,
    isSelected: Boolean,
    activeColor: Color,
    onSelect: () -> Unit,
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "pill_border_color",
    )
    val durationTextColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF9E9EB2),
        animationSpec = tween(200),
        label = "pill_duration_color",
    )
    val costTextColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color(0xFF656578),
        animationSpec = tween(200),
        label = "pill_cost_color",
    )

    val backgroundBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                activeColor.copy(alpha = 0.22f),
                activeColor.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF13131C),
                Color(0xFF0F0F16),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundBrush)
            .border(
                BorderStroke(if (isSelected) 1.dp else 0.5.dp, animatedBorderColor),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SupportTierPips(
            count = tier.energySegments,
            pipColor = if (isSelected) activeColor else Color(0xFF323244),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tier.shortDuration,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = GoogleSansWeight.bold,
                fontSize = 12.5.sp,
            ),
            color = durationTextColor,
            maxLines = 1,
        )

        Spacer(modifier = Modifier.height(1.5.dp))

        Text(
            text = tier.cost,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
                fontSize = 10.5.sp,
            ),
            color = costTextColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SupportTierPips(
    count: Int,
    pipColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(pipColor),
            )
        }
    }
}
