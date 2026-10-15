package cx.aswin.boxlore.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth

/** Room kept for the footer (pill + main button) when picking the layout tier. */
private val EchoFooterAllowance = 128.dp

/** Echo's conversation callbacks, kept as a group so the route wiring stays readable. */
internal data class EchoCallbacks(
    val onBack: () -> Unit,
    val onCancelRequest: () -> Unit,
    val onOptionToggle: (String) -> Unit,
    val onCustomInputChange: (String) -> Unit,
    val onContinue: () -> Unit,
    val onRevealSuggestions: () -> Unit,
    val onRetry: () -> Unit,
    val onPickTopics: () -> Unit,
    val onFindShows: () -> Unit,
    val onChangeAnswers: () -> Unit,
    val onSearch: (String?) -> Unit,
    val onDeckReaction: (EchoDeckReaction) -> Unit = {},
    val onDeckBack: () -> Unit = {},
)

/**
 * Echo's conversation: avatar and reaction, one question, its answers, and a footer of buttons.
 * Each area keeps its place on every turn; only its contents change. Status lives where the
 * next question will appear (a placeholder in its shape) and in the top bar's progress line.
 */
@Composable
internal fun EchoOnboardingScreen(uiState: OnboardingUiState, callbacks: EchoCallbacks, deckClips: EchoShowPreview? = null) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)
    var writing by rememberSaveable(uiState.aiCurrentTurn, uiState.aiAssistantMessage) { mutableStateOf(uiState.aiCustomInputText.isNotBlank()) }
    val kind = EchoScreenModel.kind(uiState, writing)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val preview = rememberEchoDeckPreview(uiState, kind, deckClips)
    val actions = EchoScreenActions(
        callbacks = callbacks,
        dismissKeyboard = {
            focus.clearFocus()
            keyboard?.hide()
        },
        setWriting = { writing = it },
        confirmFinish = { confirmFinish = true },
        selectedOptions = { uiState.aiSelectedOptions },
    )
    val footer = rememberEchoFooter(uiState, kind)
    val handleBack = { actions.back(kind, imeVisible) }
    BackHandler(onBack = handleBack)
    if (confirmFinish) {
        EchoFinishConfirmation(
            onConfirm = {
                confirmFinish = false
                callbacks.onFindShows()
            },
            onDismiss = { confirmFinish = false },
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("echo_onboarding_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EchoTopBar(
                progress = EchoScreenModel.progress(uiState, kind),
                busy = kind == EchoScreenKind.Waiting,
                searchEnabled = kind != EchoScreenKind.Waiting,
                actions = EchoTopBarActions(onBack = handleBack, onSearch = { callbacks.onSearch(null) }, onPickTopics = callbacks.onPickTopics),
                menu = EchoMenuState(menuOpen, { menuOpen = it }, aboutOpen, { aboutOpen = it }),
            )
        },
    ) { padding ->
        // Tiers come from the whole content height (before the keyboard and the footer), so sizes
        // never change between turns; the open keyboard only folds Echo away.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).boxloreCardWidth(BoxloreCardFamily.Utility)) {
            val metrics = EchoLayout.metrics(maxHeight - EchoFooterAllowance, LocalDensity.current.fontScale, keyboardOpen = imeVisible)
            ClearFocusWhenKeyboardCloses(imeVisible)
            Column(Modifier.fillMaxSize().imePadding()) {
                EchoConversationBody(uiState, kind, metrics, paused = menuOpen || aboutOpen, actions, callbacks, preview)
                AnimatedContent(
                    targetState = kind == EchoScreenKind.Deck,
                    transitionSpec = EchoMotion.handoff(),
                    contentAlignment = Alignment.BottomCenter,
                    label = "Echo footer",
                ) { deck ->
                    if (deck) {
                        EchoDeckReactions(uiState.aiDeck.reactions.size, metrics, callbacks.onDeckReaction)
                    } else {
                        EchoFooterBar(footer, actions::act)
                    }
                }
            }
            EchoPickedNote(uiState.aiDeck, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        }
    }
}

