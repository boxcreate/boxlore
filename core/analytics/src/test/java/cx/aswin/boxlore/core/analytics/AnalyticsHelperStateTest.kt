package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers [AnalyticsHelper] state helpers and the feedback / lifecycle emitters that route
 * through [AnalyticsEmit] (intercepted, so no PostHog SDK is required).
 */
class AnalyticsHelperStateTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        recorder.clear()
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() {
        restore()
        // Reset transient state back to defaults for other suites.
        AnalyticsHelper.consumeSeekSource()
        AnalyticsHelper.consumePauseReason()
    }

    @Test
    fun seekSourceDefaultsAndResetsAfterConsume() {
        assertEquals("scrubber", AnalyticsHelper.consumeSeekSource())

        AnalyticsHelper.setSeekSource("skip_button")
        assertEquals("skip_button", AnalyticsHelper.consumeSeekSource())
        // consuming resets to default
        assertEquals("scrubber", AnalyticsHelper.consumeSeekSource())
    }

    @Test
    fun pauseReasonDefaultsAndResetsAfterConsume() {
        assertEquals("user_voluntary", AnalyticsHelper.consumePauseReason())

        AnalyticsHelper.setPauseReason("audio_focus_loss")
        assertEquals("audio_focus_loss", AnalyticsHelper.consumePauseReason())
        assertEquals("user_voluntary", AnalyticsHelper.consumePauseReason())
    }

    @Test
    fun deriveGenrePersonaDelegatesToLogic() {
        val persona = AnalyticsHelper.deriveGenrePersona(setOf("News", "Government", "History"))
        assertEquals("civic_junkie", persona["listener_profile"])
    }

    @Test
    fun trackAppCheckStatusEmitsGlossaryEvent() {
        AnalyticsHelper.trackAppCheckStatus(tokenObtained = true, provider = "play_integrity")

        val event = recorder.first { it.first == "app_check_status" }
        assertEquals(true, event.second["token_obtained"])
        assertEquals("play_integrity", event.second["provider"])
    }

    @Test
    fun trackFirstEpisodePlayedEmitsSetOnce() {
        AnalyticsHelper.trackFirstEpisodePlayed()

        val event = recorder.first { it.first == "first_episode_played" }
        assertTrue(event.second.containsKey("\$set_once"))
    }

    @Test
    fun trackEngagementPromptShownIncludesCompletedEpisodesWhenPresent() {
        AnalyticsHelper.trackEngagementPromptShown("milestone_review", "episode_milestone", 7)

        val event = recorder.first { it.first == "engagement_prompt_shown" }
        assertEquals("milestone_review", event.second["prompt_type"])
        assertEquals("episode_milestone", event.second["source"])
        assertEquals(7, event.second["completed_episodes"])
    }

    @Test
    fun trackEngagementPromptShownOmitsCompletedWhenNull() {
        AnalyticsHelper.trackEngagementPromptShown("promoter_review", "nps_handoff", null, npsScore = 9)

        val event = recorder.first { it.first == "engagement_prompt_shown" }
        assertFalse(event.second.containsKey("completed_episodes"))
        assertEquals(9, event.second["nps_score"])
    }

    @Test
    fun reviewPromptsNeverSendTheSurveyTrigger() {
        // Regression: prompts sent feedback_submitted, which also made the person eligible for the NPS survey.
        AnalyticsHelper.trackEngagementPromptShown("milestone_review", "episode_milestone", 5)
        AnalyticsHelper.trackStoreReviewOpened(method = "in_app_review", source = "review_prompt")
        AnalyticsHelper.trackFeedbackSent(category = "bug", withEmail = true, withDiagnostics = false)

        assertFalse(recorder.any { it.first == "feedback_submitted" }, "got ${recorder.map { it.first }}")
        val sent = recorder.first { it.first == "feedback_sent" }.second
        assertEquals(mapOf("category" to "bug", "with_email" to true, "with_diagnostics" to false), sent)
    }

    @Test
    fun captureRoutesThroughGlossaryFilter() {
        AnalyticsHelper.capture("app_open", mapOf("is_first_open" to true))
        AnalyticsHelper.capture("totally_made_up_event")

        assertEquals(1, recorder.count { it.first == "app_open" })
        assertFalse(recorder.any { it.first == "totally_made_up_event" })
    }
}
