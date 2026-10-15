package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ArtworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/** Optional seek buttons, inside the info segment just before the Play end. */
@Composable
internal fun MiniSeekButtons(
    content: MiniPlayerContent,
    colors: ArtworkMiniPlayerColors,
    actions: MiniPlayerActions,
    enabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniSeekButton(content.seekBackwardSeconds, forward = false, isLoading = content.isLoading || !enabled, colors = colors, onClick = actions.onReplay)
        MiniSeekButton(content.seekForwardSeconds, forward = true, isLoading = content.isLoading || !enabled, colors = colors, onClick = actions.onForward)
    }
}

@Composable
private fun MiniSeekButton(
    seconds: Int,
    forward: Boolean,
    isLoading: Boolean,
    colors: ArtworkMiniPlayerColors,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier =
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .expressiveClickable(
                feedback = ExpressivePressFeedback.Control,
                shape = CircleShape,
                indication = ripple(bounded = true),
                enabled = !isLoading,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }.background(colors.progress),
        contentAlignment = Alignment.Center,
    ) {
        cx.aswin.boxlore.feature.player.SeekDurationIcon(
            seconds = seconds,
            forward = forward,
            contentDescription =
            cx.aswin.boxlore.feature.player.seekDurationContentDescription(
                seconds,
                forward,
            ),
            tint = colors.onBar.copy(alpha = if (isLoading) 0.38f else 1f),
            modifier = Modifier.size(22.dp),
        )
    }
}