/** Back and footer actions, apart from the screen so each stays easy to follow. */
private class EchoScreenActions(
    private val callbacks: EchoCallbacks,
    private val dismissKeyboard: () -> Unit,
    private val setWriting: (Boolean) -> Unit,
    private val confirmFinish: () -> Unit,
    private val selectedOptions: () -> Set<String>,
) {
    fun useChoices() {
        dismissKeyboard()
        callbacks.onCustomInputChange("")
        setWriting(false)
    }

    fun startWriting() {
        selectedOptions().forEach(callbacks.onOptionToggle)
        setWriting(true)
    }

    fun back(kind: EchoScreenKind, imeVisible: Boolean) {
        when {
            imeVisible -> dismissKeyboard()
            // Undo a send: the request stops and the answered question returns, answer included.
            kind == EchoScreenKind.Waiting || kind == EchoScreenKind.Error -> callbacks.onCancelRequest()
            kind == EchoScreenKind.Writing -> useChoices()
            kind == EchoScreenKind.Ready -> callbacks.onChangeAnswers()
            kind == EchoScreenKind.Deck -> callbacks.onDeckBack()
            else -> callbacks.onBack()
        }
    }

    fun act(action: EchoAction) {
        dismissKeyboard()
        when (action) {
            EchoAction.Continue -> callbacks.onContinue()
            EchoAction.FindShows -> callbacks.onFindShows()
            // Leaving early is a choice, so it gets a short confirmation first.
            EchoAction.FindShowsNow -> confirmFinish()
            EchoAction.SeeShows -> callbacks.onRevealSuggestions()
            EchoAction.TryAgain -> callbacks.onRetry()
            EchoAction.PickTopics -> callbacks.onPickTopics()
            EchoAction.TellMore -> startWriting()
            EchoAction.ChangeAnswers -> callbacks.onChangeAnswers()
        }
    }
}

/** Clips play only while a show card is up; a new card waits for its own tap, so nothing starts by itself. */
@Composable
private fun rememberEchoDeckPreview(uiState: OnboardingUiState, kind: EchoScreenKind, deckClips: EchoShowPreview?): EchoShowPreview? {
    val preview = if (kind == EchoScreenKind.Deck) rememberEchoShowPreview(deckClips) else null
    val card = uiState.aiDeck.current
    LaunchedEffect(preview, card?.id) {
        if (preview != null && card != null) preview.load(EchoDeckLogic.clip(card))
    }
    return preview
}

/** While Echo works, the footer keeps the layout the listener last saw. */
@Composable
private fun rememberEchoFooter(uiState: OnboardingUiState, kind: EchoScreenKind): EchoFooter {
    val memory = remember { EchoFooterMemory() }
    val current = EchoScreenModel.footer(uiState, kind)
    return when (kind) {
        EchoScreenKind.Waiting -> EchoScreenModel.busyFooter(memory.last)
        EchoScreenKind.Deck -> current
        else -> current.also { memory.last = it }
    }
}

/** Keyboard closed: the field leaves its typing state while Echo slides back in. */
@Composable
private fun ClearFocusWhenKeyboardCloses(imeVisible: Boolean) {
    val focus = LocalFocusManager.current
    var keyboardWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (keyboardWasOpen && !imeVisible) focus.clearFocus()
        keyboardWasOpen = imeVisible
    }
}

