package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.boxloreSheetIsCentered
import cx.aswin.boxlore.core.designsystem.echo.EchoBeat
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.EchoIdle
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.home.NpsScoreBand
import cx.aswin.boxlore.feature.home.NpsSurveyCopy
import cx.aswin.boxlore.feature.home.NpsSurveyDefinition
import cx.aswin.boxlore.feature.home.NpsSurveySession
import cx.aswin.boxlore.feature.home.NpsSurveyStep

/**
 * The NPS survey: a 0–10 score, one follow-up chosen by the score, then thanks (9s and 10s are offered
 * the Play Store page). Echo stands on the sheet's top edge and acts out each step, then rests with its
 * eyes alive. The sheet keeps the app's own colours in every theme, rises above the keyboard (Echo shrinks
 * and the score chip and helper make room), scrolls on short windows, and is a centered dialog on
 * expanded and folded tablets. Before a score and after the answer it closes like any sheet; on the
 * follow-up only Skip, Send or back close it, so a typed answer isn't lost to a stray tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun NpsSurveySheet(
    session: NpsSurveySession,
    copy: NpsSurveyCopy,
    onScore: (Int) -> Unit,
    onFollowUpDone: (String) -> Unit,
    onClose: () -> Unit,
    onRateOnPlay: () -> Unit,
) {
    TrackScreenView("nps_survey", send = {}) // trail only: the survey is counted by `survey shown`
    val step by rememberUpdatedState(session.step)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || step != NpsSurveyStep.FollowUp },
    )
    var answer by rememberSaveable(session.submissionId) { mutableStateOf("") }
    val centered = boxloreSheetIsCentered()
    // Typing: the answer box has focus and a keyboard really takes space (insets can claim an IME before any arrives).
    val answerInteraction = remember { MutableInteractionSource() }
    val answerFocused by answerInteraction.collectIsFocusedAsState()
    val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val typing = answerFocused && keyboardUp && session.step == NpsSurveyStep.FollowUp
    val shortWindow = LocalBoxloreWindowLayout.current.heightDp < NPS_SHORT_WINDOW_DP
    val echoSize by animateDpAsState(npsEchoSize(typing, shortWindow), label = "npsEchoSize")
    val above = echoSize * NPS_ECHO_ABOVE_SHEET

    BoxloreModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true),
        centeredDismissAllowed = session.step != NpsSurveyStep.FollowUp,
    ) {
        Box(Modifier.fillMaxWidth().testTag(NPS_SHEET_TAG)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = above),
                shape = if (centered) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = npsSheetColor(),
            ) {
                Column(
                    modifier = Modifier
                        .then(if (centered) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)))
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = echoSize - above + 4.dp, bottom = if (typing) 16.dp else 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = session.step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "npsStep",
                    ) { shown ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (typing) 12.dp else 14.dp),
                        ) {
                            when (shown) {
                                NpsSurveyStep.Score -> ScoreStep(copy, onScore = onScore, onNotNow = onClose)
                                NpsSurveyStep.FollowUp -> FollowUpStep(
                                    copy = copy,
                                    score = session.score ?: 0,
                                    answer = answer,
                                    onAnswerChange = { answer = it.take(NpsSurveyDefinition.MAX_ANSWER_LENGTH) },
                                    typing = typing,
                                    answerInteraction = answerInteraction,
                                    onDone = onFollowUpDone,
                                )
                                NpsSurveyStep.Thanks -> ThanksStep(copy, session.band ?: NpsScoreBand.Middle, onDone = onClose, onRateOnPlay = onRateOnPlay)
                            }
                        }
                    }
                }
            }
            EchoCharacter(
                sequence = npsEchoSequence(session.step, session.band),
                modifier = Modifier.align(Alignment.TopCenter).size(echoSize),
                idle = npsEchoIdle(session.step, session.band),
                prefetch = NPS_ECHO_PREFETCH,
            )
        }
    }
}

@Composable
private fun ScoreStep(copy: NpsSurveyCopy, onScore: (Int) -> Unit, onNotNow: () -> Unit) {
    Text(
        text = copy.eyebrow.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
    )
    Title(copy.question)
    ScoreGrid(copy, onScore, Modifier.padding(top = 6.dp))
    TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) { Text(copy.notNow) }
}

@Composable
@Suppress("LongParameterList")
private fun FollowUpStep(
    copy: NpsSurveyCopy,
    score: Int,
    answer: String,
    onAnswerChange: (String) -> Unit,
    typing: Boolean,
    answerInteraction: MutableInteractionSource,
    onDone: (String) -> Unit,
) {
    val band = NpsScoreBand.of(score)
    if (!typing) ScoreChip(copy.picked(score))
    Title(copy.followUpTitle(band), small = typing)
    if (!typing) {
        when (band) {
            NpsScoreBand.Low -> Helper(copy.fixHelper)
            NpsScoreBand.Middle -> Helper(copy.tenHelper)
            NpsScoreBand.High -> if (copy.ideaTag.isNotBlank()) IdeaTag(copy.ideaTag)
        }
    }
    val idea = band == NpsScoreBand.High
    AnswerField(
        value = answer,
        onValueChange = onAnswerChange,
        label = if (idea) copy.ideaLabel else copy.answerLabel,
        hint = if (idea) copy.ideaHint else copy.answerHint,
        interaction = answerInteraction,
        modifier = Modifier.padding(top = if (typing) 0.dp else 4.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        TextButton(onClick = { onDone("") }, modifier = Modifier.heightIn(min = 48.dp)) { Text(copy.skip) }
        Button(onClick = { onDone(answer) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (idea) copy.sendIdea else copy.send) }
    }
}

@Composable
private fun ThanksStep(copy: NpsSurveyCopy, band: NpsScoreBand, onDone: () -> Unit, onRateOnPlay: () -> Unit) {
    val (title, body) = when (band) {
        NpsScoreBand.Low -> copy.thanksLowTitle to copy.thanksLowBody
        NpsScoreBand.Middle -> copy.thanksMiddleTitle to copy.thanksMiddleBody
        NpsScoreBand.High -> copy.thanksHighTitle to copy.thanksHighBody
    }
    Title(title)
    Helper(body, Modifier.widthIn(max = 310.dp))
    if (band == NpsScoreBand.High) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onRateOnPlay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(copy.rateOnPlay) }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(copy.notNow) }
        }
    } else {
        Button(
            onClick = onDone,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { Text(copy.done) }
    }
}

/** Echo's part in each step: the clip plays once, then Echo rests on its still with its eyes alive. */
internal fun npsEchoSequence(step: NpsSurveyStep, band: NpsScoreBand?): String = when (step) {
    NpsSurveyStep.Score -> "survey-ask"
    NpsSurveyStep.FollowUp -> when (band) {
        NpsScoreBand.Low, null -> "survey-notes"
        NpsScoreBand.Middle -> "survey-think"
        NpsScoreBand.High -> "survey-idea"
    }
    NpsSurveyStep.Thanks -> when (band) {
        NpsScoreBand.Low, null -> "survey-thanks-calm"
        NpsScoreBand.Middle -> "survey-thanks"
        NpsScoreBand.High -> "survey-celebrate"
    }
}

