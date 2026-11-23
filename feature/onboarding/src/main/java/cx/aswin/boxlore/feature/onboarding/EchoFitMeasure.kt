package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The text a turn's fit is planned for, in the styles the screen draws it with. */
internal data class EchoFitText(
    /** The question, or null when a placeholder of fixed height stands in its place. */
    val question: String?,
    /** Height under the question (the search suggestion), or the placeholder's height. */
    val questionExtraDp: Float,
    val speech: String?,
    val labels: List<String>,
    /** Height under what Echo says in its bubble (the wait countdown). */
    val speechExtraDp: Float = 0f,
)

/** The answers a turn shows, for its fit. Writing keeps its turn's arrangement, so typing never moves things. */
internal fun echoAnswerShape(state: OnboardingUiState, kind: EchoScreenKind): EchoAnswerShape = when (kind) {
    EchoScreenKind.Asking, EchoScreenKind.Writing -> when {
        EchoScreenModel.isOpeningTurn(state) -> EchoAnswerShape(EchoAnswerKind.Topics, EchoInitialTopic.entries.size)
        EchoScreenModel.choices(state).isNotEmpty() -> EchoAnswerShape(EchoAnswerKind.Rows, EchoScreenModel.choices(state).size)
        else -> EchoAnswerShape(EchoAnswerKind.Other, 0)
    }
    EchoScreenKind.Waiting -> EchoAnswerShape(EchoAnswerKind.Rows, EchoLayout.AnswerRows)
    else -> EchoAnswerShape(EchoAnswerKind.Other, 0)
}

@Composable
internal fun rememberEchoFitText(state: OnboardingUiState, kind: EchoScreenKind): EchoFitText {
    val opening = EchoScreenModel.isOpeningTurn(state)
    val intro = stringResource(R.string.echo_intro)
    val initialQuestion = stringResource(R.string.echo_initial_question)
    val topicLabels = EchoInitialTopic.entries.map { stringResource(it.labelRes) }
    // While Echo thinks, its bubble may show its longest wait line with the countdown under it.
    val waitLine = stringArrayResource(EchoWaitStep.NextQuestion.lines).maxByOrNull { it.length }
    // An error is said in Echo's bubble, or stands in the question's place with Echo folded away;
    // planned as both, in the larger styles, so either way it fits whole.
    val error = "${stringResource(R.string.echo_error_title)}\n${stringResource(R.string.echo_error_body)}"
    return when (kind) {
        EchoScreenKind.Error -> EchoFitText(question = error, questionExtraDp = 0f, speech = error, labels = emptyList())
        EchoScreenKind.Waiting -> if (state.isSynthesizing) {
            EchoFitText(null, EchoWaitingHeight, null, emptyList())
        } else {
            EchoFitText(null, EchoWaitingHeight, waitLine, emptyList(), speechExtraDp = EchoWaitDetailHeight)
        }
        else -> {
            val suggestion = kind == EchoScreenKind.Asking && !state.aiSearchSuggestion.isNullOrBlank()
            EchoFitText(
                question = EchoScreenModel.question(state) ?: initialQuestion,
                questionExtraDp = if (suggestion) EchoSuggestionHeight else 0f,
                speech = (if (opening) intro else state.aiGuidedTurn?.response)?.takeIf { it.isNotBlank() },
                labels = if (opening) topicLabels else EchoScreenModel.choices(state).map { it.substringBefore('|').trim() },
            )
        }
    }
}

/** [EchoFitMeasure] from real text layout, with the question, bubble and answer styles the screen uses. */
@Composable
internal fun rememberEchoFitMeasure(text: EchoFitText): EchoFitMeasure {
    val measurer = rememberTextMeasurer(cacheSize = 48)
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    return remember(measurer, density, typography, text) {
        TextEchoFitMeasure(
            measurer,
            density,
            text,
            questionStyle = typography.headlineMedium.copy(lineHeight = 1.15.em, lineBreak = LineBreak.Heading),
            bubbleStyle = typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
            labelStyle = typography.bodyLarge.copy(fontSize = 12.sp),
        )
    }
}

private class TextEchoFitMeasure(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val text: EchoFitText,
    private val questionStyle: TextStyle,
    private val bubbleStyle: TextStyle,
    private val labelStyle: TextStyle,
) : EchoFitMeasure {
    // A plan re-runs while the footer changes height; the widths stay the same, so remember answers.
    private val questions = HashMap<Triple<Float, Float, Float>, Float>()
    private val bubbles = HashMap<Pair<Float, Int>, Float>()
    private val labels = HashMap<Pair<Float, Int>, Boolean>()

    override fun questionHeight(widthDp: Float, maxSp: Float, minSp: Float): Float = questions.getOrPut(Triple(widthDp, maxSp, minSp)) {
        val question = text.question ?: return@getOrPut text.questionExtraDp
        var size = maxSp
        var height: Float
        while (true) {
            val layout = measurer.measure(question, questionStyle.copy(fontSize = size.sp), constraints = Constraints(maxWidth = px(widthDp)))
            height = layout.size.height / density.density
            if (layout.lineCount <= EchoQuestionLines || size - 1f < minSp) break
            size -= 1f
        }
        height + text.questionExtraDp
    }

    override fun bubbleHeight(widthDp: Float, lines: Int): Float = bubbles.getOrPut(widthDp to lines) {
        val speech = text.speech ?: return@getOrPut 0f
        bubble(speech, widthDp, lines).size.height / density.density + EchoBubbleChrome + text.speechExtraDp
    }

    override fun bubbleFits(widthDp: Float, lines: Int): Boolean = text.speech?.let { !bubble(it, widthDp, lines).hasVisualOverflow } ?: true

    override fun questionSize(widthDp: Float, maxSp: Float, minSp: Float): Float {
        val question = text.question ?: return maxSp
        var size = maxSp
        while (size - 1f >= minSp) {
            val layout = measurer.measure(question, questionStyle.copy(fontSize = size.sp), constraints = Constraints(maxWidth = px(widthDp)))
            if (layout.lineCount <= EchoQuestionLines) break
            size -= 1f
        }
        return size
    }

    private fun bubble(speech: String, widthDp: Float, lines: Int) = measurer.measure(
        speech,
        bubbleStyle,
        overflow = TextOverflow.Ellipsis,
        maxLines = lines,
        constraints = Constraints(maxWidth = px(widthDp - EchoBubbleHorizontalPadding)),
    )

    override fun labelsFit(widthDp: Float, maxLines: Int): Boolean = labels.getOrPut(widthDp to maxLines) {
        widthDp > 0f &&
            text.labels.all { label ->
            !measurer.measure(label, labelStyle, overflow = TextOverflow.Clip, maxLines = maxLines, constraints = Constraints(maxWidth = px(widthDp))).hasVisualOverflow
        }
    }

    private fun px(dp: Float): Int = (dp * density.density).roundToInt().coerceAtLeast(1)
}

/** The question keeps to this many lines before its type shrinks. */
private const val EchoQuestionLines = 3

/** The question's placeholder while Echo thinks: two 30dp bars and their gap. */
private const val EchoWaitingHeight = 70f

/** The countdown under a wait line: its 4dp gap and a labelLarge line. */
private const val EchoWaitDetailHeight = 24f

/** The search suggestion under a question: its spacing, padding and 48dp button. */
private const val EchoSuggestionHeight = 60f

/** The bubble's side padding, and its top gap, tail and vertical padding. */
private const val EchoBubbleHorizontalPadding = 36f
private const val EchoBubbleChrome = 36f
