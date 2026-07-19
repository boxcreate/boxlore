package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomePlaybackStateLogicTest {
    @Test
    fun `build episode playback state merges history and completed ids`() {
        val history =
            listOf(
                HomeListeningHistoryItem(
                    episodeId = "ep-1",
                    podcastId = "p",
                    podcastName = "Show",
                    podcastImageUrl = null,
                    progressMs = 50L,
                    durationMs = 100L,
                    isCompleted = false,
                    isLiked = false,
                    lastPlayedAt = 1L,
                ),
            )

        val state =
            HomePlaybackStateLogic.buildEpisodePlaybackState(
                allHistory = history,
                completedEpisodeIds = setOf("ep-2"),
            )

        assertEquals(EpisodeStatus.IN_PROGRESS to 0.5f, state["ep-1"])
        assertEquals(EpisodeStatus.COMPLETED to 1f, state["ep-2"])
    }

    @Test
    fun `should show briefing hides completed dismissed or resume displayed`() {
        val briefing =
            Briefing(
                date = "2026-07-18",
                region = "us",
                title = "Daily",
                script = "script",
                audioUrl = "https://example.com/a.mp3",
                coverUrl = "https://example.com/c.jpg",
            )
        val resumeHero =
            listOf(
                SmartHeroItem(
                    type = HeroType.RESUME,
                    podcast = TestFixtures.podcast(id = "briefing_us"),
                    label = "RESUME",
                ),
            )

        assertTrue(
            HomePlaybackStateLogic.shouldShowBriefing(
                rawBriefing = briefing,
                completedEpisodeIds = emptySet(),
                briefingDismissedDate = "",
                briefingDismissedForever = false,
                heroList = emptyList(),
            ),
        )
        assertFalse(
            HomePlaybackStateLogic.shouldShowBriefing(
                rawBriefing = briefing,
                completedEpisodeIds = setOf("briefing_us_2026-07-18"),
                briefingDismissedDate = "",
                briefingDismissedForever = false,
                heroList = emptyList(),
            ),
        )
        assertFalse(
            HomePlaybackStateLogic.shouldShowBriefing(
                rawBriefing = briefing,
                completedEpisodeIds = emptySet(),
                briefingDismissedDate = "2026-07-18",
                briefingDismissedForever = false,
                heroList = emptyList(),
            ),
        )
        assertFalse(
            HomePlaybackStateLogic.shouldShowBriefing(
                rawBriefing = briefing,
                completedEpisodeIds = emptySet(),
                briefingDismissedDate = "",
                briefingDismissedForever = false,
                heroList = resumeHero,
            ),
        )
    }
}
