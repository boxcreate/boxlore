package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.support.SupportRepository
import cx.aswin.boxlore.core.support.SupportThread

/* The panes behind FeedbackScreen: the side-by-side layout and the compose and conversation routes. */

internal val FEEDBACK_LIST_PANE_WIDTH = 400.dp

/** One pane at a time on phones; side by side, the list keeps a conversation open: the picked one, else the newest, else a new message. */
/**
 * One pane at a time on phones; side by side, the list keeps a conversation open: the picked one,
 * else the newest, else a new message. The list and the detail keep their places in one row in
 * every window, so crossing the split gate never rebuilds them: the conversation's scroll, the
 * reply box's text, cursor and focus all stay (the list's scroll is kept by the host).
 */
@Composable
internal fun FeedbackPanesLayout(
    split: Boolean,
    pane: FeedbackPane,
    threads: List<SupportThread>?,
    list: @Composable (Modifier, String?) -> Unit,
    detail: @Composable (FeedbackPane, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = if (split && pane == FeedbackPane.List) threads?.firstOrNull()?.let { FeedbackPane.Thread(it.id) } ?: FeedbackPane.Compose else pane
    val showList = split || pane == FeedbackPane.List
    val showDetail = split || pane != FeedbackPane.List
    Row(modifier.fillMaxSize()) {
        if (showList) {
            Box(if (split) Modifier.width(FEEDBACK_LIST_PANE_WIDTH).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) {
                list(Modifier.fillMaxSize(), if (split) (shown as? FeedbackPane.Thread)?.id else null)
            }
        }
        if (split) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (showDetail) {
            Box(Modifier.weight(1f).fillMaxHeight()) { detail(shown, !split) }
        }
    }
}

internal class FeedbackDetailCallbacks(
    val back: () -> Unit,
    val leave: () -> Unit,
    val newFeedback: () -> Unit,
    val sent: (repliesInApp: Boolean) -> Unit,
)

/** A conversation or a new message; without its own Back arrow (side by side) the compose arrow leaves Feedback. */
@Composable
internal fun FeedbackDetailPane(
    supportRepository: SupportRepository,
    boxcastPrefs: BoxcastPrefs,
    detail: FeedbackPane,
    composeSession: Int,
    bottomContentPadding: Dp,
    callbacks: FeedbackDetailCallbacks,
    showBack: Boolean,
) {
    if (detail is FeedbackPane.Thread) {
        FeedbackThreadRoute(supportRepository, detail.id, bottomContentPadding, onBack = callbacks.back.takeIf { showBack }, onNewFeedback = callbacks.newFeedback)
    } else {
        FeedbackComposeRoute(
            supportRepository,
            boxcastPrefs,
            composeSession,
            bottomContentPadding,
            onBack = if (showBack) callbacks.back else callbacks.leave,
            onDone = callbacks.sent,
        )
    }
}

internal const val FEEDBACK_GITHUB_NEW_ISSUE_URL = "https://github.com/boxcreate/boxlore/issues/new"

@Composable
private fun FeedbackComposeRoute(
    supportRepository: SupportRepository,
    boxcastPrefs: BoxcastPrefs,
    session: Int,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onDone: (repliesInApp: Boolean) -> Unit,
) {
    val context = LocalContext.current
    // A new key per "Send feedback": a fresh form each time (the saved draft still comes back).
    val feedbackViewModel: FeedbackViewModel = viewModel(
        key = "feedback-compose-$session",
        factory = FeedbackViewModel.Factory(supportRepository, boxcastPrefs, context),
    )
    val uiState by feedbackViewModel.uiState.collectAsStateWithLifecycle()
    FeedbackContent(
        state = uiState,
        actions = FeedbackComposeActions(
            onBack = onBack,
            onCategorySelected = feedbackViewModel::onCategorySelected,
            onMessageChanged = feedbackViewModel::onMessageChanged,
            onAttachDiagnosticsChanged = feedbackViewModel::onAttachDiagnosticsChanged,
            onPhotosPicked = feedbackViewModel::onPhotosPicked,
            onRemovePhoto = feedbackViewModel::onRemovePhoto,
            onSubmit = feedbackViewModel::onSubmit,
            onPreviewDiagnostics = feedbackViewModel::loadLogsForPreview,
            onDiscardDraft = feedbackViewModel::onDiscardDraft,
        ),
        boxcastPrefs = boxcastPrefs,
        bottomContentPadding = bottomContentPadding,
        onDone = { onDone(uiState.repliesInApp) },
    )
    if (uiState.logsPreview != null || uiState.isLoadingLogs) {
        LogsPreviewDialog(
            logs = uiState.logsPreview,
            isLoading = uiState.isLoadingLogs,
            diagnosticInfo = uiState.diagnosticInfo,
            onShare = { shareFeedbackDiagnostics(context, uiState) },
            onDismissRequest = feedbackViewModel::clearLogsPreview,
        )
    }
}

@Composable
private fun FeedbackThreadRoute(
    supportRepository: SupportRepository,
    threadId: String,
    bottomContentPadding: Dp,
    onBack: (() -> Unit)?,
    onNewFeedback: () -> Unit,
) {
    val threadViewModel: FeedbackThreadViewModel = viewModel(
        key = "feedback-thread-$threadId",
        factory = FeedbackThreadViewModel.Factory(supportRepository, threadId),
    )
    val state by threadViewModel.uiState.collectAsStateWithLifecycle()
    // The view model can be kept from an earlier visit: load fresh each time the conversation shows.
    LaunchedEffect(threadViewModel) { threadViewModel.activate() }
    FeedbackThreadScreen(
        state = state,
        actions = FeedbackThreadActions(
            onBack = onBack,
            onRetry = threadViewModel::reload,
            onLoadEarlier = threadViewModel::loadEarlier,
            reply = FeedbackReplyActions(threadViewModel::onReplyOpen, threadViewModel::onReplyChanged, threadViewModel::sendReply),
            onSolve = threadViewModel::solve,
            onNewFeedback = onNewFeedback,
            photo = threadViewModel::photo,
            onSeen = threadViewModel::onSeen,
        ),
        bottomContentPadding = bottomContentPadding,
    )
}
