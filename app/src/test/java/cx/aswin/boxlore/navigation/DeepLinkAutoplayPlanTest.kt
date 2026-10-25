package cx.aswin.boxlore.navigation

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Old autoplay share links (`autoplay=true`, `t=`) keep starting and seeking their episode when the
 * page shows it under the phone's own ID, and a page loaded for another link never plays.
 */
class DeepLinkAutoplayPlanTest {
    private val page = EpisodeInfoUiState.Success(
        episode = Episode("-9", "Episode", "", "https://cdn.example/9.mp3", podcastId = "920666"),
        podcastId = "920666",
        podcastTitle = "Show",
        requestedEpisodeId = "5001",
    )

    @Test
    fun `a link ID that resolves to the phone's own ID still autoplays and seeks`() {
        val plan = deepLinkAutoplayPlan("5001", autoplay = "true", t = 90, start = null, success = page, currentEpisodeId = null)

        assertEquals(DeepLinkAutoplayPlan(startEpisode = true, seekToMs = 90_000L, playAfterSeek = true), plan)
    }

    @Test
    fun `the player already on the page's episode only seeks`() {
        val plan = deepLinkAutoplayPlan("5001", autoplay = "true", t = null, start = 10, success = page, currentEpisodeId = "-9")

        assertEquals(DeepLinkAutoplayPlan(startEpisode = false, seekToMs = 10_000L, playAfterSeek = true), plan)
    }

    @Test
    fun `a link without autoplay seeks without playing`() {
        val plan = deepLinkAutoplayPlan("5001", autoplay = "false", t = 30, start = 10, success = page, currentEpisodeId = null)

        assertEquals(DeepLinkAutoplayPlan(startEpisode = false, seekToMs = 30_000L, playAfterSeek = false), plan)
    }

    @Test
    fun `a page loaded for another link never plays`() {
        assertNull(deepLinkAutoplayPlan("6001", autoplay = "true", t = 90, start = null, success = page, currentEpisodeId = null))
    }
}
