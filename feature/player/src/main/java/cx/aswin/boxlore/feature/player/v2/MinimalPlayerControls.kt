package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.SeekDurationIcon
import cx.aswin.boxlore.feature.player.seekDurationContentDescription
import cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel

internal data class MinimalPlayerActions(
    val onPlayPause: () -> Unit,
    val onReplay: () -> Unit,
    val onForward: () -> Unit,
    val onSleep: () -> Unit,
    val onSpeed: () -> Unit,
    val onQueue: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onCollapse: () -> Unit,
    val onPodcastInfo: () -> Unit,
)

@Composable
internal fun MinimalPlayerControls(state: PlayerState, colors: ColorScheme, actions: MinimalPlayerActions) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = 334.dp).fillMaxWidth()) {
            val playSize = (maxWidth - 6.dp) / 2
            val smallSize = (playSize - 6.dp) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MinimalShapeButton(
                    onClick = actions.onPlayPause,
                    active = state.isPlaying,
                    style = MinimalButtonStyle(colors, MinimalControlKind.PLAY, prominent = true, enabled = !state.isLoading),
                    description = stringResource(
                        if (state.isLoading) {
                            R.string.minimal_buffering
                        } else if (state.isPlaying) {
                            R.string.minimal_pause
                        } else {
                            R.string.minimal_play
                        }
                    ),
                    modifier = Modifier.size(playSize),
                ) { tint ->
                    if (state.isLoading) {
                        BoxLoreLoader.CircularWavy(size = 32.dp, color = tint)
                    } else {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(44.dp), tint)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MinimalSeekButton(state.seekBackwardMs, false, colors, smallSize, actions.onReplay, Modifier.weight(1f))
                        MinimalSeekButton(state.seekForwardMs, true, colors, smallSize, actions.onForward, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MinimalShapeButton(
                            actions.onSleep,
                            state.sleepTimerEnd != null || state.sleepAtEndOfEpisode,
                            MinimalButtonStyle(colors, MinimalControlKind.SLEEP),
                            stringResource(R.string.minimal_sleep),
                            Modifier.weight(1f).height(smallSize),
                        ) { Icon(Icons.Rounded.Bedtime, null, Modifier.size(26.dp), it) }
                        MinimalShapeButton(
                            actions.onSpeed,
                            state.playbackSpeed != 1f,
                            MinimalButtonStyle(colors, MinimalControlKind.SPEED),
                            stringResource(R.string.minimal_speed, formatSpeedLabel(state.playbackSpeed)),
                            Modifier.weight(1f).height(smallSize),
                        ) {
                            Text(
                                formatSpeedLabel(state.playbackSpeed),
                                color = it,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.medium, textDirection = TextDirection.Ltr),
                                maxLines = 1,
                                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalSeekButton(ms: Long, forward: Boolean, colors: ColorScheme, size: Dp, onClick: () -> Unit, modifier: Modifier) {
    val seconds = (ms / 1_000L).toInt()
    val description = seekDurationContentDescription(seconds, forward)
    val kind = if (forward) MinimalControlKind.FORWARD else MinimalControlKind.BACK
    MinimalShapeButton(onClick, false, MinimalButtonStyle(colors, kind), description, modifier.height(size)) {
        SeekDurationIcon(seconds, forward, description, it, Modifier.size(28.dp))
    }
}

private data class MinimalButtonStyle(val colors: ColorScheme, val kind: MinimalControlKind, val prominent: Boolean = false, val enabled: Boolean = true)

@Composable
private fun MinimalShapeButton(
    onClick: () -> Unit,
    active: Boolean,
    style: MinimalButtonStyle,
    description: String,
    modifier: Modifier,
    content: @Composable (Color) -> Unit,
) {
    val colors = style.colors
    val auxiliaryAccent = remember(colors) { minimalControlAccent(colors) }
    // A buffering/transport change can cancel the gesture before it emits Release. A fresh
    // source clears that transient press; the playback state always owns the active silhouette.
    val interactions = remember(style.enabled, active) { MutableInteractionSource() }
    val pressed = key(interactions) { interactions.collectIsPressedAsState().value }
    val shape = rememberMinimalControlShape(style.kind, active || (pressed && style.enabled))
    val scale by animateFloatAsState(
        if (pressed && style.enabled) 0.97f else 1f,
        spring(dampingRatio = 0.9f, stiffness = 550f),
        label = "minimalControlPress",
    )
    val containerColor = when {
        style.prominent -> colors.primary
        active -> auxiliaryAccent.container
        else -> colors.surfaceContainerHighest
    }
    val contentColor = when {
        style.prominent -> colors.onPrimary
        active -> auxiliaryAccent.content
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.semantics {
            role = Role.Button
            contentDescription = description
        },
        enabled = style.enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactions,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            content(contentColor)
        }
    }
}