/** Echo, the question and the answers (or the ready stage), above the footer. */
@Composable
private fun ColumnScope.EchoConversationBody(
    uiState: OnboardingUiState,
    kind: EchoScreenKind,
    metrics: EchoLayoutMetrics,
    paused: Boolean,
    actions: EchoScreenActions,
    callbacks: EchoCallbacks,
    preview: EchoShowPreview?,
) {
    if (kind == EchoScreenKind.Ready) {
        EchoReadyStage(uiState, metrics, paused = paused, modifier = Modifier.weight(1f).padding(horizontal = 24.dp))
        return
    }
    // No scrolling: Echo, the question and the answers, with spare height shared out.
    val stretch = kind == EchoScreenKind.Asking || kind == EchoScreenKind.Waiting
    val baseRow = if (EchoScreenModel.isOpeningTurn(uiState) && kind == EchoScreenKind.Asking) metrics.tileHeight else metrics.rowHeight
    EchoBodyLayout(
        gap = metrics.gap,
        answersMinHeight = if (stretch) EchoLayout.answersMinHeight(baseRow) else null,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
        header = {
            AnimatedVisibility(
                visible = metrics.showEcho,
                enter = fadeIn(tween(200, delayMillis = 60)) + expandVertically(spring(stiffness = 420f, dampingRatio = 0.9f)),
                exit = fadeOut(tween(100)) + shrinkVertically(spring(stiffness = 420f, dampingRatio = 0.9f)),
            ) {
                EchoHeader(uiState, kind, metrics, paused = paused)
            }
        },
        question = { EchoQuestionArea(uiState, kind, metrics) { suggestion -> callbacks.onSearch(suggestion) } },
        answers = {
            EchoAnswerArea(
                uiState,
                kind,
                metrics,
                EchoAnswerCallbacks(
                    toggle = callbacks.onOptionToggle,
                    changeDraft = callbacks.onCustomInputChange,
                    startWriting = actions::startWriting,
                    useChoices = actions::useChoices,
                    submit = { actions.act(EchoAction.Continue) },
                ),
                preview = preview,
            )
        },
    )
}

/** Holds the last settled footer; written during composition, never read as state. */
private class EchoFooterMemory {
    var last: EchoFooter? = null
}

@Composable
private fun EchoFinishConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("echo_finish_confirmation"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.echo_confirm_title)) },
        text = { Text(stringResource(R.string.echo_confirm_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.echo_find_shows)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.echo_keep_answering)) } },
    )
}

/**
 * Echo, the question and the answers, never scrolling. The answers get their minimum height first,
 * the question the room left (its type shrinks to fit), then the spare height is shared out: cards
 * grow into part of it ([EchoLayout.GrowShare], up to [EchoLayout.MaxGrowPerRow] each), and the
 * rest goes a little above Echo, mostly between the question and the answers, and the remainder
 * below, so the answers sit just under the middle. [answersMinHeight] is null when the answer slot
 * holds a text box or nothing, which keeps its own height.
 */
@Composable
private fun EchoBodyLayout(
    gap: Dp,
    answersMinHeight: Dp?,
    modifier: Modifier,
    header: @Composable () -> Unit,
    question: @Composable () -> Unit,
    answers: @Composable () -> Unit,
) {
    Layout(contents = listOf(header, question, answers), modifier = modifier) { (headerParts, questionParts, answerParts), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width, maxHeight = height)
        val gapPx = gap.roundToPx()
        val headers = headerParts.map { it.measure(loose) }
        val headerHeight = headers.maxOfOrNull { it.height } ?: 0
        val headerGap = if (headerHeight > 0) gapPx else 0
        val minAnswers = answersMinHeight?.roundToPx() ?: 0
        val questionRoom = (height - headerHeight - headerGap - minAnswers - gapPx).coerceAtLeast(0)
        val questions = questionParts.map { it.measure(loose.copy(maxHeight = questionRoom)) }
        val questionHeight = questions.maxOfOrNull { it.height } ?: 0
        val answerPlaceables = if (answersMinHeight != null) {
            val spare = (height - headerHeight - headerGap - questionHeight - gapPx - minAnswers).coerceAtLeast(0)
            val growth = minOf((spare * EchoLayout.GrowShare).toInt(), EchoLayout.AnswerRows * EchoLayout.MaxGrowPerRow.roundToPx())
            answerParts.map { it.measure(Constraints.fixed(width, minAnswers + growth)) }
        } else {
            answerParts.map { it.measure(loose.copy(maxHeight = (height - headerHeight - headerGap - questionHeight - gapPx).coerceAtLeast(0))) }
        }
        val answersHeight = answerPlaceables.maxOfOrNull { it.height } ?: 0
        val free = (height - headerHeight - headerGap - questionHeight - gapPx - answersHeight).coerceAtLeast(0)
        layout(width, height) {
            var y = (free * EchoLayout.SpareAboveShare).toInt()
            headers.forEach { it.placeRelative(0, y) }
            y += headerHeight + headerGap
            questions.forEach { it.placeRelative(0, y) }
            y += questionHeight + gapPx + (free * EchoLayout.SpareBetweenShare).toInt()
            answerPlaceables.forEach { it.placeRelative(0, y) }
        }
    }
}
