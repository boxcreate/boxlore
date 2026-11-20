package cx.aswin.boxlore.feature.home

import androidx.annotation.StringRes
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
    /** A decided survey not on screen by then is dropped: its finish moment has passed. */
    val expiresAtMs: Long? = null,
) {
    val band: NpsScoreBand? get() = score?.let(NpsScoreBand::of)

    fun isExpired(nowMs: Long): Boolean = !manual && expiresAtMs != null && nowMs > expiresAtMs
}

/**
 * The survey's wording: [strings] reads the app's text (by resource id) and any key in [overrides], from
 * [NpsSurveyDefinition.COPY_FLAG]'s payload, replaces it; an empty `ideaTag` hides the tag on the idea step.
 */
internal class NpsSurveyCopy(
    private val overrides: Map<String, String> = emptyMap(),
    private val strings: (Int) -> String,
) {
    private fun text(key: String, @StringRes id: Int) = overrides[key] ?: strings(id)

    /** The same overrides, worded by [strings] (the sheet's own resources, so it follows the app language). */
    fun withStrings(strings: (Int) -> String) = NpsSurveyCopy(overrides, strings)

    val eyebrow = text("eyebrow", R.string.home_nps_eyebrow)
    val question = text("question", R.string.home_nps_question)
    val notLikely = text("notLikely", R.string.home_nps_not_likely)
    val veryLikely = text("veryLikely", R.string.home_nps_very_likely)
    val notNow = text("notNow", R.string.home_nps_not_now)
    private val picked = text("picked", R.string.home_nps_picked)
    val fixTitle = text("fixTitle", R.string.home_nps_fix_title)
    val fixHelper = text("fixHelper", R.string.home_nps_fix_helper)
    val tenTitle = text("tenTitle", R.string.home_nps_ten_title)
    val tenHelper = text("tenHelper", R.string.home_nps_ten_helper)
    val ideaTitle = text("ideaTitle", R.string.home_nps_idea_title)
    val ideaTag = text("ideaTag", R.string.home_nps_idea_tag)
    val answerLabel = text("answerLabel", R.string.home_nps_answer_label)
    val answerHint = text("answerHint", R.string.home_nps_answer_hint)
    val ideaLabel = text("ideaLabel", R.string.home_nps_idea_label)
    val ideaHint = text("ideaHint", R.string.home_nps_idea_hint)
    val skip = text("skip", R.string.home_nps_skip)
    val send = text("send", R.string.home_nps_send)
    val sendIdea = text("sendIdea", R.string.home_nps_send_idea)
    val thanksLowTitle = text("thanksLowTitle", R.string.home_nps_thanks_low_title)
    val thanksLowBody = text("thanksLowBody", R.string.home_nps_thanks_low_body)
    val thanksMiddleTitle = text("thanksMiddleTitle", R.string.home_nps_thanks_middle_title)
    val thanksMiddleBody = text("thanksMiddleBody", R.string.home_nps_thanks_middle_body)
    val thanksHighTitle = text("thanksHighTitle", R.string.home_nps_thanks_high_title)
    val thanksHighBody = text("thanksHighBody", R.string.home_nps_thanks_high_body)
    val done = text("done", R.string.home_nps_done)
    val rateOnPlay = text("rateOnPlay", R.string.home_nps_rate_on_play)

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
    var copy: NpsSurveyCopy? = null
        private set

    val isOpen: Boolean get() = _session.value != null

    /** This survey has been on screen. */
    val wasDisplayed: Boolean get() = displayed.get()

    fun open(manual: Boolean, copy: NpsSurveyCopy, expiresAtMs: Long? = null) {
        if (isOpen) return
        this.copy = copy
        displayed.set(false)
        _session.value = NpsSurveySession(submissionId = newSubmissionId(), manual = manual, expiresAtMs = expiresAtMs)
    }

    /** Drops a decided survey that never reached the screen; nothing is sent and nothing is used up. */
    fun discardUndisplayed() {
        if (!displayed.get()) _session.value = null
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
        val wording = copy ?: return
        val band = NpsScoreBand.of(score)
        val followUpId = NpsSurveyCopy.followUpQuestionId(band)
        AnalyticsHelper.trackSurveySent(
            surveyId = NpsSurveyDefinition.SURVEY_ID,
            submissionId = session.submissionId,
            // Every question in the survey's order, as PostHog's own SDKs send them: its queries read answers by position.
            questions = listOf(
                SurveyQuestionRef(NpsSurveyDefinition.SCORE_QUESTION_ID, wording.question),
                SurveyQuestionRef(NpsSurveyDefinition.FIX_QUESTION_ID, wording.fixTitle),
                SurveyQuestionRef(NpsSurveyDefinition.TEN_QUESTION_ID, wording.tenTitle),
                SurveyQuestionRef(NpsSurveyDefinition.IDEA_QUESTION_ID, wording.ideaTitle),
            ),
            responses = buildMap {
                put(NpsSurveyDefinition.SCORE_QUESTION_ID, score)
                answer?.let { put(followUpId, it) }
            },
            completed = completed,
        )
    }
}
