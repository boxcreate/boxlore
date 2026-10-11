package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Connected action segments; screen owners retain navigation and shortcut callbacks. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBarUtilityActions(
    onFeedbackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFeedbackLongClick: () -> Unit = {},
    onSettingsLongClick: () -> Unit = {},
    settingsButtonModifier: Modifier = Modifier,
) {
    val onUpdate = LocalUpdateAvailableAction.current
    val leading = RoundedCornerShape(topStart = 24.dp, topEnd = 7.dp, bottomEnd = 7.dp, bottomStart = 24.dp)
    val trailing = RoundedCornerShape(topStart = 7.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 7.dp)
    val middle = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onUpdate != null) {
            UtilityActionSegment(
                onClick = onUpdate,
                onLongClick = {},
                shape = leading,
                modifier = Modifier.clip(leading).then(updateShimmerModifier()),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                UpdateAvailableIcon(Modifier.size(22.dp))
            }
        }
        UtilityActionSegment(
            onClick = onFeedbackClick,
            onLongClick = onFeedbackLongClick,
            shape = if (onUpdate == null) leading else middle,
        ) {
            Icon(
                imageVector = Icons.Rounded.Feedback,
                contentDescription = "Send Feedback",
                modifier = Modifier.size(20.dp),
            )
        }
        UtilityActionSegment(
            onClick = onSettingsClick,
            onLongClick = onSettingsLongClick,
            shape = trailing,
            modifier = settingsButtonModifier,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Settings",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UtilityActionSegment(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "utilityActionPress",
    )

    Surface(
        modifier = modifier
            .size(width = 43.dp, height = 33.dp)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
