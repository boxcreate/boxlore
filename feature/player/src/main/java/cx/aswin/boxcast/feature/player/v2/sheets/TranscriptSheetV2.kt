package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.data.TranscriptSegment
import cx.aswin.boxcast.feature.player.TranscriptView
import kotlinx.coroutines.flow.Flow

@Composable
fun TranscriptSheetV2(
    transcript: List<TranscriptSegment>,
    positionFlow: Flow<Long>,
    colorScheme: ColorScheme,
    isSyncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    transcriptUrl: String? = null,
) {
    PlayerSheetScaffold(
        colorScheme = colorScheme,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Close", color = colorScheme.primary)
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close transcript",
                    tint = colorScheme.onSurface,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) {
            TranscriptView(
                transcript = transcript,
                positionFlow = positionFlow,
                colorScheme = colorScheme,
                onSeek = onSeek,
                isSyncEnabled = isSyncEnabled,
                onSyncEnabledChange = onSyncEnabledChange,
                modifier = Modifier.fillMaxSize(),
                transcriptUrl = transcriptUrl,
            )
        }
    }
}
