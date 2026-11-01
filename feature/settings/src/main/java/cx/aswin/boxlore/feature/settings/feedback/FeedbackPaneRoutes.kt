package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
@Composable
internal fun FeedbackPanesLayout(
    split: Boolean,
    pane: FeedbackPane,
    threads: List<SupportThread>?,
    list: @Composable (Modifier, String?) -> Unit,
    detail: @Composable (FeedbackPane, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (split) {
        val shown = if (pane == FeedbackPane.List) threads?.firstOrNull()?.let { FeedbackPane.Thread(it.id) } ?: FeedbackPane.Compose else pane
        Row(modifier.fillMaxSize()) {
            list(Modifier.width(FEEDBACK_LIST_PANE_WIDTH).fillMaxHeight(), (shown as? FeedbackPane.Thread)?.id)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight()) { detail(shown, false) }
        }
    } else if (pane == FeedbackPane.List) {
        list(modifier, null)
    } else {
        Box(modifier) { detail(pane, true) }
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
    FeedbackThreadScreen(
        state = state,
        actions = FeedbackThreadActions(
            onBack = onBack,
            onRetry = threadViewModel::reload,
            onLoadEarlier = threadViewModel::loadEarlier,
            onReplyOpen = threadViewModel::onReplyOpen,
            onReplyChanged = threadViewModel::onReplyChanged,
            onSendReply = threadViewModel::sendReply,
            onSolve = threadViewModel::solve,
            onNewFeedback = onNewFeedback,
            photo = threadViewModel::photo,
        ),
        bottomContentPadding = bottomContentPadding,
    )
}