/** White in light themes, as approved; a raised container in dark ones. */
@Composable
private fun npsSheetColor() =
    if (LocalEffectiveDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

/** Echo's eyes at rest: calm after a low score (no smile), otherwise lively. */
private fun npsEchoIdle(step: NpsSurveyStep, band: NpsScoreBand?): EchoIdle =
    if (step == NpsSurveyStep.Thanks && band == NpsScoreBand.Low) NpsEchoCalmIdle else NpsEchoIdle

/** Echo's size: smaller while typing and on short windows, so the answer and its buttons stay in reach. */
internal fun npsEchoSize(typing: Boolean, shortWindow: Boolean): Dp = when {
    typing && shortWindow -> 72.dp
    typing || shortWindow -> 132.dp
    else -> 172.dp
}

/** How much of Echo stands above the sheet's top edge. */
private const val NPS_ECHO_ABOVE_SHEET = 0.535f

/** Below this window height (dp) Echo starts smaller. */
private const val NPS_SHORT_WINDOW_DP = 600f

internal const val NPS_SHEET_TAG = "nps_survey_sheet"
internal const val NPS_ANSWER_TAG = "nps_survey_answer"

private val NPS_ECHO_PREFETCH = listOf("survey-notes", "survey-think", "survey-idea", "survey-thanks", "survey-celebrate")

/** Echo looks at the listener, down at the sheet now and then, and smiles once in a while. */
private val NpsEchoIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(EchoGaze(0f, 0f), 2600, blinks = 1),
        EchoBeat(EchoGaze(0f, 0.8f), 1300, EchoFace.Curious),
        EchoBeat(EchoGaze(0f, 0f), 2200, blinks = 1),
        EchoBeat(EchoGaze(-0.5f, 0.3f), 1100, blinks = 1),
        EchoBeat(EchoGaze(0f, 0f), 2400, EchoFace.Happy),
    ),
)

/** After a low score: Echo just listens, blinking and glancing, with no smile. */
private val NpsEchoCalmIdle = EchoIdle(
    intro = emptyList(),
    loop = listOf(
        EchoBeat(EchoGaze(0f, 0f), 2800, blinks = 1),
        EchoBeat(EchoGaze(0f, 0.6f), 1400),
        EchoBeat(EchoGaze(0f, 0f), 2600, blinks = 2),
    ),
)
