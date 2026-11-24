package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneBottomInset
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneStatusBarsPadding

/** A button on [InfoStatusMessage]: its label and what it does. */
internal data class InfoStatusAction(val label: String, val onClick: () -> Unit)

internal const val INFO_STATUS_MESSAGE_TAG = "info_status_message"

/**
 * A calm, centred message for a page that couldn't show its content: an icon tile, a title, a short
 * explanation, then the [primary] button and an optional [secondary] text button. Centred in this
 * pane's safe area at a readable width; it scrolls when the pane is too short, so its buttons are
 * never cut off. Announced politely to screen readers.
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
    InfoStatusMessage(
        title = title,
        body = body,
        modifier = modifier,
        leading = { InfoStatusIconTile(icon) },
        primary = primary,
        secondary = secondary,
    )
}

/**
 * The shared layout behind [InfoStatusMessage] and the episode link states: an optional [leading]
 * picture (an icon tile or a loader), the title as a heading, the body, then the buttons that are
 * given. Pads by this pane's own system bars, so a pane below a tabletop hinge or beside another
 * pane isn't pushed off centre by bars it doesn't touch.
 */
@Suppress("LongParameterList")
@Composable
internal fun InfoStatusMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    primary: InfoStatusAction? = null,
    secondary: InfoStatusAction? = null,
    tag: String = INFO_STATUS_MESSAGE_TAG,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .boxlorePaneStatusBarsPadding()
            .boxloreHorizontalSafePadding()
            .padding(bottom = boxlorePaneBottomInset()),
        contentAlignment = Alignment.Center,
    ) {
        BoxloreScrollableCenteredColumn(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag(tag)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leading?.invoke()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                primary?.let { action -> Button(onClick = action.onClick, modifier = Modifier.padding(top = 4.dp)) { Text(action.label) } }
                secondary?.let { action -> TextButton(onClick = action.onClick) { Text(action.label) } }
            }
        }
    }
}

@Composable
private fun InfoStatusIconTile(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

private val MAX_CONTENT_WIDTH = 480.dp
