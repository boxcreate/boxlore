package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun GenerateTranscriptDialog(
    colorScheme: ColorScheme,
    autoTranscriptLimitLeft: Int?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val estimatedTime = remember(autoTranscriptLimitLeft) {
        "~1-2 min"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = playerSheetShape(
                PlayerChromeGeometry.SheetOverlayTopCorner,
                PlayerChromeGeometry.SheetOverlayTopCorner,
                PlayerChromeGeometry.SheetOverlayTopCorner,
                PlayerChromeGeometry.SheetOverlayTopCorner,
            ),
            color = colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = colorScheme.tertiaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generate Transcript", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (autoTranscriptLimitLeft == 0) {
                        "Daily AI limit reached. Please try again tomorrow."
                    } else {
                        "AI transcription is in beta and may contain errors."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (autoTranscriptLimitLeft == 0) colorScheme.error else colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 12.dp, smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 12.dp, smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 12.dp, smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 12.dp, smoothnessAsPercentBR = 60,
                        ),
                        color = colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Timer, null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Est. $estimatedTime", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                val canGenerate = autoTranscriptLimitLeft == null || autoTranscriptLimitLeft > 0
                Button(
                    enabled = canGenerate,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                ) {
                    Text("Generate")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}
