package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A show's own playback speed and trim silence survive every show page rebuild, like its skip overrides. */
class PodcastInfoShowPlaybackLogicTest {
    @Test
    fun `enriching the page keeps the stored show's speed and trim silence`() {
        val local = TestFixtures.podcast(id = "p1").copy(subscribedAt = 5L, playbackSpeedOverride = 1.5f, skipSilenceOverride = false)

        val enriched = PodcastInfoEnrichLogic.enrichPodcastWithFallback(
            apiPodcast = TestFixtures.podcast(id = "p1"),
            currentPodcast = null,
            localPodcast = local,
            pageEpisodes = emptyList(),
            sortParam = "newest",
        )

        assertEquals(1.5f, enriched.playbackSpeedOverride)
        assertEquals(false, enriched.skipSilenceOverride)
    }

    @Test
    fun `a show without its own settings stays on the app settings`() {
        val enriched = PodcastInfoEnrichLogic.enrichPodcastWithFallback(
            apiPodcast = TestFixtures.podcast(id = "p1"),
            currentPodcast = null,
            localPodcast = null,
            pageEpisodes = emptyList(),
            sortParam = "newest",
        )

        assertNull(enriched.playbackSpeedOverride)
        assertNull(enriched.skipSilenceOverride)
    }

    @Test
    fun `a refresh keeps the page's settings, else the stored show's`() {
        val refreshed = TestFixtures.podcast(id = "p1")
        val fromPage = PodcastInfoEnrichLogic.preserveSubscriptionProperties(
            refreshedPodcast = refreshed,
            latestPodcast = TestFixtures.podcast(id = "p1").copy(playbackSpeedOverride = 2f, skipSilenceOverride = true),
            localPodcast = TestFixtures.podcast(id = "p1").copy(playbackSpeedOverride = 0.8f, skipSilenceOverride = false),
            isSubscribed = true,
        )
        val fromStore = PodcastInfoEnrichLogic.preserveSubscriptionProperties(
            refreshedPodcast = refreshed,
            latestPodcast = TestFixtures.podcast(id = "p1"),
            localPodcast = TestFixtures.podcast(id = "p1").copy(playbackSpeedOverride = 0.8f, skipSilenceOverride = false),
            isSubscribed = true,
        )

        assertEquals(2f to true, fromPage.playbackSpeedOverride to fromPage.skipSilenceOverride)
        assertEquals(0.8f to false, fromStore.playbackSpeedOverride to fromStore.skipSilenceOverride)
    }

    @Test
    fun `a late refresh keeps the settings changed while it loaded`() {
        val current = PodcastInfoUiState.Success(
            podcast = TestFixtures.podcast(id = "p1").copy(playbackSpeedOverride = 1.25f, skipSilenceOverride = true),
            episodes = emptyList(),
            isSubscribed = true,
            directFeedChip = DirectFeedChipState.Hidden,
        )
        val stale = current.copy(podcast = TestFixtures.podcast(id = "p1"))

        val applied = PodcastInfoAsyncResultLogic.preserveCurrentSubscription(current, stale, targetPodcastId = "p1")!!

        assertEquals(1.25f, applied.podcast.playbackSpeedOverride)
        assertEquals(true, applied.podcast.skipSilenceOverride)
    }
}
