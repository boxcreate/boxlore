package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.EpisodeSort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoViewModelLogicTest {
    @Test
    fun `resolveInitialSort prefers explicit preferredSort`() {
        assertEquals(EpisodeSort.OLDEST, PodcastInfoSortLogic.resolveInitialSort("oldest", "episodic"))
        assertEquals(EpisodeSort.NEWEST, PodcastInfoSortLogic.resolveInitialSort("newest", "serial"))
    }

    @Test
    fun `resolveInitialSort falls back to podcast type`() {
        assertEquals(EpisodeSort.OLDEST, PodcastInfoSortLogic.resolveInitialSort(null, "serial"))
        assertEquals(EpisodeSort.NEWEST, PodcastInfoSortLogic.resolveInitialSort(null, "episodic"))
        assertEquals(EpisodeSort.NEWEST, PodcastInfoSortLogic.resolveInitialSort("other", "episodic"))
    }

    @Test
    fun `enrichPodcastWithFallback copies local notification flags and latest episode`() {
        val api = TestFixtures.podcast(id = "p1").copy(fallbackImageUrl = null, latestEpisode = null)
        val current = TestFixtures.podcast(id = "p1").copy(subscribedAt = 99L, fallbackImageUrl = "cur.png")
        val local =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                skipBeginningOverrideMs = 1_000L,
                skipEndingOverrideMs = 2_000L,
            )
        val episode = TestFixtures.episode(id = "e1").copy(imageUrl = "ep.png", publishedDate = 50)

        val enriched =
            PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                apiPodcast = api,
                currentPodcast = current,
                localPodcast = local,
                pageEpisodes = listOf(episode),
                sortParam = "newest",
            )

        assertEquals("cur.png", enriched.fallbackImageUrl)
        assertEquals(99L, enriched.subscribedAt)
        assertEquals(true, enriched.notificationsEnabled)
        assertEquals(true, enriched.autoDownloadEnabled)
        assertEquals(1_000L, enriched.skipBeginningOverrideMs)
        assertEquals(2_000L, enriched.skipEndingOverrideMs)
        assertEquals("e1", enriched.latestEpisode?.id)
    }

    @Test
    fun `enrichPodcastWithFallback oldest sort picks max publishedDate`() {
        val api = TestFixtures.podcast(id = "p1").copy(latestEpisode = null)
        val older = TestFixtures.episode(id = "old").copy(publishedDate = 10)
        val newer = TestFixtures.episode(id = "new").copy(publishedDate = 99)

        val enriched =
            PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                apiPodcast = api,
                currentPodcast = null,
                localPodcast = null,
                pageEpisodes = listOf(older, newer),
                sortParam = "oldest",
            )

        assertEquals("new", enriched.latestEpisode?.id)
        assertNull(enriched.skipBeginningOverrideMs)
    }

    @Test
    fun `pull refresh targets RSS catalog, opted-in feed, or PI catalog`() {
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.RSS_CATALOG,
            PodcastInfoPullRefreshLogic.target(
                isRss = true,
                chip = DirectFeedChipState.Hidden,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.DIRECT_FEED,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                chip = DirectFeedChipState.Updated,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.PI_CATALOG,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                chip = DirectFeedChipState.Offer,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.NONE,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                chip = DirectFeedChipState.Fetching,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.PI_CATALOG,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                chip = DirectFeedChipState.Hidden,
            ),
        )
        assertTrue(PodcastInfoPullRefreshLogic.shouldApply("p1", "p1"))
        assertFalse(PodcastInfoPullRefreshLogic.shouldApply("p1", "p2"))
        assertTrue(PodcastInfoPullRefreshLogic.shouldPersistLibraryTip(isSubscribed = true, hasTip = true))
        assertFalse(PodcastInfoPullRefreshLogic.shouldPersistLibraryTip(isSubscribed = true, hasTip = false))
        assertFalse(PodcastInfoPullRefreshLogic.shouldPersistLibraryTip(isSubscribed = false, hasTip = true))
    }
}
