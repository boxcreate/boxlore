package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One NPS survey's steps and PostHog events: shown once, the score kept at once, one submission, never shown-and-lost. */
class NpsSurveyControllerTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private var scheduledDisplays = 0
    private val scores = mutableListOf<Int>()
    private val controller =
        NpsSurveyController(
            onScheduledDisplay = { scheduledDisplays++ },
            onScored = { scores += it },
            newSubmissionId = { "sub-1" },
        )

    @Before
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @After
    fun tearDown() = restore()

    private fun sent() = events.filter { it.first == "survey sent" }.map { it.second }

    private fun openAndShow(manual: Boolean = false) {
        controller.open(manual = manual, copy = NpsSurveyCopy())
        controller.onDisplayed()
    }

    @Test
    fun `a scheduled survey counts as shown once, when it is on screen`() {
        controller.open(manual = false, copy = NpsSurveyCopy())
        assertTrue(events.isEmpty())
        assertEquals(0, scheduledDisplays)

        controller.onDisplayed()
        controller.onDisplayed() // Home resumed again with the sheet still up

        assertEquals(listOf("survey shown"), events.map { it.first })
        assertEquals(NpsSurveyDefinition.SURVEY_ID, events.single().second["\$survey_id"])
        assertEquals(1, scheduledDisplays)
    }

    @Test
    fun `a survey opened by hand sends its events but leaves the schedule alone`() {
        openAndShow(manual = true)

        assertEquals(listOf("survey shown"), events.map { it.first })
        assertEquals(0, scheduledDisplays)
    }

    @Test
    fun `the score is sent at once as a partial answer, then the follow-up completes the same submission`() {
        openAndShow()
        controller.onScore(4)

        assertEquals(NpsSurveyStep.FollowUp, controller.session.value?.step)
        assertEquals(listOf(4), scores)
        val partial = sent().single()
        assertEquals(false, partial["\$survey_completed"])
        assertEquals("sub-1", partial["\$survey_submission_id"])
        assertEquals(4, partial["\$survey_response_${NpsSurveyDefinition.SCORE_QUESTION_ID}"])
        assertFalse(partial.containsKey("\$survey_response_${NpsSurveyDefinition.FIX_QUESTION_ID}"))

        controller.onFollowUpDone("  Search should find episodes by guest  ")

        val final = sent().last()
        assertEquals(true, final["\$survey_completed"])
        assertEquals("sub-1", final["\$survey_submission_id"])
        assertEquals(4, final["\$survey_response_${NpsSurveyDefinition.SCORE_QUESTION_ID}"])
        assertEquals("Search should find episodes by guest", final["\$survey_response_${NpsSurveyDefinition.FIX_QUESTION_ID}"])
        assertEquals(NpsSurveyStep.Thanks, controller.session.value?.step)
        assertFalse(events.any { it.first == "survey dismissed" })
    }

    @Test
    fun `each score band asks its own follow-up`() {
        mapOf(
            0 to NpsSurveyDefinition.FIX_QUESTION_ID,
            6 to NpsSurveyDefinition.FIX_QUESTION_ID,
            7 to NpsSurveyDefinition.TEN_QUESTION_ID,
            8 to NpsSurveyDefinition.TEN_QUESTION_ID,
            9 to NpsSurveyDefinition.IDEA_QUESTION_ID,
            10 to NpsSurveyDefinition.IDEA_QUESTION_ID,
        ).forEach { (score, questionId) ->
            events.clear()
            openAndShow()
            controller.onScore(score)
            controller.onFollowUpDone("idea")
            val questions = sent().last()["\$survey_questions"] as List<*>
            assertEquals("score $score", questionId, (questions.last() as Map<*, *>)["id"])
            assertEquals("score $score", "idea", sent().last()["\$survey_response_$questionId"])
            controller.onClose()
        }
    }

    @Test
    fun `Skip completes with the score alone, and a long answer is cut to 500 characters`() {
        openAndShow()
        controller.onScore(9)
        controller.onFollowUpDone("   ")
        assertFalse(sent().last().containsKey("\$survey_response_${NpsSurveyDefinition.IDEA_QUESTION_ID}"))
        assertEquals(true, sent().last()["\$survey_completed"])
        controller.onClose()

        openAndShow()
        controller.onScore(9)
        controller.onFollowUpDone("x".repeat(600))
        assertEquals(500, (sent().last()["\$survey_response_${NpsSurveyDefinition.IDEA_QUESTION_ID}"] as String).length)
    }

    @Test
    fun `Not now before a score is a dismissal, only once the survey was seen`() {
        controller.open(manual = false, copy = NpsSurveyCopy())
        controller.onClose() // decided but never on screen
        assertTrue(events.isEmpty())

        openAndShow()
        controller.onClose()

        assertEquals(listOf("survey shown", "survey dismissed"), events.map { it.first })
        assertNull(controller.session.value)
    }

    @Test
    fun `closing on the follow-up keeps the score as a completed answer, not a dismissal`() {
        openAndShow()
        controller.onScore(7)
        controller.onClose()

        assertEquals(listOf(false, true), sent().map { it["\$survey_completed"] })
        assertFalse(events.any { it.first == "survey dismissed" })
        assertNull(controller.session.value)
    }

    @Test
    fun `closing the thanks sends nothing more`() {
        openAndShow()
        controller.onScore(10)
        controller.onFollowUpDone("")
        val before = events.size
        controller.onClose()
        assertEquals(before, events.size)
    }

    @Test
    fun `a second open while one is up is ignored`() {
        controller.open(manual = false, copy = NpsSurveyCopy())
        val first = controller.session.value
        controller.open(manual = true, copy = NpsSurveyCopy())
        assertEquals(first, controller.session.value)
    }

    @Test
    fun `wording overrides replace only their keys and an empty tag hides it`() {
        val copy = NpsSurveyCopy(mapOf("ideaTag" to "", "picked" to "Your score: {score}"))
        assertEquals("", copy.ideaTag)
        assertEquals("Your score: 8", copy.picked(8))
        assertEquals("How likely are you to recommend boxlore to a friend?", copy.question)
        assertEquals("You picked 4", NpsSurveyCopy().picked(4))
    }
}
