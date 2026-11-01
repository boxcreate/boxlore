package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Additional coverage for [RecordingAnalytics] feedback / lifecycle helpers not exercised by
 * [RecordingAnalyticsTest].
 */
class RecordingAnalyticsExtraTest {
    private lateinit var analytics: RecordingAnalytics

    @BeforeEach
    fun setUp() {
        analytics = RecordingAnalytics()
    }

    @Test
    fun trackEngagementPromptShownCarriesNpsScoreWhenPresent() {
        analytics.trackEngagementPromptShown("milestone_review", "episode_milestone", npsScore = 10)

        val event = analytics.lastEvent!!
        assertEquals("engagement_prompt_shown", event.name)
        assertEquals(10, event.properties["nps_score"])
    }

    @Test
    fun trackEngagementPromptShownOmitsNpsScoreWhenNull() {
        analytics.trackEngagementPromptShown("milestone_review", "episode_milestone", completedEpisodes = 5)

        assertFalse("nps_score" in analytics.lastEvent!!.properties)
    }

    @Test
    fun trackFirstEpisodePlayedEmitsEvent() {
        analytics.trackFirstEpisodePlayed()

        assertEquals(1, analytics.eventCount("first_episode_played"))
    }

    @Test
    fun trackAppCheckStatusCapturesTokenAndProvider() {
        analytics.trackAppCheckStatus(tokenObtained = true, provider = "play_integrity")

        val event = analytics.lastEvent!!
        assertEquals("app_check_status", event.name)
        assertEquals(true, event.properties["token_obtained"])
        assertEquals("play_integrity", event.properties["provider"])
    }

    @Test
    fun nonGlossaryEventsRemainDropped() {
        analytics.capture("totally_made_up_event")
        assertTrue(analytics.events.isEmpty())
        assertNull(analytics.lastEvent)
    }
}
