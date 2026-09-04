package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoViewModelLogicTest {
    @Test
    fun `playback source retains only Video Spotlight attribution`() {
        assertEquals(
            "home_video_spotlight",
            PodcastInfoPlaybackSourceLogic.retainedEntryPoint("home_video_spotlight"),
        )
        assertNull(PodcastInfoPlaybackSourceLogic.retainedEntryPoint("home_discover_grid"))
        assertNull(PodcastInfoPlaybackSourceLogic.retainedEntryPoint(null))
    }

    @Test
    fun `resolvedPlaybackEntryPoints retains video spotlight and falls back to podcast_detail`() {
        val (videoEntryPoint, videoSource) =
            PodcastInfoPlaybackSourceLogic.resolvedPlaybackEntryPoints("home_video_spotlight")
        assertEquals("home_video_spotlight", videoEntryPoint)
        assertEquals("podcast_detail", videoSource)

        val (gridEntryPoint, gridSource) =
            PodcastInfoPlaybackSourceLogic.resolvedPlaybackEntryPoints("home_discover_grid")
        assertEquals("podcast_detail", gridEntryPoint)
        assertEquals("podcast_detail", gridSource)

        val (nullEntryPoint, nullSource) =
            PodcastInfoPlaybackSourceLogic.resolvedPlaybackEntryPoints(null)
        assertEquals("podcast_detail", nullEntryPoint)
        assertEquals("podcast_detail", nullSource)

        assertEquals("podcast_detail", PodcastInfoPlaybackSourceLogic.BULK_PLAY_ENTRY_POINT)
        assertEquals("podcast_detail", PodcastInfoPlaybackSourceLogic.BULK_PLAY_SOURCE_ENTRY_POINT)
    }

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

    @Test
    fun `pull refresh on subscribed non-RSS show targets subscribed direct feed`() {
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.SUBSCRIBED_DIRECT_FEED,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                isSubscribed = true,
                chip = DirectFeedChipState.Hidden,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.SUBSCRIBED_DIRECT_FEED,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                isSubscribed = true,
                chip = DirectFeedChipState.Updated,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.NONE,
            PodcastInfoPullRefreshLogic.target(
                isRss = false,
                isSubscribed = true,
                chip = DirectFeedChipState.Fetching,
            ),
        )
        assertEquals(
            PodcastInfoPullRefreshLogic.Target.RSS_CATALOG,
            PodcastInfoPullRefreshLogic.target(
                isRss = true,
                isSubscribed = true,
                chip = DirectFeedChipState.Hidden,
            ),
        )
    }

    @Test
    fun `preserveSubscriptionProperties preserves all local subscription toggles and settings`() {
        val rawApiPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                subscribedAt = 0L,
                skipBeginningOverrideMs = null,
                skipEndingOverrideMs = null,
                fallbackImageUrl = null,
                preferredSort = null,
            )
        val latestPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                subscribedAt = 123456L,
                skipBeginningOverrideMs = 15_000L,
                skipEndingOverrideMs = 30_000L,
                fallbackImageUrl = "https://example.com/fallback.jpg",
                preferredSort = "oldest",
            )

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = null,
                isSubscribed = true,
            )

        assertTrue(preserved.notificationsEnabled)
        assertTrue(preserved.autoDownloadEnabled)
        assertEquals(123456L, preserved.subscribedAt)
        assertEquals(15_000L, preserved.skipBeginningOverrideMs)
        assertEquals(30_000L, preserved.skipEndingOverrideMs)
        assertEquals("https://example.com/fallback.jpg", preserved.fallbackImageUrl)
        assertEquals("oldest", preserved.preferredSort)
    }

    @Test
    fun `preserveSubscriptionProperties enriches from localPodcast fallback when available`() {
        val rawApiPodcast = TestFixtures.podcast(id = "p1")
        val latestPodcast = TestFixtures.podcast(id = "p1")
        val localPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                subscribedAt = 987654L,
                skipBeginningOverrideMs = 5_000L,
                skipEndingOverrideMs = 10_000L,
                fallbackImageUrl = "https://example.com/local_art.jpg",
                preferredSort = "newest",
            )

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = localPodcast,
                isSubscribed = true,
            )

        assertTrue(preserved.notificationsEnabled)
        assertTrue(preserved.autoDownloadEnabled)
        assertEquals(987654L, preserved.subscribedAt)
        assertEquals(5_000L, preserved.skipBeginningOverrideMs)
        assertEquals(10_000L, preserved.skipEndingOverrideMs)
        assertEquals("https://example.com/local_art.jpg", preserved.fallbackImageUrl)
        assertEquals("newest", preserved.preferredSort)
    }

    @Test
    fun `preserveSubscriptionProperties keeps notifications off when explicitly disabled`() {
        val rawApiPodcast = TestFixtures.podcast(id = "p1")
        val latestPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                subscribedAt = 12345L,
            )
        val localPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                subscribedAt = 12345L,
            )

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = localPodcast,
                isSubscribed = true,
            )

        assertFalse(preserved.notificationsEnabled)
        assertFalse(preserved.autoDownloadEnabled)
        assertEquals(12345L, preserved.subscribedAt)
    }

    @Test
    fun `late refresh preserves an unsubscribe completed while it was loading`() {
        val current =
            PodcastInfoUiState.Success(
                podcast =
                TestFixtures
                    .podcast(id = "p1")
                    .copy(
                        subscribedAt = 0L,
                        notificationsEnabled = false,
                        autoDownloadEnabled = false,
                        skipBeginningOverrideMs = 5_000L,
                        skipEndingOverrideMs = 10_000L,
                        fallbackImageUrl = "cur.png",
                        preferredSort = "oldest",
                    ),
                episodes = emptyList(),
                isSubscribed = false,
                directFeedChip = DirectFeedChipState.Offer,
            )
        val staleResult =
            PodcastInfoUiState.Success(
                podcast =
                TestFixtures
                    .podcast(id = "p1")
                    .copy(
                        subscribedAt = 99L,
                        notificationsEnabled = true,
                        autoDownloadEnabled = true,
                        skipBeginningOverrideMs = null,
                        skipEndingOverrideMs = null,
                        fallbackImageUrl = null,
                        preferredSort = null,
                    ),
                episodes = listOf(TestFixtures.episode(id = "new")),
                isSubscribed = true,
                directFeedChip = DirectFeedChipState.Hidden,
            )

        val applied =
            PodcastInfoAsyncResultLogic.preserveCurrentSubscription(
                current = current,
                result = staleResult,
                targetPodcastId = "p1",
            )!!

        assertFalse(applied.isSubscribed)
        assertEquals(0L, applied.podcast.subscribedAt)
        assertFalse(applied.podcast.notificationsEnabled)
        assertFalse(applied.podcast.autoDownloadEnabled)
        assertEquals(5_000L, applied.podcast.skipBeginningOverrideMs)
        assertEquals(10_000L, applied.podcast.skipEndingOverrideMs)
        assertEquals("cur.png", applied.podcast.fallbackImageUrl)
        assertEquals("oldest", applied.podcast.preferredSort)
        assertEquals(DirectFeedChipState.Offer, applied.directFeedChip)
        assertEquals(listOf("new"), applied.episodes.map { it.id })
    }

    @Test
    fun `preserveSubscriptionProperties respects explicit unsubscribed state and clears flags`() {
        val rawApiPodcast = TestFixtures.podcast(id = "p1")
        val latestPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                subscribedAt = 12345L,
            )
        val localPodcast =
            TestFixtures.podcast(id = "p1").copy(
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                subscribedAt = 12345L,
            )

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = localPodcast,
                isSubscribed = false,
            )

        assertFalse(preserved.notificationsEnabled)
        assertFalse(preserved.autoDownloadEnabled)
        assertEquals(0L, preserved.subscribedAt)
    }

    @Test
    fun `preserveSubscriptionProperties prioritizes newest episode from local direct feed over older api episode`() {
        val olderApiEpisode = TestFixtures.episode(id = "ep-old").copy(publishedDate = 1000L)
        val newerFeedEpisode = TestFixtures.episode(id = "ep-new").copy(publishedDate = 2000L)

        val rawApiPodcast = TestFixtures.podcast(id = "p1").copy(latestEpisode = olderApiEpisode)
        val latestPodcast = TestFixtures.podcast(id = "p1").copy(latestEpisode = olderApiEpisode)
        val localPodcast = TestFixtures.podcast(id = "p1").copy(latestEpisode = newerFeedEpisode)

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = localPodcast,
                isSubscribed = true,
            )

        assertEquals("ep-new", preserved.latestEpisode?.id)
        assertEquals(2000L, preserved.latestEpisode?.publishedDate)
    }

    @Test
    fun `preserveSubscriptionProperties filters out blank feedUrl and preferredSort fallbacks`() {
        val rawApiPodcast =
            TestFixtures.podcast(id = "p1").copy(
                feedUrl = "",
                preferredSort = "",
            )
        val latestPodcast =
            TestFixtures.podcast(id = "p1").copy(
                feedUrl = "https://example.com/latest_feed.xml",
                preferredSort = "newest",
            )
        val localPodcast =
            TestFixtures.podcast(id = "p1").copy(
                feedUrl = "https://example.com/local_feed.xml",
                preferredSort = "oldest",
            )

        val preserved =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = rawApiPodcast,
                latestPodcast = latestPodcast,
                localPodcast = localPodcast,
                isSubscribed = true,
            )

        assertEquals("https://example.com/latest_feed.xml", preserved.feedUrl)
        assertEquals("newest", preserved.preferredSort)
    }

    @Test
    fun `enrichPodcastWithFallback keeps toggles disabled when show is not subscribed`() {
        val apiPodcast = TestFixtures.podcast(id = "p1")
        val currentPodcast = TestFixtures.podcast(id = "p1").copy(subscribedAt = 0L, notificationsEnabled = false)
        val localPodcast = TestFixtures.podcast(id = "p1").copy(subscribedAt = 0L, notificationsEnabled = true)

        val enriched =
            PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                apiPodcast = apiPodcast,
                currentPodcast = currentPodcast,
                localPodcast = localPodcast,
                pageEpisodes = emptyList(),
                sortParam = "newest",
            )

        assertFalse(enriched.notificationsEnabled)
        assertFalse(enriched.autoDownloadEnabled)
        assertEquals(0L, enriched.subscribedAt)
    }

    @Test
    fun `enrichPodcastWithFallback picks newer episode from local podcast over older api episode`() {
        val olderApiEpisode = TestFixtures.episode(id = "ep-old").copy(publishedDate = 1000L)
        val newerLocalEpisode = TestFixtures.episode(id = "ep-new").copy(publishedDate = 2000L)

        val apiPodcast = TestFixtures.podcast(id = "p1").copy(latestEpisode = olderApiEpisode)
        val currentPodcast = TestFixtures.podcast(id = "p1").copy(subscribedAt = 100L)
        val localPodcast = TestFixtures.podcast(id = "p1").copy(subscribedAt = 100L, latestEpisode = newerLocalEpisode)

        val enriched =
            PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                apiPodcast = apiPodcast,
                currentPodcast = currentPodcast,
                localPodcast = localPodcast,
                pageEpisodes = emptyList(),
                sortParam = "newest",
            )

        assertEquals("ep-new", enriched.latestEpisode?.id)
        assertEquals(2000L, enriched.latestEpisode?.publishedDate)
    }
}
