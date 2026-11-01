package cx.aswin.boxlore.feature.settings.feedback

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.support.SupportThread
import java.time.ZoneId
import java.util.Locale

internal const val FEEDBACK_LIST_TAG = "feedback_list"
internal const val FEEDBACK_NEW_TAG = "feedback_new"

/** The list's own room for its pinned buttons: two rows of buttons and their gaps. */
private val LIST_ACTIONS_HEIGHT = 136.dp

/**
 * "Feedback": the listener's conversations in one card, newest first, with Rate on Play,
 * Report on GitHub and Send feedback pinned at the bottom; the list scrolls behind them.
 */
internal class FeedbackListActions(
    val onOpen: (String) -> Unit,
    val onNew: () -> Unit,
    val onRetry: () -> Unit,
    val onRate: () -> Unit,
    val onGitHub: () -> Unit,
    val onBack: (() -> Unit)?,
)

@Composable
internal fun FeedbackListScreen(
    threads: List<SupportThread>?,
    loadFailed: Boolean,
    selectedId: String?,
    bottomContentPadding: Dp,
    actions: FeedbackListActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val onBack = actions.onBack
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = bottomContentPadding + systemBar
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) FeedbackIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate back", onBack) else Spacer(Modifier.width(16.dp))
                    Text("Feedback", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        threads != null -> ThreadList(threads, selectedId, actions.onOpen, listState, PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + LIST_ACTIONS_HEIGHT + 16.dp))
                        loadFailed -> LoadFailed(actions.onRetry, Modifier.align(Alignment.Center).padding(bottom = bottom + LIST_ACTIONS_HEIGHT))
                        else -> CircularProgressIndicator(Modifier.align(Alignment.Center).padding(bottom = bottom + LIST_ACTIONS_HEIGHT))
                    }
                    ListActions(actions.onNew, actions.onRate, actions.onGitHub, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottom + 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ThreadList(threads: List<SupportThread>, selectedId: String?, onOpen: (String) -> Unit, listState: LazyListState, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize().testTag(FEEDBACK_LIST_TAG), state = listState, contentPadding = contentPadding) {
        item(key = "heading") {
            Text(
                "Your feedback",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(threads, key = { _, thread -> thread.id }) { index, thread ->
            val first = index == 0
            val last = index == threads.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 20.dp else 0.dp,
                topEnd = if (first) 20.dp else 0.dp,
                bottomStart = if (last) 20.dp else 0.dp,
                bottomEnd = if (last) 20.dp else 0.dp,
            )
            val time = feedbackListTime(thread.updatedAt, now, ZoneId.systemDefault(), Locale.getDefault(), is24Hour)
            Column(Modifier.clip(shape)) {
                ThreadRow(thread, feedbackStatusLine(thread.status.label, thread.status.showTime, time), selected = thread.id == selectedId, shape = shape, onClick = { onOpen(thread.id) })
                if (!last) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        HorizontalDivider(Modifier.padding(start = 34.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: SupportThread, statusLine: String, selected: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    val closed = thread.status.key == "closed"
    val unread = thread.unread
    val statusColor = when (thread.status.key) {
        "new_reply" -> MaterialTheme.colorScheme.primary
        "solved" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(shape = shape, feedback = ExpressivePressFeedback.Surface, onClick = onClick)
            .semantics { contentDescription = "${thread.title}. $statusLine" },
    ) {
        Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 8.dp).size(8.dp).then(if (unread) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    thread.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) GoogleSansWeight.semiBold else null,
                    color = if (closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (thread.status.key) {
                        "solved" -> Icon(Icons.Rounded.Check, contentDescription = null, tint = statusColor, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                        "closed" -> Icon(Icons.Rounded.Lock, contentDescription = null, tint = statusColor, modifier = Modifier.padding(end = 4.dp).size(14.dp))
                    }
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                        fontWeight = if (unread) GoogleSansWeight.semiBold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Couldn't load your feedback. Check your connection and try again.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("Try again", fontWeight = GoogleSansWeight.semiBold) }
    }
}

@Composable
private fun ListActions(onNew: () -> Unit, onRate: () -> Unit, onGitHub: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRate, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rate on Play", fontWeight = GoogleSansWeight.semiBold)
            }
            FilledTonalButton(onClick = onGitHub, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report on GitHub", fontWeight = GoogleSansWeight.semiBold)
            }
        }
        Button(onClick = onNew, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(FEEDBACK_NEW_TAG)) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Send feedback", style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
        }
    }
}
