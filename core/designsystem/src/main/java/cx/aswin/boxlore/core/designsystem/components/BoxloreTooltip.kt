package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/**
 * Names an icon-only button when a listener hovers it with a mouse or long-presses it: a plain
 * tooltip below the button, or at [position] (above a row near the bottom, so a finger doesn't hide
 * it). Pass the button's own content description as [label], so the tooltip says what a screen
 * reader already says. It changes nothing about the button's size or layout. The tooltip box is not
 * the row's direct child, so a row weight goes on a `Box` around it, not on the button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxloreTooltip(
    label: String,
    position: BoxloreTooltipPosition = BoxloreTooltipPosition.Below,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (position == BoxloreTooltipPosition.Above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

/** Where [BoxloreTooltip] opens beside its button. */
enum class BoxloreTooltipPosition { Below, Above }
