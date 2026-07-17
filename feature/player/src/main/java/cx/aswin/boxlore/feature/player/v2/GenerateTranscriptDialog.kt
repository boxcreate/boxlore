package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cx.aswin.boxlore.feature.player.v2.logic.TranscriptDialogState
import cx.aswin.boxlore.feature.player.v2.logic.estimateTranscriptTime
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Confirmation dialog for AI transcript generation, showing the estimated
 * processing time and the remaining daily credit allowance.
 */
@Composable
fun GenerateTranscriptDialog(
    episodeDurationSec: Long,
    autoTranscriptLimitLeft: Int?,
    colorScheme: ColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogState = remember(episodeDurationSec, autoTranscriptLimitLeft) {
        TranscriptDialogState(
            estimatedTime = estimateTranscriptTime(episodeDurationSec),
            remainingGenerations = autoTranscriptLimitLeft
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        TranscriptDialogContent(dialogState, colorScheme, onConfirm, onDismiss)
    }
}

@Composable
private fun TranscriptDialogContent(
    state: TranscriptDialogState,
    colorScheme: ColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = squircle(28.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TranscriptDialogHeader(state, colorScheme)
            Spacer(modifier = Modifier.height(16.dp))
            TranscriptInfoRow(state, colorScheme)
            Spacer(modifier = Modifier.height(24.dp))
            TranscriptDialogActions(state, colorScheme, onConfirm, onDismiss)
        }
    }
}

@Composable
private fun TranscriptDialogHeader(state: TranscriptDialogState, colorScheme: ColorScheme) {
    Surface(
        shape = CircleShape,
        color = colorScheme.tertiaryContainer,
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Generate Transcript",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        state.supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.limitReached) colorScheme.error else colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TranscriptInfoRow(state: TranscriptDialogState, colorScheme: ColorScheme) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        InfoPill(
            icon = Icons.Rounded.Timer,
            text = "Est. ${state.estimatedTime}",
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurfaceVariant
        )
        state.remainingGenerations?.let { remaining ->
            InfoPill(
                icon = Icons.Rounded.AutoAwesome,
                text = "$remaining left for the day",
                containerColor = if (state.limitReached) {
                    colorScheme.errorContainer
                } else {
                    colorScheme.tertiaryContainer
                },
                contentColor = if (state.limitReached) {
                    colorScheme.onErrorContainer
                } else {
                    colorScheme.onTertiaryContainer
                }
            )
        }
    }
}

@Composable
private fun TranscriptDialogActions(
    state: TranscriptDialogState,
    colorScheme: ColorScheme,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Button(
        enabled = state.canGenerate,
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        shape = squircle(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary,
            disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Text(
            if (state.canGenerate) "Generate" else "Limit Reached",
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel", color = colorScheme.onSurfaceVariant)
    }
}

private fun squircle(radius: Dp) = AbsoluteSmoothCornerShape(
    cornerRadiusTL = radius, smoothnessAsPercentTL = 60,
    cornerRadiusTR = radius, smoothnessAsPercentTR = 60,
    cornerRadiusBL = radius, smoothnessAsPercentBL = 60,
    cornerRadiusBR = radius, smoothnessAsPercentBR = 60
)

@Composable
private fun InfoPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(shape = squircle(12.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}
