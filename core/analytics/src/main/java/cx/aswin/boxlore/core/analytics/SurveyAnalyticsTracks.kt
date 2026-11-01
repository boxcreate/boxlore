package cx.aswin.boxlore.core.analytics

import com.posthog.PostHog

/** A survey question as PostHog knows it: its ID there and the text the listener saw. */
data class SurveyQuestionRef(val id: String, val question: String,)

/**
 * PostHog's standard survey events for a survey boxlore draws itself (an `api` survey), so its results
 * page and NPS charts work as for PostHog's own. One submission keeps one [submissionId] across its
 * partial and final sends; each send carries every answer so far, keyed by question ID.
 */
internal object SurveyAnalyticsTracks {
    fun trackSurveyShown(surveyId: String) {
        AnalyticsEmit.event(SURVEY_SHOWN, mapOf(SURVEY_ID to surveyId))
    }

    fun trackSurveySent(
        surveyId: String,
        submissionId: String,
        questions: List<SurveyQuestionRef>,
        responses: Map<String, Any>,
        completed: Boolean,
    ) {
        AnalyticsEmit.event(
            SURVEY_SENT,
            buildMap {
                put(SURVEY_ID, surveyId)
                put("\$survey_submission_id", submissionId)
                put("\$survey_completed", completed)
                put("\$survey_questions", questions.map { mapOf("id" to it.id, "question" to it.question) })
                responses.forEach { (questionId, answer) -> put("\$survey_response_$questionId", answer) }
            },
        )
    }

    /** Closed before any answer; a survey with an answer sent is never also dismissed. */
    fun trackSurveyDismissed(surveyId: String) {
        AnalyticsEmit.event(SURVEY_DISMISSED, mapOf(SURVEY_ID to surveyId))
    }

    /** String overrides from [flagKey]'s payload (a JSON object), so wording can change without a release. */
    fun surveyCopyOverrides(flagKey: String): Map<String, String> =
        try {
            parseCopyOverrides(if (PostHogStartup.isReady) PostHog.getFeatureFlagPayload(flagKey, null) else null)
        } catch (e: Exception) {
            emptyMap()
        }

    /** The string values of an object [payload]; anything else gives none. */
    internal fun parseCopyOverrides(payload: Any?): Map<String, String> {
        val root = payload as? Map<*, *> ?: return emptyMap()
        return buildMap { root.forEach { (key, value) -> if (key is String && value is String) put(key, value) } }
    }

    const val SURVEY_SHOWN = "survey shown"
    const val SURVEY_SENT = "survey sent"
    const val SURVEY_DISMISSED = "survey dismissed"
    private const val SURVEY_ID = "\$survey_id"
}
