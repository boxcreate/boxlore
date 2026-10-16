package cx.aswin.boxlore.core.analytics

import cx.aswin.boxlore.core.prefs.DiscoverySection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecommendationsAnalyticsTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        restore = AnalyticsEmit.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() = restore()

    @Test
    fun `every section reports the name listeners see`() {
        assertEquals(
            listOf("brief", "picked_for_you", "because_you_like", "time_of_day_picks", "more_like_this"),
            DiscoverySection.entries.map(RecommendationsAnalyticsTracks::sectionName),
        )
    }

    @Test
    fun `a switch reports its section, new value, source and how many sections are off`() {
        AnalyticsHelper.trackRecommendationSectionToggled(DiscoverySection.PICKED_FOR_YOU, enabled = false, source = "settings", sectionsOff = 2)
        AnalyticsHelper.trackRecommendationSectionToggled(DiscoverySection.DAILY_BRIEF, enabled = true, source = "settings", sectionsOff = 1)

        assertEquals(
            listOf(
                "recommendation_section_toggled" to
                    mapOf<String, Any>("section" to "picked_for_you", "enabled" to false, "source" to "settings", "sections_off" to 2),
                "recommendation_section_toggled" to
                    mapOf<String, Any>("section" to "brief", "enabled" to true, "source" to "settings", "sections_off" to 1),
            ),
            recorder.filter { it.first == "recommendation_section_toggled" },
        )
    }
}
