package cx.aswin.boxlore.feature.settings.feedback

import android.graphics.BitmapFactory
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.support.SupportEnding
import cx.aswin.boxlore.core.support.SupportMessage
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val FEEDBACK_THREAD_TAG = "feedback_thread"
internal const val FEEDBACK_REPLY_FIELD_TAG = "feedback_reply_field"
internal const val FEEDBACK_REPLY_SEND_TAG = "feedback_reply_send"

internal class FeedbackReplyActions(val onOpen: (Boolean) -> Unit, val onChanged: (String) -> Unit, val onSend: () -> Unit)

internal class FeedbackThreadActions(
    val onBack: (() -> Unit)?,
    val onRetry: () -> Unit,
    val onLoadEarlier: () -> Unit,
    val reply: FeedbackReplyActions,
    val onSolve: () -> Unit,
    val onNewFeedback: () -> Unit,
    val photo: suspend (String) -> ByteArray?,
    /** The conversation is on screen (marks the team's replies read up to the newest one shown). */
    val onSeen: () -> Unit = {},
) {
    val onReplyOpen: (Boolean) -> Unit get() = reply.onOpen
    val onReplyChanged: (String) -> Unit get() = reply.onChanged
    val onSendReply: () -> Unit get() = reply.onSend
}

/**
 * One conversation: the listener's messages in outlined cards, the team's in a filled card, then
 * how it ended (solved invites a reply; closed points to new feedback). The reply box rides on the
 * keyboard, which replaces the bottom chrome padding as on the compose screen.
 */
@Composable
internal fun FeedbackThreadScreen(
    state: FeedbackThreadUiState,
    actions: FeedbackThreadActions,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
    imeBottom: Dp = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
) {
    TrackScreenView("feedback_thread")
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(modifier = modifier.fillMaxSize().testTag(FEEDBACK_THREAD_TAG), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().padding(bottom = feedbackBottomPadding(imeBottom, bottomContentPadding, systemBar)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (actions.onBack != null) FeedbackIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate back", actions.onBack) else Spacer(Modifier.width(16.dp))
                    Text(
                        state.conversation?.thread?.categoryLabel ?: "Feedback",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.conversation != null -> Messages(state, actions)
                        state.gone -> Notice("This conversation isn't available any more.", actions.onBack?.let { "Back" to it }, Modifier.align(Alignment.Center))
                        state.loadFailed -> Notice("Couldn't load this conversation. Check your connection and try again.", "Try again" to actions.onRetry, Modifier.align(Alignment.Center))
                        else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
                if (state.replyOpen) ReplyBar(state, actions)
            }
        }
    }
}

@Composable
private fun Messages(state: FeedbackThreadUiState, actions: FeedbackThreadActions) {
    val conversation = state.conversation ?: return
    // Read only once it is really showing, up to what is showing.
    LaunchedEffect(conversation.thread.unread, state.newestShownAt) { if (conversation.thread.unread) actions.onSeen() }
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.loadFailed) {
            item(key = "refresh_failed") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Couldn't refresh this conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onRetry) { Text("Try again", fontWeight = GoogleSansWeight.semiBold) }
                }
            }
        }
        if (conversation.earlier != null) {
            item(key = "earlier") {
                TextButton(onClick = actions.onLoadEarlier, enabled = !state.loadingEarlier, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.loadingEarlier) "Loading…" else "Show earlier messages", fontWeight = GoogleSansWeight.semiBold)
                }
            }
        }
        items(conversation.messages, key = { it.id }) { message ->
            MessageCard(message, feedbackMessageTime(message.createdAt, now, zone, locale, is24Hour), actions.photo)
        }
        conversation.ending?.let { ending ->
            item(key = "ending") {
                EndingCard(ending, feedbackListTime(ending.at, now, zone, locale, is24Hour), onReply = { actions.onReplyOpen(true) }, onNew = actions.onNewFeedback, canReply = state.canReply && !state.replyOpen)
            }
        }
        if (conversation.ending == null && state.canReply && !state.replyOpen) {
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { actions.onReplyOpen(true) }, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Reply", fontWeight = GoogleSansWeight.semiBold)
                    }
                    if (state.canSolve) {
                        TextButton(onClick = actions.onSolve, enabled = !state.solving, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Mark solved", fontWeight = GoogleSansWeight.semiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: SupportMessage, time: String, photo: suspend (String) -> ByteArray?) {
    val team = message.fromTeam
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (team) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (team) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (team) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (team) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("b", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.semiBold)
                    }
                    Text(message.author.ifBlank { "boxlore team" }, style = MaterialTheme.typography.titleSmall)
                    Text("· $time", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                }
            } else {
                Text("${message.author.ifBlank { "You" }} · $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            if (message.photoIds.isNotEmpty()) {
                // Scrolls sideways, so all of a message's photos stay reachable on narrow phones.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.photoIds.forEachIndexed { index, id -> PhotoThumb(id, index, photo) }
                }
            }
            if (message.diagnostics) {
                Text("Diagnostics included", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhotoThumb(id: String, index: Int, photo: suspend (String) -> ByteArray?) {
    val image by produceState<ImageBitmap?>(null, id) {
        value = withContext(Dispatchers.Default) {
            photo(id)?.let { bytes ->
                runCatching {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .size(width = 64.dp, height = 112.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Photo ${index + 1}" },
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun EndingCard(ending: SupportEnding, time: String, onReply: () -> Unit, onNew: () -> Unit, canReply: Boolean) {
    val solved = ending.kind == "solved"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (solved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (solved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (solved) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (solved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(feedbackStatusLine(ending.title, showTime = ending.at > 0, time = time), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
            }
            if (ending.body.isNotBlank()) {
                Text(
                    ending.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (solved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val label = ending.actionLabel
            when {
                ending.actionKind == "reply" && label != null && canReply -> Button(
                    onClick = onReply,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) { Text(label, fontWeight = GoogleSansWeight.semiBold) }
                ending.actionKind == "new" && label != null -> Button(onClick = onNew, shape = CircleShape, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(label, fontWeight = GoogleSansWeight.semiBold)
                }
            }
        }
    }
}

@Composable
private fun ReplyBar(state: FeedbackThreadUiState, actions: FeedbackThreadActions) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 180.dp)) {
                if (state.reply.isEmpty()) {
                    Text("Write your reply", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = state.reply,
                    onValueChange = actions.onReplyChanged,
                    readOnly = state.sending,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag(FEEDBACK_REPLY_FIELD_TAG)
                        .semantics { contentDescription = "Your reply" },
                )
            }
            state.replyError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp, end = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { actions.onReplyOpen(false) }, enabled = !state.sending, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", fontWeight = GoogleSansWeight.semiBold)
                }
                Button(
                    onClick = actions.onSendReply,
                    enabled = state.canSendReply,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(FEEDBACK_REPLY_SEND_TAG),
                ) {
                    Text(if (state.sending) "Sending" else "Send reply", fontWeight = GoogleSansWeight.semiBold)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>?, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label, fontWeight = GoogleSansWeight.semiBold) } }
    }
}
