package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn

/** A button on [InfoStatusMessage]: its label and what it does. */
internal data class InfoStatusAction(val label: String, val onClick: () -> Unit)

internal const val INFO_STATUS_MESSAGE_TAG = "info_status_message"

/**
 * A calm, centred message for a page that couldn't show its content: an icon tile, a title, a short
 * explanation, then the [primary] button and an optional [secondary] text button. Centred in the
 * window's safe area at a readable width; it scrolls when the window is too short, so its buttons
 * are never cut off. Announced politely to screen readers.
 */
@Composable
internal fun InfoStatusMessage(
    icon: ImageVector,
    title: String,
    body: String,
    primary: InfoStatusAction,
    modifier: Modifier = Modifier,
    secondary: InfoStatusAction? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        BoxloreScrollableCenteredColumn(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag(INFO_STATUS_MESSAGE_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(22.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(onClick = primary.onClick, modifier = Modifier.padding(top = 4.dp)) { Text(primary.label) }
                secondary?.let { action -> TextButton(onClick = action.onClick) { Text(action.label) } }
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 480.dp
