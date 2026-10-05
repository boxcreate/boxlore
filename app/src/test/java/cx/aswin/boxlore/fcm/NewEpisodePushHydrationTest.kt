package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewEpisodePushHydrationTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        subscriptionRepository = SubscriptionRepository(database.podcastDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun skipsWhenNotOptedIn() = runBlocking {
        val port = FakePort(optedIn = emptySet(), tip = episode("-9"))
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertNull(result)
        assertEquals(0, port.resolveCalls)
        assertEquals(0, port.refreshCalls)
    }

    @Test
    fun refreshPersistsTipButDoesNotHydrateWithoutEnclosureOrGuid() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val tip = episode("-9")
        val port =
            FakePort(
                optedIn = setOf("123"),
                tip = tip,
                cached = listOf(tip),
                refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 1,
                    totalSupplementCount = 1,
                    newestFeedEpisode = tip,
                ),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = null,
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                    loadPiBaseline = { emptyList() },
                ),
            )
        assertNull(result)
        assertEquals(1, port.refreshCalls)
        assertEquals(0, port.resolveCalls)
        assertTrue(port.baselineLoaded)
        val stored = subscriptionRepository.getPodcastEntity("123")
        assertEquals("-9", stored?.latestEpisode?.id)
        assertTrue(stored?.rssHasNewEpisodes != true)
    }

    @Test
    fun fallsBackToCachedEnclosureWhenRefreshReturnsNull() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val cached = episode("-8", audioUrl = "https://cdn.example.com/ep.mp3")
        val port =
            FakePort(
                optedIn = setOf("123"),
                tip = null,
                cached = listOf(cached),
                refreshOutcome = EpisodeSupplementOutcome.Failure("feed down"),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertEquals("-8", result?.id)
    }

    @Test
    fun returnsNullWhenResolveThrowsAndNoCachedMatch() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val port =
            FakePort(
                optedIn = setOf("123"),
                throwOnResolve = true,
                throwOnRefresh = true,
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/missing.mp3",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertNull(result)
        assertNull(subscriptionRepository.getPodcastEntity("123")?.latestEpisode)
    }

    @Test
    fun doesNotPromoteUnrelatedNewestWhenPayloadEnclosureDiffers() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val newest = episode("-9", audioUrl = "https://cdn.example.com/newer.mp3")
        val cached = episode("-8", audioUrl = "https://cdn.example.com/ep.mp3")
        val port =
            FakePort(
                optedIn = setOf("123"),
                tip = newest,
                cached = listOf(cached, newest),
                refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 1,
                    totalSupplementCount = 2,
                    newestFeedEpisode = newest,
                ),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                payloadGuid = "guid-ep",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertEquals("-8", result?.id)
        assertEquals(1, port.refreshCalls)
        assertEquals(0, port.resolveCalls)
        assertEquals("-9", subscriptionRepository.getPodcastEntity("123")?.latestEpisode?.id)
    }

    @Test
    fun promotesMatchedGuidWhenEnclosureIsAbsent() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val tip = episode("-9", audioUrl = "https://cdn.example.com/guid-only.mp3")
        val port =
            FakePort(
                optedIn = setOf("123"),
                tip = tip,
                tipGuid = "guid-ep",
                cached = listOf(tip),
                refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 1,
                    totalSupplementCount = 1,
                    newestFeedEpisode = tip,
                ),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = null,
                payloadGuid = "guid-ep",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertEquals("-9", result?.id)
        assertEquals("guid-ep", port.lastMatch?.guid)
        assertNull(port.lastMatch?.enclosureUrl)
    }

    @Test
    fun returnsNullWhenIdentifiedPayloadDoesNotMatchNewestTip() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val newest = episode("-9", audioUrl = "https://cdn.example.com/newer.mp3")
        val port =
            FakePort(
                optedIn = setOf("123"),
                tip = newest,
                cached = listOf(newest),
                refreshOutcome =
                EpisodeSupplementOutcome.Success(
                    addedCount = 1,
                    totalSupplementCount = 1,
                    newestFeedEpisode = newest,
                ),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/missing.mp3",
                payloadGuid = "guid-missing",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    episodeSupplementPort = port,
                ),
            )
        assertNull(result)
    }

    @Test
    fun piBaselineLoaderForwardsPodcastIdAndLimit1000() = runBlocking {
        var seenFeed: String? = null
        var seenLimit: Int? = null
        val loader =
            NewEpisodePushHydration.piBaselineLoader { feedId, limit ->
                seenFeed = feedId
                seenLimit = limit
                emptyList()
            }
        loader("123")
        assertEquals("123", seenFeed)
        assertEquals(1000, seenLimit)
    }

    @Test
    fun localCatalogMatchesEnclosureAndSkipsBlankKeys() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val cached = episode("-8", audioUrl = "https://cdn.example.com/ep.mp3")
        val catalog =
            cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort(
                readyIds = setOf("123"),
                episodes = mutableMapOf("-8" to cached),
            )
        val matched =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                payloadGuid = "guid-ep",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    localEpisodeCatalog = catalog,
                ),
            )
        assertEquals("-8", matched?.id)
        assertEquals(0, catalog.refreshCalls)
        val blank =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = null,
                payloadGuid = null,
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    localEpisodeCatalog = catalog,
                ),
            )
        assertNull(blank)
    }

    @Test
    fun localCatalogStillResolvesAfterRefreshFailure() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val cached = episode("-8", audioUrl = "https://cdn.example.com/ep.mp3")
        val catalog =
            cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort(
                readyIds = setOf("123"),
                episodes = mutableMapOf("-8" to cached),
            )
        catalog.refreshError = IllegalStateException("feed down")
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    localEpisodeCatalog = catalog,
                ),
            )
        assertEquals("-8", result?.id)
    }

    @Test
    fun localCatalogPropagatesCancellation() {
        val catalog =
            cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort(
                readyIds = setOf("123"),
            )
        catalog.refreshError = kotlinx.coroutines.CancellationException("cancelled")
        val error =
            kotlin
                .runCatching {
                    runBlocking {
                        NewEpisodePushHydration.resolveLocalEpisode(
                            podcastId = "123",
                            payloadFeedUrl = "https://feeds.example/show.xml",
                            payloadEnclosureUrl = "https://cdn.example.com/ep.mp3",
                            sources =
                            NewEpisodePushHydration.Sources(
                                subscriptionRepository = subscriptionRepository,
                                localEpisodeCatalog = catalog,
                            ),
                        )
                    }
                }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun localCatalogReturnsNullWhenPayloadDoesNotMatch() = runBlocking {
        subscriptionRepository.subscribe(
            Podcast(
                id = "123",
                title = "Show",
                artist = "A",
                imageUrl = "https://img",
                description = "d",
                genre = "News",
                feedUrl = "https://feeds.example/show.xml",
            ),
        )
        val catalog =
            cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort(
                readyIds = setOf("123"),
                episodes =
                mutableMapOf(
                    "-8" to episode("-8", audioUrl = "https://cdn.example.com/ep.mp3"),
                ),
            )
        val result =
            NewEpisodePushHydration.resolveLocalEpisode(
                podcastId = "123",
                payloadFeedUrl = "https://feeds.example/show.xml",
                payloadEnclosureUrl = "https://cdn.example.com/missing.mp3",
                payloadGuid = "guid-missing",
                sources =
                NewEpisodePushHydration.Sources(
                    subscriptionRepository = subscriptionRepository,
                    localEpisodeCatalog = catalog,
                ),
            )
        assertNull(result)
    }

    @Test fun pureRssPushRefreshesSavedFeedAndHydratesCanonicalNegativeIdWithoutCatalogLookup() = runBlocking {
        val id = "rss:private-show"
        val savedUrl = "https://publisher.example/feed?token=private"
        val context = ApplicationProvider.getApplicationContext<Context>()
        database.podcastDao().upsert(
            cx.aswin.boxlore.core.database.PodcastEntity(
            id,
                "RSS",
                "Author",
                "",
                null,
                sourceType = cx.aswin.boxlore.core.database.PodcastEntity.SOURCE_RSS,
            isSubscribed = true,
                feedUrl = savedUrl,
        )
        )
        var body = """<rss version="2.0"><channel><title>RSS</title><item><guid>old</guid><title>Old</title><pubDate>Wed, 01 Jan 2020 00:00:00 GMT</pubDate><enclosure url="https://cdn/old.mp3"/></item></channel></rss>"""
        var gets = 0
        val client = object : cx.aswin.boxlore.core.rss.RssFeedClient() {
            override suspend fun fetch(url: String): cx.aswin.boxlore.core.rss.RssFetchResult {
                assertEquals(savedUrl, url)
                gets++
                return cx.aswin.boxlore.core.rss.RssFetchResult(url, null, null, body.toByteArray())
            }
        }
        val rss = cx.aswin.boxlore.core.rss.RssPodcastRepository.createForTests(context, database, client)
        rss.refreshCatalog(id).getOrThrow()
        val oldId = rss.episodeCatalog.findByCatalogKey(id, "old", null)!!.id
        body = """<rss version="2.0"><channel><title>RSS</title><item><guid>new</guid><title>New</title><pubDate>Thu, 02 Jan 2020 00:00:00 GMT</pubDate><enclosure url="https://cdn/new.mp3"/></item></channel></rss>"""
        val shared = cx.aswin.boxlore.core.catalog.SubscribedEpisodeCatalog(
            cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository.create(database, client),
            rss.episodeCatalog,
        )
        val sources = NewEpisodePushHydration.Sources(
            subscriptionRepository,
            localEpisodeCatalog = shared,
            loadPiBaseline = { error("Pure RSS cannot query the catalog") }
        )
        val matched = NewEpisodePushHydration.resolveLocalEpisode(id, "https://unrelated.example/feed", "https://cdn/new.mp3", "new", sources)!!
        assertEquals(id, matched.podcastId)
        assertTrue(matched.id.toLong() < 0)
        assertEquals("https://cdn/new.mp3", matched.audioUrl)
        assertEquals(2, gets)
        assertEquals(oldId, rss.episodeCatalog.findByCatalogKey(id, "old", null)!!.id)
        assertEquals(matched.id, NewEpisodePushHydration.resolveLocalEpisode(id, null, "https://cdn/new.mp3", "new", sources)!!.id)
        assertEquals(2, gets)
        assertNull(NewEpisodePushHydration.resolveLocalEpisode(id, null, "https://cdn/missing.mp3", "missing", sources))
    }

    private fun episode(id: String, audioUrl: String = "https://cdn.example.com/ep.mp3",) = Episode(
        id = id,
        title = "Feed ep",
        description = "d",
        audioUrl = audioUrl,
        podcastId = "123",
        publishedDate = 200L,
        duration = 1800,
    )

    @Test
    fun unindexedPushForcesFreshPublisherFeedDuringHeaderlessQuietPeriod() = runBlocking {
        database.podcastDao().upsert(
            cx.aswin.boxlore.core.database.PodcastEntity(
            "123",
                "Show",
                "Author",
                "",
                null,
                isSubscribed = true,
                autoDownloadEnabled = true,
            feedUrl = "https://feeds.example/show.xml"
            )
        )
        var body = """<rss version="2.0"><channel><title>Show</title><item><guid>old</guid><title>Old</title><enclosure url="https://cdn/old.mp3"/></item></channel></rss>"""
        var fetches = 0
        val feed = object : cx.aswin.boxlore.core.rss.RssFeedClient() {
            override suspend fun fetch(url: String): cx.aswin.boxlore.core.rss.RssFetchResult {
                fetches++
                return cx.aswin.boxlore.core.rss.RssFetchResult(url, null, null, body.toByteArray())
            }
        }
        val catalog = cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository.create(database, feed)
        catalog.refresh(cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest("123", "https://feeds.example/show.xml"))
        body = """<rss version="2.0"><channel><title>Show</title><item><guid>new-guid</guid><title>Release</title><enclosure url="https://cdn/new.mp3"/></item></channel></rss>"""
        var piCalls = 0
        val sources = NewEpisodePushHydration.Sources(
            subscriptionRepository,
            localEpisodeCatalog = catalog,
            loadPiBaseline = {
                piCalls++
                emptyList()
            }
        )
        val result = NewEpisodePushHydration.resolveLocalEpisode("123", null, "https://cdn/new.mp3", "new-guid", sources)
        assertEquals("https://cdn/new.mp3", result?.audioUrl)
        assertTrue(result!!.id.toLong() < 0)
        assertEquals(2, fetches)
        assertEquals(0, piCalls)
        // Repeated pushes keep the canonical ID and do not fetch again.
        assertEquals(result.id, NewEpisodePushHydration.resolveLocalEpisode("123", null, "https://cdn/new.mp3", "new-guid", sources)?.id)
        assertEquals(2, fetches)
    }

    private class FakePort(
        var optedIn: Set<String> = emptySet(),
        var tip: Episode? = null,
        var cached: List<Episode> = emptyList(),
        var throwOnResolve: Boolean = false,
        var throwOnRefresh: Boolean = false,
        var tipGuid: String? = null,
        var refreshOutcome: EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect,
    ) : EpisodeSupplementPort {
        var resolveCalls: Int = 0
        var refreshCalls: Int = 0
        var lastMatch: EpisodeSupplementPort.FeedItemMatch? = null
        var baselineLoaded: Boolean = false

        override suspend fun refreshFromFeed(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome {
            refreshCalls += 1
            if (throwOnRefresh) error("feed down")
            return refreshOutcome
        }

        override suspend fun refreshFromFeed(request: EpisodeSupplementPort.RefreshFromFeedRequest): EpisodeSupplementOutcome {
            request.loadBaseline?.invoke()?.also { baselineLoaded = true }
            return refreshFromFeed(
                podcastIndexId = request.podcastIndexId,
                feedUrl = request.feedUrl,
                baselineEpisodes = request.baselineEpisodes,
                podcastTitle = request.podcastTitle,
                podcastImageUrl = request.podcastImageUrl,
                podcastGenre = request.podcastGenre,
                podcastArtist = request.podcastArtist,
            )
        }

        override suspend fun optInFromFeedIfDisconnected(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect

        override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean = podcastIndexId in optedIn

        override suspend fun listOptedInPodcastIds(): Set<String> = optedIn

        override suspend fun resolveNewestTipFromFeed(request: EpisodeSupplementPort.NewestTipRequest): Episode? {
            resolveCalls += 1
            lastMatch = request.match
            if (throwOnResolve) error("feed down")
            val guid =
                request.match
                    ?.guid
                    ?.trim()
                    .orEmpty()
            val enclosure =
                request.match
                    ?.enclosureUrl
                    ?.trim()
                    .orEmpty()
            if (guid.isEmpty() && enclosure.isEmpty()) return tip
            if (enclosure.isNotEmpty() && tip?.audioUrl?.trim() != enclosure) return null
            if (guid.isNotEmpty() && tipGuid != null && guid != tipGuid) return null
            if (guid.isNotEmpty() && enclosure.isEmpty() && tipGuid == null) return null
            return tip
        }

        override suspend fun getEpisodesForPodcast(
            podcastIndexId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = cached

        override suspend fun getEpisode(
            episodeId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): Episode? = cached.find { it.id == episodeId }

        override suspend fun search(
            podcastIndexId: String,
            query: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = emptyList()
    }
}
