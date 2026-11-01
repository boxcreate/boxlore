package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.SurveyQuestionRef
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The PostHog `api` survey boxlore's NPS sheet reports to, with its question IDs there. */
internal object NpsSurveyDefinition {
    const val SURVEY_ID = "01a197f4-4105-0000-4ccb-67748f392475"
    const val SCORE_QUESTION_ID = "7cf85c01-079f-469b-9116-6b2eeb9ed881"
    const val FIX_QUESTION_ID = "39273125-fc72-47e2-b937-2fd018009add"
    const val TEN_QUESTION_ID = "45b27138-239e-4047-a5d0-801889e1dce8"
    const val IDEA_QUESTION_ID = "bf4dde95-a8af-48e5-b320-6228ac2a8c25"

    /** Flag whose payload, a JSON object of [NpsSurveyCopy] keys, changes the wording with no release. */
    const val COPY_FLAG = "nps-survey-copy"

    /** The longest typed answer sent; the field stops there. */
    const val MAX_ANSWER_LENGTH = 500
}

/** How a score is followed up: 0–6, 7–8 and 9–10, as PostHog splits NPS. */
internal enum class NpsScoreBand {
    Low,
    Middle,
    High,
    ;

    companion object {
        fun of(score: Int): NpsScoreBand = when {
            score <= 6 -> Low
            score <= 8 -> Middle
            else -> High
        }
    }
}

internal enum class NpsSurveyStep { Score, FollowUp, Thanks }

/** One survey on screen: its step, the score, and the submission ID every send of it shares. */
internal data class NpsSurveySession(
    val submissionId: String,
    val step: NpsSurveyStep = NpsSurveyStep.Score,
    val score: Int? = null,
    /** Opened by long-pressing Feedback: it sends its events but never uses up the schedule. */
    val manual: Boolean = false,
) {
    val band: NpsScoreBand? get() = score?.let(NpsScoreBand::of)
}

/**
 * The survey's wording. Any key here can be replaced from [NpsSurveyDefinition.COPY_FLAG]'s payload;
 * an empty `ideaTag` hides the tag on the idea step.
 */
internal class NpsSurveyCopy(private val overrides: Map<String, String> = emptyMap()) {
    private fun text(key: String, default: String) = overrides[key] ?: default

    val eyebrow = text("eyebrow", "A quick sound check")
    val question = text("question", "How likely are you to recommend boxlore to a friend?")
    val notLikely = text("notLikely", "Not likely")
    val veryLikely = text("veryLikely", "Very likely")
    val notNow = text("notNow", "Not now")
    private val picked = text("picked", "You picked {score}")
    val fixTitle = text("fixTitle", "What's the one thing we should fix first?")
    val fixHelper = text("fixHelper", "Every answer is read. Be as specific as you like.")
    val tenTitle = text("tenTitle", "What would turn this up to a 10?")
    val tenHelper = text("tenHelper", "A missing feature, a rough edge, anything.")
    val ideaTitle = text("ideaTitle", "What should we queue up next?")
    val ideaTag = text("ideaTag", "Most listener ideas get built within a few weeks")
    val answerLabel = text("answerLabel", "Your answer (optional)")
    val answerHint = text("answerHint", "Type your answer…")
    val ideaLabel = text("ideaLabel", "Your idea (optional)")
    val ideaHint = text("ideaHint", "Type your idea…")
    val skip = text("skip", "Skip")
    val send = text("send", "Send")
    val sendIdea = text("sendIdea", "Send idea")
    val thanksLowTitle = text("thanksLowTitle", "Thank you for telling us")
    val thanksLowBody = text("thanksLowBody", "We read every answer, and yours helps us decide what to fix first.")
    val thanksMiddleTitle = text("thanksMiddleTitle", "Received, loud and clear.")
    val thanksMiddleBody = text("thanksMiddleBody", "Every answer gets a proper listen, and it shapes what comes next.")
    val thanksHighTitle = text("thanksHighTitle", "Music to our ears.")
    val thanksHighBody =
        text("thanksHighBody", "Want to pass the mic? A rating on Google Play helps other listeners tune in to boxlore.")
    val done = text("done", "Done")
    val rateOnPlay = text("rateOnPlay", "Rate on Google Play")

    fun picked(score: Int) = picked.replace("{score}", score.toString())

