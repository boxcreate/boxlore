package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun PlayerTopBar(
    colorScheme: ColorScheme,
    showSwipeMinimizeTip: Boolean,
    isExpanded: Boolean,
    onSwipeMinimizeTipDismissed: () -> Unit,
    onCollapse: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.1f))
                    .clickable(onClick = onCollapse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        var tipVisible by remember { mutableStateOf(showSwipeMinimizeTip) }
        LaunchedEffect(showSwipeMinimizeTip, isExpanded) {
            if (showSwipeMinimizeTip && isExpanded) {
                tipVisible = true
                delay(3500)
                tipVisible = false
                onSwipeMinimizeTipDismissed()
            } else {
                tipVisible = false
            }
        }
        AnimatedContent(
            targetState = tipVisible && isExpanded,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "topBarLabel",
        ) { isShowingTip ->
            Text(
                text = if (isShowingTip) "↓ Swipe down to minimize" else "Now Playing",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isShowingTip) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.1f))
                    .clickable(onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
