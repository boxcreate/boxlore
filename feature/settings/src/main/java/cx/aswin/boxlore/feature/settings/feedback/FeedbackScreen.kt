package cx.aswin.boxlore.feature.settings.feedback

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.support.SupportRepository
import kotlinx.coroutines.launch

/** Below this height the full layout scrolls instead of stretching the message card. */
internal val FEEDBACK_TALL_ENOUGH = 600.dp
private val FEEDBACK_SHORT_MESSAGE_HEIGHT = 220.dp

/**
 * Feedback: the conversation list once there are conversations, otherwise straight to "Share
 * your thoughts"; one conversation from the list or a reply notification ([openThreadId]). On a
 * landscape tablet the list and the conversation sit side by side.
 */
@Composable
fun FeedbackScreen(
    supportRepository: SupportRepository,
    boxcastPrefs: BoxcastPrefs,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    openThreadId: String? = null,
) {
    TrackScreenView("feedback")
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var pane by rememberSaveable(stateSaver = FeedbackPaneSaver) { mutableStateOf(initialFeedbackPane(openThreadId, supportRepository.hasConversations)) }
    var composeSession by rememberSaveable { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    // Kept here, so the list's scroll position survives it being hidden behind a conversation on phones.
    val listState = rememberLazyListState()
    val threads by supportRepository.threads.collectAsStateWithLifecycle()
    val refresh: () -> Unit = {
        scope.launch { loadFailed = supportRepository.refresh() == null && supportRepository.threads.value == null }
    }
    // The list is fresh each time it shows: replies read or sent elsewhere change its statuses.
    LaunchedEffect(pane == FeedbackPane.List) { if (pane == FeedbackPane.List || supportRepository.threads.value == null) refresh() }
    val split = feedbackUsesSplit(LocalBoxlorePaneWidth.current, LocalBoxloreWindowLayout.current)
    // Side by side, the conversation shown for the list becomes the selection, so narrowing the window keeps it open.
    val newestId = threads?.firstOrNull()?.id
    LaunchedEffect(split, newestId, threads != null) {
        if (split && pane == FeedbackPane.List && threads != null) pane = newestId?.let { FeedbackPane.Thread(it) } ?: FeedbackPane.Compose
    }
    // A reply push brings the list's statuses up to date while Feedback is open.
    LaunchedEffect(supportRepository) { supportRepository.pushedReplies.collect { refresh() } }
    val goBack: () -> Unit = { feedbackBack(pane, supportRepository.hasConversations, split)?.let { pane = it } ?: onBack() }
    BackHandler(onBack = goBack)
    val openCompose: () -> Unit = {
        composeSession += 1
        pane = FeedbackPane.Compose
    }
    val listActions = FeedbackListActions(
        onOpen = { pane = FeedbackPane.Thread(it) },
        onNew = openCompose,
        onRetry = refresh,
        onRate = {
            AnalyticsHelper.trackStoreReviewOpened(method = "store_page", source = "feedback_list")
            openPlayStorePage(context)
        },
        onGitHub = { uriHandler.openUri(FEEDBACK_GITHUB_NEW_ISSUE_URL) },
        onBack = onBack,
    )
    val callbacks = FeedbackDetailCallbacks(
        back = goBack,
        leave = onBack,
        newFeedback = openCompose,
        // A message that became a conversation goes to the list; one through the old form leaves.
        sent = { repliesInApp -> if (repliesInApp) pane = FeedbackPane.List else onBack() },
    )
    FeedbackPanesLayout(
        split = split,
        pane = pane,
        threads = threads,
        list = { listModifier, selectedId -> FeedbackListScreen(threads, loadFailed, selectedId, bottomContentPadding, listActions, listModifier, listState) },
        detail = { detail, showBack -> FeedbackDetailPane(supportRepository, boxcastPrefs, detail, composeSession, bottomContentPadding, callbacks, showBack) },
        modifier = modifier,
    )
}

internal class FeedbackComposeActions(
    val onBack: () -> Unit,
    val onCategorySelected: (String) -> Unit,
    val onMessageChanged: (String) -> Unit,
    val onAttachDiagnosticsChanged: (Boolean) -> Unit,
    val onPhotosPicked: (List<Uri>) -> Unit,
    val onRemovePhoto: (PickedPhoto) -> Unit,
    val onSubmit: () -> Unit,
    val onPreviewDiagnostics: () -> Unit,
    val onDiscardDraft: () -> Unit,
)

/**
 * The compose screen until the message is sent, then [FeedbackSentScreen], whose Done calls [onDone].
 * [imeBottom] is the keyboard's height (tests pass their own).
 */
@Composable
internal fun FeedbackContent(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    boxcastPrefs: BoxcastPrefs,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
    imeBottom: Dp = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    onDone: () -> Unit = actions.onBack,
) {
    if (state.isSuccess) {
        FeedbackSentScreen(
            boxcastPrefs = boxcastPrefs,
            repliesInApp = state.repliesInApp,
            photosDropped = state.photosDropped,
            onDone = onDone,
            bottomContentPadding = bottomContentPadding,
            modifier = modifier,
        )
    } else {
        FeedbackComposeScreen(state = state, actions = actions, bottomContentPadding = bottomContentPadding, imeBottom = imeBottom, modifier = modifier)
    }
}

/** True while the listener is writing: the message has focus and the keyboard is up. */
internal fun isTypingLayout(messageFocused: Boolean, imeBottom: Dp): Boolean = messageFocused && imeBottom > 0.dp

/** Space under the content: the keyboard while it's up, otherwise the app's bottom chrome and the system bar. */
internal fun feedbackBottomPadding(imeBottom: Dp, chrome: Dp, systemBar: Dp): Dp = if (imeBottom > 0.dp) imeBottom else chrome + systemBar

/**
 * One layout for every state, so the message field is never replaced: the keyboard coming up
 * (the typing layout) or the window getting short (scrolling) only changes what sits around it,
 * and the field keeps its focus, cursor and the text being composed.
 */
@Composable
internal fun FeedbackComposeScreen(
    state: FeedbackUiState,
    actions: FeedbackComposeActions,
    bottomContentPadding: Dp,
    imeBottom: Dp,
    modifier: Modifier = Modifier,
) {
    var messageFocused by rememberSaveable { mutableStateOf(false) }
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val typing = isTypingLayout(messageFocused, imeBottom)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = state.meta.maxPhotos.coerceAtLeast(2)),
    ) { uris -> actions.onPhotosPicked(uris) }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val uriHandler = LocalUriHandler.current
    val scroll = rememberScrollState()
    val editable = !state.isSubmitting

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = feedbackBottomPadding(imeBottom, bottomContentPadding, systemBar)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
                if (typing) TypingHeader(state, actions) else TopRow(state, actions)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // While typing the field takes the room the keyboard leaves; a short window otherwise scrolls.
                    val stretch = typing || maxHeight >= FEEDBACK_TALL_ENOUGH
                    Column(Modifier.fillMaxSize().then(if (stretch) Modifier else Modifier.verticalScroll(scroll))) {
                        if (!typing) Intro(state)
                        if (!typing) {
                            FeedbackTypePills(
                                choices = state.choices,
                                selected = state.choice,
                                enabled = editable,
                                onSelect = actions.onCategorySelected,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                            )
                        }
                        FeedbackMessageCard(
                            value = state.message,
                            onValueChange = actions.onMessageChanged,
                            placeholder = state.choice.placeholder,
                            maxChars = state.meta.maxBody,
                            readOnly = !editable,
                            onFocusChanged = { messageFocused = it },
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, top = if (typing) 8.dp else 16.dp)
                                .fillMaxWidth()
                                .then(if (stretch) Modifier.weight(1f) else Modifier.height(FEEDBACK_SHORT_MESSAGE_HEIGHT)),
                        )
                        if (!typing) AttachRows(state, onAddPhoto = pickPhotos, actions = actions)
                        (state.errorMessage ?: state.limitMessage)?.let { error -> ErrorLine(error, small = typing) }
                        if (!typing) SendFooter(state, onSubmit = actions.onSubmit, onPrivacy = { uriHandler.openUri(FEEDBACK_PRIVACY_URL) })
                    }
                }
                if (typing) {
                    FeedbackTypingBar(
                        state = state,
                        onAddPhoto = pickPhotos,
                        onToggleDiagnostics = { actions.onAttachDiagnosticsChanged(!state.attachDiagnostics) },
                        onSend = actions.onSubmit,
                    )
                }
            }
        }
    }
}

