package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixTimeTest {
    @Test
    fun `remaining minutes include complete hours`() {
        assertEquals(90, remainingMixMinutes(10800, 0.5f))
        assertEquals(60, remainingMixMinutes(7200, 0.5f))
        assertEquals(180, remainingMixMinutes(14400, 0.25f))
    }

    @Test
    fun `sub minute remainders display one minute rather than zero`() {
        assertEquals(1, remainingMixMinutes(120, 0.75f))
        assertEquals(1, remainingMixMinutes(60, 0.99f))
        assertEquals(1, remainingMixMinutes(120, 0.5f))
    }

    @Test
    fun `ordinary remainders retain whole minute formatting`() {
        assertEquals(30, remainingMixMinutes(3600, 0.5f))
        assertEquals(9, remainingMixMinutes(1200, 0.51f))
    }

    @Test
    fun `daily mix summary counts unplayed and in progress episodes and the time left`() {
        fun show(id: String, duration: Int, status: EpisodeStatus = EpisodeStatus.UNPLAYED, progress: Float? = null) =
            TestFixtures.podcast(id = id).copy(
                latestEpisode = TestFixtures.episode(id = "ep-$id", duration = duration),
                episodeStatus = status,
                resumeProgress = progress,
            )
        val summary = dailyMixSummary(
            listOf(
                show("a", 3600),
                show("b", 1800, EpisodeStatus.IN_PROGRESS, 0.5f),
                show("c", 2400, EpisodeStatus.COMPLETED, 1f),
                show("d", 0),
                TestFixtures.podcast(id = "no-episode"),
            ),
        )
        assertEquals(DailyMixSummary(episodes = 3, minutes = 75), summary)
        assertEquals(DailyMixSummary(0, 0), dailyMixSummary(emptyList()))
    }
}
