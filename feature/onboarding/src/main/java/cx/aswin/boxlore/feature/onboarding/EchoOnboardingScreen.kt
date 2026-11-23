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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import kotlin.math.roundToInt

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
            val tierHeight = maxHeight - EchoFooterAllowance
            val metrics = EchoLayout.metrics(tierHeight, LocalDensity.current.fontScale, keyboardOpen = imeVisible)
            // Set by the body's fit (planned while the body is measured): on the tightest windows Something
            // else waits beside the main button. The footer, measured first, takes it on the next frame.
            var somethingElseInFooter by remember { mutableStateOf(false) }
            ClearFocusWhenKeyboardCloses(imeVisible)
            Column(Modifier.fillMaxSize().imePadding()) {
                EchoConversationBody(
                    EchoBodyInput(uiState, kind, tierHeight, metrics, imeVisible),
                    paused = menuOpen || aboutOpen,
                    actions,
                    callbacks,
                    preview,
                    onSomethingElseInFooter = { somethingElseInFooter = it },
                )
                AnimatedContent(
                    targetState = kind == EchoScreenKind.Deck,
                    transitionSpec = EchoMotion.handoff(),
                    contentAlignment = Alignment.BottomCenter,
                    label = "echoFooter",
                ) { deck ->
                    if (deck) {
                        EchoDeckReactions(uiState.aiDeck.reactions.size, metrics, callbacks.onDeckReaction)
                    } else {
                        val leading = if (somethingElseInFooter && kind == EchoScreenKind.Asking) actions::startWriting else null
                        EchoFooterBar(footer, actions::act, somethingElse = leading)
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

/** What the body needs from the screen to plan its fit. */
private data class EchoBodyInput(
    val uiState: OnboardingUiState,
    val kind: EchoScreenKind,
    val tierHeight: Dp,
    val metrics: EchoLayoutMetrics,
    val keyboardOpen: Boolean,
)

/**
 * Echo, the question and the answers (or the ready stage), above the footer. The fit is planned from
 * the body's own room as it is without the keyboard, so opening it never changes the layout step.
 */
@Composable
private fun ColumnScope.EchoConversationBody(
    input: EchoBodyInput,
    paused: Boolean,
    actions: EchoScreenActions,
    callbacks: EchoCallbacks,
    preview: EchoShowPreview?,
    onSomethingElseInFooter: (Boolean) -> Unit,
) {
    val uiState = input.uiState
    val kind = input.kind
    if (kind == EchoScreenKind.Ready) {
        SideEffect { onSomethingElseInFooter(false) }
        EchoReadyStage(uiState, input.metrics, paused = paused, modifier = Modifier.weight(1f).padding(horizontal = 24.dp))
        return
    }
    val density = LocalDensity.current
    val keyboardExtra = with(density) {
        (WindowInsets.ime.getBottom(this) - WindowInsets.navigationBars.getBottom(this)).coerceAtLeast(0).toDp()
    }
    val shape = echoAnswerShape(uiState, kind)
    val measure = rememberEchoFitMeasure(rememberEchoFitText(uiState, kind))
    // No scrolling: Echo, the question and the answers, with spare height shared out.
    val stretch = kind == EchoScreenKind.Asking || kind == EchoScreenKind.Waiting
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
        val body = DpSize(maxWidth, (maxHeight + keyboardExtra).value.roundToInt().dp)
        val planned = remember(body, input.tierHeight, density.fontScale, shape, measure) {
            echoFitPlan(body, input.tierHeight, density.fontScale, keyboardOpen = false, shape, measure)
        }
        val fit = if (input.keyboardOpen) planned.copy(metrics = planned.metrics.copy(showEcho = false)) else planned
        SideEffect { onSomethingElseInFooter(fit.somethingElseInFooter) }
        EchoBodyLayout(
            fit = fit,
            stretch = stretch,
            modifier = Modifier.fillMaxSize(),
            header = {
                // The body's own slot, not the screen column's.
                androidx.compose.animation.AnimatedVisibility(
                    visible = fit.metrics.showEcho,
                    enter = fadeIn(tween(200, delayMillis = 60)) + expandVertically(spring(stiffness = 420f, dampingRatio = 0.9f)),
                    exit = fadeOut(tween(100)) + shrinkVertically(spring(stiffness = 420f, dampingRatio = 0.9f)),
                ) {
                    EchoHeader(uiState, kind, fit.metrics, paused = paused)
                }
            },
            question = { EchoQuestionArea(uiState, kind, fit.metrics) { suggestion -> callbacks.onSearch(suggestion) } },
            answers = {
                EchoAnswerArea(
                    uiState,
                    kind,
                    fit,
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
}

/** Holds the last settled footer; written during composition, never read as state. */
private class EchoFooterMemory {
    var last: EchoFooter? = null
}

@Composable
private fun EchoFinishConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("onboarding_echo_finish_confirmation")
    BoxloreAlertDialog(
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
 * Echo, the question and the answers, never scrolling. The question gets its full planned height
 * first ([EchoFit.questionHeight]), then the answers their minimum, and they never reach under the
 * footer. Spare height is shared out: cards grow into part of it ([EchoLayout.GrowShare], up to
 * [EchoLayout.MaxGrowPerRow] each), a little goes above Echo, more between the question and the
 * answers (at most [EchoLayout.MaxBetweenGap]) and the rest below, so the group sits just under the
 * middle. Beside the answers ([EchoFit.sideBySide]), Echo and the question stand centred in a start
 * column. The three slots stay the same in both arrangements, so focus, drafts and the keyboard
 * survive a switch. [stretch] is false when the answer slot holds a text box or nothing, which keeps
 * its own height.
 */
@Composable
private fun EchoBodyLayout(
    fit: EchoFit,
    stretch: Boolean,
    modifier: Modifier,
    header: @Composable () -> Unit,
    question: @Composable () -> Unit,
    answers: @Composable () -> Unit,
) {
    // Read in order whatever the arrangement: Echo, then the question, then the answers, each as a group,
    // so beside the answers TalkBack doesn't weave the answer rows in among Echo and the question.
    val slots = listOf(header, question, answers).mapIndexed { index, slot ->
        @Composable {
            val group = Modifier.semantics {
                isTraversalGroup = true
                traversalIndex = index.toFloat()
            }
            Box(group.testTag(EchoBodySlotTags[index]), propagateMinConstraints = true) { slot() }
        }
    }
    Layout(contents = slots, modifier = modifier) { (headerParts, questionParts, answerParts), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gapPx = fit.metrics.gap.roundToPx()
        val columnGap = SideBySideGap.dp.roundToPx()
        val startWidth = if (fit.sideBySide) ((width - columnGap) * fit.startShare).toInt() else width
        val answersWidth = if (fit.sideBySide) width - startWidth - columnGap else width
        val loose = Constraints(maxWidth = startWidth, maxHeight = height)
        val headers = headerParts.map { it.measure(loose) }
        val headerHeight = headers.maxOfOrNull { it.height } ?: 0
        val beforeQuestion = headerHeight + if (headerHeight > 0) gapPx else 0
        val minAnswers = fit.answersMinHeight?.takeIf { stretch }?.roundToPx()
        // The question gets its planned height first, then whatever the answers leave.
        val besideQuestion = if (fit.sideBySide) 0 else (minAnswers ?: 0) + gapPx
        val questionRoom = maxOf(height - beforeQuestion - besideQuestion, fit.questionHeight.roundToPx()).coerceIn(0, height)
        val questions = questionParts.map { it.measure(loose.copy(maxHeight = questionRoom)) }
        val questionHeight = questions.maxOfOrNull { it.height } ?: 0
        val slots = EchoBodySlots(headers, questions, beforeQuestion, questionHeight, gapPx)
        val room = if (fit.sideBySide) height else (height - slots.used).coerceAtLeast(0)
        val answerPlaceables = answerParts.map { it.measure(echoAnswerConstraints(answersWidth, room, minAnswers, fit.growRows * EchoLayout.MaxGrowPerRow.roundToPx())) }
        layout(width, height) {
            if (fit.sideBySide) placeBeside(slots, answerPlaceables, width, height) else placeStacked(slots, answerPlaceables, height, EchoLayout.MaxBetweenGap.roundToPx())
        }
    }
}

/** Test tags of the body's three slots, in reading order. */
internal val EchoBodySlotTags = listOf("echo_slot_header", "echo_slot_question", "echo_slot_answers")

/** Echo and the question, measured, with the room they take above the answers. */
private class EchoBodySlots(
    val headers: List<Placeable>,
    val questions: List<Placeable>,
    val beforeQuestion: Int,
    val questionHeight: Int,
    val gap: Int,
) {
    val used: Int get() = beforeQuestion + questionHeight + gap
}

/** The answers' slot: a fixed height (their minimum plus a share of the spare room), or what they need. */
private fun echoAnswerConstraints(width: Int, room: Int, minAnswers: Int?, maxGrowth: Int): Constraints {
    if (minAnswers == null) return Constraints(maxWidth = width, maxHeight = room)
    val spare = (room - minAnswers).coerceAtLeast(0)
    val growth = minOf((spare * EchoLayout.GrowShare).toInt(), maxGrowth)
    return Constraints.fixed(width, minOf(minAnswers + growth, room))
}

/** One column: a little spare room above Echo, more (capped) between the question and the answers, the rest below. */
private fun Placeable.PlacementScope.placeStacked(slots: EchoBodySlots, answers: List<Placeable>, height: Int, maxBetween: Int) {
    val answersHeight = answers.maxOfOrNull { it.height } ?: 0
    val free = (height - slots.used - answersHeight).coerceAtLeast(0)
    val share = (free * EchoLayout.SpareBetweenShare).toInt()
    val between = minOf(share, maxBetween)
    var y = (free * EchoLayout.SpareAboveShare).toInt() + (share - between) / 2
    slots.headers.forEach { it.placeRelative(0, y) }
    y += slots.beforeQuestion
    slots.questions.forEach { it.placeRelative(0, y) }
    y += slots.questionHeight + slots.gap + between
    answers.forEach { it.placeRelative(0, y) }
}

/** Echo and the question centred in the start column, the answers centred in the end column. */
private fun Placeable.PlacementScope.placeBeside(slots: EchoBodySlots, answers: List<Placeable>, width: Int, height: Int) {
    var y = ((height - slots.beforeQuestion - slots.questionHeight) / 2).coerceAtLeast(0)
    slots.headers.forEach { it.placeRelative(0, y) }
    y += slots.beforeQuestion
    slots.questions.forEach { it.placeRelative(0, y) }
    answers.forEach { it.placeRelative(width - it.width, ((height - it.height) / 2).coerceAtLeast(0)) }
}