/** Photos (while the server allows them) and the diagnostics switch, under the message. */
@Composable
private fun AttachRows(state: FeedbackUiState, onAddPhoto: () -> Unit, actions: FeedbackComposeActions) {
    if (state.meta.photosEnabled) {
        FeedbackPhotoRow(
            photos = state.photos,
            canAdd = state.canAddPhotos,
            canRemove = !state.isSubmitting,
            maxPhotos = state.meta.maxPhotos,
            onAdd = onAddPhoto,
            onRemove = actions.onRemovePhoto,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
    }
    FeedbackDiagnosticsRow(
        checked = state.attachDiagnostics,
        enabled = !state.isSubmitting,
        onCheckedChange = actions.onAttachDiagnosticsChanged,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun ErrorLine(text: String, small: Boolean) {
    Text(
        text,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun ColumnScope.SendFooter(state: FeedbackUiState, onSubmit: () -> Unit, onPrivacy: () -> Unit) {
    FeedbackSendButton(
        enabled = state.canSend,
        sending = state.isSubmitting,
        onClick = onSubmit,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(64.dp),
    )
    FeedbackPrivacyLink(onOpen = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
}

@Composable
private fun Intro(state: FeedbackUiState) {
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
}

@Composable
private fun TypingHeader(state: FeedbackUiState, actions: FeedbackComposeActions) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedbackIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate back", actions.onBack)
        Text("Share your thoughts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box(Modifier.padding(end = 8.dp)) {
            FeedbackTypeMenuPill(choices = state.choices, selected = state.choice, enabled = !state.isSubmitting, onSelect = actions.onCategorySelected)
        }
    }
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