    /** The follow-up question for [band], as sent with its answer. */
    fun followUpTitle(band: NpsScoreBand) = when (band) {
        NpsScoreBand.Low -> fixTitle
        NpsScoreBand.Middle -> tenTitle
        NpsScoreBand.High -> ideaTitle
    }

    companion object {
        fun followUpQuestionId(band: NpsScoreBand) = when (band) {
            NpsScoreBand.Low -> NpsSurveyDefinition.FIX_QUESTION_ID
            NpsScoreBand.Middle -> NpsSurveyDefinition.TEN_QUESTION_ID
            NpsScoreBand.High -> NpsSurveyDefinition.IDEA_QUESTION_ID
        }
    }
}

/**
 * Runs one NPS survey for Home: which step is on screen, and PostHog's survey events. The score is sent
 * as soon as it is picked (a partial answer), so it is kept if the listener goes no further; the
 * follow-up's Send or Skip completes the same submission. [onScheduledDisplay] uses up the schedule
 * once a decided survey is on screen; [onScored] keeps the score for the review prompt.
 */
internal class NpsSurveyController(
    private val onScheduledDisplay: () -> Unit,
    private val onScored: (Int) -> Unit,
    private val newSubmissionId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _session = MutableStateFlow<NpsSurveySession?>(null)
    val session: StateFlow<NpsSurveySession?> = _session.asStateFlow()
    private val displayed = AtomicBoolean(false)
    var copy: NpsSurveyCopy = NpsSurveyCopy()
        private set

    val isOpen: Boolean get() = _session.value != null

    /** This survey has been on screen. */
    val wasDisplayed: Boolean get() = displayed.get()

    fun open(manual: Boolean, copy: NpsSurveyCopy) {
        if (isOpen) return
        this.copy = copy
        displayed.set(false)
        _session.value = NpsSurveySession(submissionId = newSubmissionId(), manual = manual)
    }

    /** The sheet is on screen with Home resumed: `survey shown`, once per survey. */
    fun onDisplayed() {
        val session = _session.value ?: return
        if (!displayed.compareAndSet(false, true)) return
        AnalyticsHelper.trackSurveyShown(NpsSurveyDefinition.SURVEY_ID)
        if (!session.manual) onScheduledDisplay()
    }

    fun onScore(score: Int) {
        val session = _session.value?.takeIf { it.step == NpsSurveyStep.Score } ?: return
        val scored = session.copy(step = NpsSurveyStep.FollowUp, score = score.coerceIn(0, 10))
        _session.value = scored
        send(scored, answer = null, completed = false)
        onScored(score)
    }

    /** Send (with [answer]) or Skip (blank) on the follow-up: the submission is complete. */
    fun onFollowUpDone(answer: String) {
        val session = _session.value?.takeIf { it.step == NpsSurveyStep.FollowUp } ?: return
        send(session, answer.trim().take(NpsSurveyDefinition.MAX_ANSWER_LENGTH).ifEmpty { null }, completed = true)
        _session.value = session.copy(step = NpsSurveyStep.Thanks)
    }

    /**
     * The listener closed the survey (Not now, Done, back): before a score it is `survey dismissed`;
     * on the follow-up the score alone completes it, so nothing already answered is lost.
     */
    fun onClose() {
        val session = _session.value ?: return
        when (session.step) {
            NpsSurveyStep.Score -> if (displayed.get()) AnalyticsHelper.trackSurveyDismissed(NpsSurveyDefinition.SURVEY_ID)
            NpsSurveyStep.FollowUp -> send(session, answer = null, completed = true)
            NpsSurveyStep.Thanks -> Unit
        }
        _session.value = null
    }

    private fun send(session: NpsSurveySession, answer: String?, completed: Boolean) {
        val score = session.score ?: return
        val band = NpsScoreBand.of(score)
        val followUpId = NpsSurveyCopy.followUpQuestionId(band)
        AnalyticsHelper.trackSurveySent(
            surveyId = NpsSurveyDefinition.SURVEY_ID,
            submissionId = session.submissionId,
            questions = listOf(
                SurveyQuestionRef(NpsSurveyDefinition.SCORE_QUESTION_ID, copy.question),
                SurveyQuestionRef(followUpId, copy.followUpTitle(band)),
            ),
            responses = buildMap {
                put(NpsSurveyDefinition.SCORE_QUESTION_ID, score)
                answer?.let { put(followUpId, it) }
            },
            completed = completed,
        )
    }
}
