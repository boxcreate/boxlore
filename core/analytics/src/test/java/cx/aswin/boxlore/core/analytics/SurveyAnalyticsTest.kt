package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** boxlore's own survey sheet sends PostHog's standard survey events, shaped as its results page reads them. */
class SurveyAnalyticsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() = restore()

    @Test
    fun `a send carries the submission, its questions and every answer so far`() {
        AnalyticsHelper.trackSurveySent(
            surveyId = "s1",
            submissionId = "sub1",
            questions = listOf(SurveyQuestionRef("q1", "How likely?"), SurveyQuestionRef("q2", "Why?")),
            responses = mapOf("q1" to 7, "q2" to "Faster search"),
            completed = true,
        )

        val (name, props) = recorder.single()
        assertEquals("survey sent", name)
        assertEquals("s1", props["\$survey_id"])
        assertEquals("sub1", props["\$survey_submission_id"])
        assertEquals(true, props["\$survey_completed"])
        assertEquals(7, props["\$survey_response_q1"])
        assertEquals("Faster search", props["\$survey_response_q2"])
        assertEquals(
            listOf(mapOf("id" to "q1", "question" to "How likely?"), mapOf("id" to "q2", "question" to "Why?")),
            props["\$survey_questions"],
        )
    }

    @Test
    fun `shown and dismissed name the survey only`() {
        AnalyticsHelper.trackSurveyShown("s1")
        AnalyticsHelper.trackSurveyDismissed("s1")

        assertEquals(listOf("survey shown" to mapOf("\$survey_id" to "s1"), "survey dismissed" to mapOf("\$survey_id" to "s1")), recorder)
    }

    @Test
    fun `wording overrides keep only string values of an object payload`() {
        assertEquals(
            mapOf("ideaTag" to "", "question" to "Would you recommend boxlore?"),
            SurveyAnalyticsTracks.parseCopyOverrides(mapOf("ideaTag" to "", "question" to "Would you recommend boxlore?", "n" to 3, 4 to "x")),
        )
        assertTrue(SurveyAnalyticsTracks.parseCopyOverrides("{\"ideaTag\":\"\"}").isEmpty())
        assertTrue(SurveyAnalyticsTracks.parseCopyOverrides(null).isEmpty())
    }
}
