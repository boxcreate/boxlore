package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlinx.coroutines.delay

internal data class PlayerTopBarActions(
    val onSwipeMinimizeTipDismissed: () -> Unit,
    val onCollapse: () -> Unit,
    val onShare: () -> Unit,
)

@Composable
internal fun PlayerTopBar(
    colorScheme: ColorScheme,
    showSwipeMinimizeTip: Boolean,
    isExpanded: Boolean,
    isCasting: Boolean,
    canCast: Boolean,
    actions: PlayerTopBarActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            BoxLoreCastRouteButton(
                enabled = canCast || isCasting,
                isCasting = isCasting,
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colorScheme.onSurface.copy(alpha = 0.1f)),
            )
        }

        var tipVisible by remember { mutableStateOf(showSwipeMinimizeTip) }
        LaunchedEffect(showSwipeMinimizeTip, isExpanded) {
            if (showSwipeMinimizeTip && isExpanded) {
                tipVisible = true
                delay(3500)
                tipVisible = false
                actions.onSwipeMinimizeTipDismissed()
            } else {
                tipVisible = false
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = actions.onCollapse),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = tipVisible && isExpanded,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "topBarLabel",
            ) { isShowingTip ->
                Text(
                    text = if (isShowingTip) "Swipe down to minimize" else "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = if (isShowingTip) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse player",
                tint = colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.1f))
                    .clickable(onClick = actions.onShare),
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
