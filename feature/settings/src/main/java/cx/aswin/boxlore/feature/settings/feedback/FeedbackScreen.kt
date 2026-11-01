package cx.aswin.boxlore.feature.settings.feedback

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.support.SupportRepository

/** Below this height the full layout scrolls instead of stretching the message card. */
internal val FEEDBACK_TALL_ENOUGH = 600.dp
private val FEEDBACK_SHORT_MESSAGE_HEIGHT = 220.dp

/**
 * "Share your thoughts": the type pills, a roomy message card, photos (when the server switches
 * them on), diagnostics and Send. While typing, the pills fold into one pill in the header and a
 * slim bar rides on the keyboard, so Send is always reachable. After sending, [FeedbackSentScreen].
 */
@Composable
fun FeedbackScreen(
    supportRepository: SupportRepository,
    boxcastPrefs: BoxcastPrefs,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    TrackScreenView("feedback")
    val context = LocalContext.current
    val feedbackViewModel: FeedbackViewModel = viewModel(
        factory = FeedbackViewModel.Factory(supportRepository, boxcastPrefs, context),
    )
    val uiState by feedbackViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

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
        modifier = modifier,
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

internal class FeedbackComposeActions(
    val onBack: () -> Unit,
    val onCategorySelected: (FeedbackCategory) -> Unit,
    val onMessageChanged: (String) -> Unit,
    val onAttachDiagnosticsChanged: (Boolean) -> Unit,
    val onPhotosPicked: (List<Uri>) -> Unit,
    val onRemovePhoto: (PickedPhoto) -> Unit,
    val onSubmit: () -> Unit,
    val onPreviewDiagnostics: () -> Unit,
    val onDiscardDraft: () -> Unit,
)

/** The compose screen until the message is sent, then [FeedbackSentScreen]; Done leaves like Back. */
@Composable
internal fun FeedbackContent(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    boxcastPrefs: BoxcastPrefs,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (state.isSuccess) {
        FeedbackSentScreen(
            boxcastPrefs = boxcastPrefs,
            repliesInApp = state.repliesInApp,
            onDone = actions.onBack,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier,
        )
    } else {
        FeedbackComposeScreen(state = state, actions = actions, bottomContentPadding = bottomContentPadding, modifier = modifier)
    }
}

/** True while the listener is writing: the message has focus and the keyboard is up. */
internal fun isTypingLayout(messageFocused: Boolean, imeBottom: Dp): Boolean = messageFocused && imeBottom > 0.dp

/** Space under the content: the keyboard while it's up, otherwise the app's bottom chrome and the system bar. */
internal fun feedbackBottomPadding(imeBottom: Dp, chrome: Dp, systemBar: Dp): Dp = if (imeBottom > 0.dp) imeBottom else chrome + systemBar

@Composable
internal fun FeedbackComposeScreen(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var messageFocused by rememberSaveable { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val typing = isTypingLayout(messageFocused, imeBottom)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = state.meta.maxPhotos.coerceAtLeast(2)),
    ) { uris -> actions.onPhotosPicked(uris) }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = feedbackBottomPadding(imeBottom, bottomContentPadding, systemBar)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
                if (typing) {
                    TypingLayout(state, actions, onFocusChanged = { messageFocused = it }, onAddPhoto = pickPhotos)
                } else {
                    FullLayout(state, actions, onFocusChanged = { messageFocused = it }, onAddPhoto = pickPhotos)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FullLayout(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    onFocusChanged: (Boolean) -> Unit,
    onAddPhoto: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    TopRow(state, actions)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val tall = maxHeight >= FEEDBACK_TALL_ENOUGH
        val content: @Composable ColumnScope.() -> Unit = {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Share your thoughts", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bugs, ideas, anything at all. The boxlore team reads every message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.shownNotice?.let { notice ->
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            FeedbackTypePills(
                selected = state.category,
                onSelect = actions.onCategorySelected,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
            )
            FeedbackMessageCard(
                value = state.message,
                onValueChange = actions.onMessageChanged,
                placeholder = state.category.placeholder,
                maxChars = state.meta.maxBody.coerceAtMost(FeedbackViewModel.MAX_MESSAGE),
                onFocusChanged = onFocusChanged,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .fillMaxWidth()
                    .then(if (tall) Modifier.weight(1f) else Modifier.height(FEEDBACK_SHORT_MESSAGE_HEIGHT)),
            )
            if (state.meta.photosEnabled) {
                FeedbackPhotoRow(
                    photos = state.photos,
                    canAdd = state.canAddPhotos,
                    maxPhotos = state.meta.maxPhotos,
                    onAdd = onAddPhoto,
                    onRemove = actions.onRemovePhoto,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            FeedbackDiagnosticsRow(
                checked = state.attachDiagnostics,
                onCheckedChange = actions.onAttachDiagnosticsChanged,
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp),
            )
            state.errorMessage?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            FeedbackSendButton(
                enabled = state.canSend,
                sending = state.isSubmitting,
                onClick = actions.onSubmit,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(64.dp),
            )
            FeedbackPrivacyLink(
                onOpen = { uriHandler.openUri(FEEDBACK_PRIVACY_URL) },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp),
            )
        }
        if (tall) {
            Column(Modifier.fillMaxSize(), content = content)
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
        }
    }
}

@Composable
private fun ColumnScope.TypingLayout(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    onFocusChanged: (Boolean) -> Unit,
    onAddPhoto: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedbackIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate back", actions.onBack)
        Text("Share your thoughts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box(Modifier.padding(end = 8.dp)) {
            FeedbackTypeMenuPill(selected = state.category, onSelect = actions.onCategorySelected)
        }
    }
    FeedbackMessageCard(
        value = state.message,
        onValueChange = actions.onMessageChanged,
        placeholder = state.category.placeholder,
        maxChars = state.meta.maxBody.coerceAtMost(FeedbackViewModel.MAX_MESSAGE),
        onFocusChanged = onFocusChanged,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().weight(1f),
    )
    state.errorMessage?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    FeedbackTypingBar(
        state = state,
        onAddPhoto = onAddPhoto,
        onToggleDiagnostics = { actions.onAttachDiagnosticsChanged(!state.attachDiagnostics) },
        onSend = actions.onSubmit,
    )
}

@Composable
private fun TopRow(state: FeedbackUiState, actions: FeedbackComposeActions) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FeedbackIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate back", actions.onBack)
        Box {
            FeedbackIconButton(Icons.Rounded.MoreVert, "More options", { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Preview diagnostics") }, onClick = {
                    menuOpen = false
                    actions.onPreviewDiagnostics()
                })
                DropdownMenuItem(text = { Text("Rate on Play") }, onClick = {
                    menuOpen = false
                    AnalyticsHelper.trackStoreReviewOpened(method = "store_page", source = "feedback_screen")
                    openPlayStorePage(context)
                })
                DropdownMenuItem(text = { Text("Report on GitHub") }, onClick = {
                    menuOpen = false
                    uriHandler.openUri(buildFeedbackGitHubIssueUrl(state))
                })
                if (state.message.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Discard draft") }, onClick = {
                        menuOpen = false
                        actions.onDiscardDraft()
                    })
                }
            }
        }
    }
}

/** The app's Play listing: the store app when there is one, else the web page. */
internal fun openPlayStorePage(context: android.content.Context) {
    val pkg = context.packageName
    val tries = listOf(Uri.parse("market://details?id=$pkg"), Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
    for (uri in tries) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {
            // Try the next way, or do nothing when neither store nor browser exists.
        }
    }
}
