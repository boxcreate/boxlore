package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreTooltip
import cx.aswin.boxlore.core.designsystem.components.LocalWatchPlayOffer
import cx.aswin.boxlore.core.designsystem.components.PlayingOnPill
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.player.R
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
    /** Short sideways phones: no space above and below the 48dp buttons. */
    tight: Boolean = false,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = if (tight) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerHeaderCastButton(enabled = canCast || isCasting, isCasting = isCasting, colorScheme = colorScheme)

        val watchOffer = LocalWatchPlayOffer.current
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
                .clickable(onClickLabel = stringResource(R.string.player_collapse), onClick = actions.onCollapse),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = tipVisible && isExpanded,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "topBarLabel",
            ) { isShowingTip ->
                // With a watch in reach the label says where it plays and can move it; the tip still
                // shows first, and a tap beside the pill still collapses the player.
                if (watchOffer != null && !isShowingTip) {
                    PlayingOnPill(offer = watchOffer, colorScheme = colorScheme)
                } else {
                    Text(
                        text = stringResource(if (isShowingTip) R.string.player_swipe_down_tip else R.string.player_now_playing),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = GoogleSansWeight.semiBold,
                        color = if (isShowingTip) colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (watchOffer == null) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_collapse),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        PlayerHeaderButton(Icons.Rounded.Share, stringResource(R.string.player_share), colorScheme, actions.onShare)
    }
}

/** A player header icon button: a solid 48dp circle that names itself on hover or long-press. */
@Composable
internal fun PlayerHeaderButton(icon: ImageVector, label: String, colorScheme: ColorScheme, onClick: () -> Unit) {
    MaterialTheme(colorScheme = colorScheme) {
        BoxloreTooltip(label) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(PLAYER_HEADER_BUTTON_SIZE),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surfaceContainerHigh,
                    contentColor = colorScheme.onSurface,
                ),
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The Cast button in the same 48dp circle as the other header buttons, keeping its own tint for state. */
@Composable
internal fun PlayerHeaderCastButton(enabled: Boolean, isCasting: Boolean, colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        BoxloreTooltip(stringResource(if (isCasting) R.string.player_change_cast_device else R.string.player_cast)) {
            BoxLoreCastRouteButton(
                enabled = enabled,
                isCasting = isCasting,
                tint = colorScheme.onSurface,
                activeTint = colorScheme.primary,
                modifier = Modifier
                    .size(PLAYER_HEADER_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHigh),
            )
        }
    }
}

internal val PLAYER_HEADER_BUTTON_SIZE = 48.dp
