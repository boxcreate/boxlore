package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.library.R
import kotlinx.coroutines.launch

/**
 * New Episodes row that acts on a swipe and then springs back: start to end adds to the queue,
 * end to start marks played. Both are also TalkBack actions. Starting on a row, a sideways drag
 * belongs to the row rather than to the Shows / New Episodes pager.
 */
@Composable
internal fun LatestEpisodeSwipeRow(
    onAction: (LatestSwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val resetScope = rememberCoroutineScope()
    val addToQueueLabel = stringResource(R.string.library_add_to_queue)
    val markPlayedLabel = stringResource(R.string.library_mark_as_played)
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        onDismiss = { value ->
            latestSwipeAction(value)?.let(onAction)
            resetScope.launch { state.reset() }
        },
        backgroundContent = { LatestSwipeBackground(direction = state.dismissDirection) },
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(addToQueueLabel) {
                    onAction(LatestSwipeAction.AddToQueue)
                    true
                },
                CustomAccessibilityAction(markPlayedLabel) {
                    onAction(LatestSwipeAction.MarkPlayed)
                    true
                },
            )
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}

@Composable
private fun LatestSwipeBackground(direction: SwipeToDismissBoxValue) {
    when (latestSwipeAction(direction)) {
        LatestSwipeAction.AddToQueue -> LatestSwipeHint(
            label = stringResource(R.string.library_add_to_queue),
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            alignEnd = false,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LatestSwipeAction.MarkPlayed -> LatestSwipeHint(
            label = stringResource(R.string.library_mark_as_played),
            icon = Icons.Rounded.CheckCircle,
            alignEnd = true,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        null -> Unit
    }
}

@Composable
private fun LatestSwipeHint(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    alignEnd: Boolean,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 24.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!alignEnd) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        if (alignEnd) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
    }
}
