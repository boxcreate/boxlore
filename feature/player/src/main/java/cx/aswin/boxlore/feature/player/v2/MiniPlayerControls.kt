package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

@Composable
internal fun MiniTransportControls(
    content: MiniPlayerContent,
    colorScheme: ColorScheme,
    actions: MiniPlayerActions,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content.showSeekButtons) {
            MiniSeekButton(
                seconds = content.seekBackwardSeconds,
                forward = false,
                isLoading = content.isLoading || !enabled,
                colorScheme = colorScheme,
                onClick = actions.onReplay,
            )
        }
        MiniPlayButton(
            isPlaying = content.isPlaying,
            isLoading = content.isLoading,
            colorScheme = colorScheme,
            onClick = actions.onPlayPause,
            enabled = enabled,
        )
        if (content.showSeekButtons) {
            MiniSeekButton(
                seconds = content.seekForwardSeconds,
                forward = true,
                isLoading = content.isLoading || !enabled,
                colorScheme = colorScheme,
                onClick = actions.onForward,
            )
        }
    }
}

@Composable
private fun MiniPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier =
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colorScheme.primary)
            .expressiveClickable(
                shape = CircleShape,
                indication = ripple(bounded = true),
                enabled = !isLoading && enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(220),
            label = "miniLoading",
        ) { loading ->
            MiniPlayButtonContent(loading, isPlaying, colorScheme)
        }
    }
}

@Composable
private fun MiniPlayButtonContent(
    isLoading: Boolean,
    isPlaying: Boolean,
    colorScheme: ColorScheme,
) {
    if (isLoading) {
        BoxLoreLoader.CircularWavy(
            modifier = Modifier.size(24.dp),
            color = colorScheme.onPrimary,
            trackColor = colorScheme.onPrimary.copy(alpha = 0.24f),
        )
        return
    }
    Crossfade(
        targetState = isPlaying,
        animationSpec = tween(180),
        label = "miniPlayPause",
    ) { playing ->
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun MiniSeekButton(
    seconds: Int,
    forward: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier =
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colorScheme.secondaryContainer)
            .expressiveClickable(
                shape = CircleShape,
                indication = ripple(bounded = true),
                enabled = !isLoading,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
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
            tint = colorScheme.onSecondaryContainer.copy(alpha = if (isLoading) 0.38f else 1f),
            modifier = Modifier.size(22.dp),
        )
    }
}
