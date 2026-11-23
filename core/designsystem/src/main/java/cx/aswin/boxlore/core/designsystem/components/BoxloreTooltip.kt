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
 * tooltip below the button. Pass the button's own content description as [label], so the tooltip
 * says what a screen reader already says. It changes nothing about the button's size or layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxloreTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}
