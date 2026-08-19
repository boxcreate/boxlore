package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room coverage for [SubscriptionRepository]. Firebase / ranking side-effects are
 * fire-and-forget (guarded by try/catch and `getIfInitialized()`), so they no-op hermetically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var repository: SubscriptionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        podcastDao = database.podcastDao()
        repository = SubscriptionRepository(podcastDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun podcast(
        id: String = "pod-1",
        title: String = "Show",
        type: String = "episodic",
        sourceType: String = Podcast.SOURCE_PODCAST_INDEX,
        feedUrl: String? = null,
        linkedPodcastIndexId: String? = null,
    ) = Podcast(
        id = id,
        title = title,
        artist = "Artist",
        imageUrl = "https://example.com/$id.jpg",
        description = "desc",
        genre = "Technology",
        type = type,
        sourceType = sourceType,
        feedUrl = feedUrl,
        linkedPodcastIndexId = linkedPodcastIndexId,
    )

    @Test
    fun subscribePersistsSubscribedEntity() =
        runTest {
            repository.subscribe(podcast())

            val stored = podcastDao.getPodcast("pod-1")!!
            assertTrue(stored.isSubscribed)
            assertEquals("Show", stored.title)
            assertTrue(repository.isSubscribed("pod-1"))
        }

    @Test
    fun subscribeSerialDefaultsToOldestSort() =
        runTest {
            repository.subscribe(podcast(type = "serial"))

            val stored = podcastDao.getPodcast("pod-1")!!
            assertEquals("oldest", stored.preferredSort)
            assertEquals("serial", stored.type)
        }

    @Test
    fun subscribeEpisodicDefaultsToNewestSort() =
        runTest {
            repository.subscribe(podcast(type = "episodic"))

            assertEquals("newest", podcastDao.getPodcast("pod-1")!!.preferredSort)
        }

    @Test
    fun subscribeIsSkippedWhenLinkedRssAlreadySubscribed() =
        runTest {
            // A subscribed RSS row links to the podcast-index id; subscribing the index copy is a no-op.
            podcastDao.upsert(
                PodcastEntity(
                    podcastId = "-2001",
                    title = "RSS Copy",
                    author = "Artist",
                    imageUrl = "",
                    description = null,
                    isSubscribed = true,
                    sourceType = PodcastEntity.SOURCE_RSS,
                    linkedPodcastIndexId = "pod-1",
                ),
            )

            repository.subscribe(podcast(id = "pod-1"))

            assertNull(podcastDao.getPodcast("pod-1"))
            assertTrue(repository.isSubscribed("pod-1"))
        }

    @Test
    fun toggleSubscriptionSubscribesThenUnsubscribes() =
        runTest {
            repository.toggleSubscription(podcast())
            assertTrue(podcastDao.getPodcast("pod-1")!!.isSubscribed)

            repository.toggleSubscription(podcast())
            val after = podcastDao.getPodcast("pod-1")!!
            assertFalse(after.isSubscribed)
            assertEquals(0L, after.subscribedAt)
        }

    @Test
    fun subscribedPodcastIdsReflectsSubscriptions() =
        runTest {
            repository.subscribe(podcast(id = "a", title = "A"))
            repository.subscribe(podcast(id = "b", title = "B"))

            assertEquals(setOf("a", "b"), repository.subscribedPodcastIds.first())
            assertEquals(
                setOf("a", "b"),
                repository.subscribedPodcasts
                    .first()
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun isSubscribedFalseForUnknown() =
        runTest {
            assertFalse(repository.isSubscribed("nobody"))
        }

    @Test
    fun setNotificationsEnabledForRssForcesOff() =
        runTest {
            repository.subscribe(podcast(id = "-3001", sourceType = Podcast.SOURCE_RSS, feedUrl = "https://feed"))
            repository.setNotificationsEnabled(podcast(id = "-3001", sourceType = Podcast.SOURCE_RSS), true)

            val stored = podcastDao.getPodcast("-3001")!!
            assertFalse(stored.notificationsEnabled)
            assertFalse(stored.autoDownloadEnabled)
        }

    @Test
    fun setAutoDownloadEnabledForRssForcesOff() =
        runTest {
            repository.subscribe(podcast(id = "-3002", sourceType = Podcast.SOURCE_RSS, feedUrl = "https://feed"))
            repository.setAutoDownloadEnabled("-3002", true)

            assertFalse(podcastDao.getPodcast("-3002")!!.autoDownloadEnabled)
        }

    @Test
    fun setAutoDownloadEnabledForIndexPodcastPersists() =
        runTest {
            repository.subscribe(podcast(id = "pod-1"))
            repository.setAutoDownloadEnabled("pod-1", true)

            assertTrue(podcastDao.getPodcast("pod-1")!!.autoDownloadEnabled)
        }

    @Test
    fun updatePreferredSortUpdatesTypeToo() =
        runTest {
            repository.subscribe(podcast())
            repository.updatePreferredSort("pod-1", "oldest")

            val stored = podcastDao.getPodcast("pod-1")!!
            assertEquals("oldest", stored.preferredSort)
            assertEquals("serial", stored.type)
        }

    @Test
    fun setPlaybackSkipOverridesPersist() =
        runTest {
            repository.subscribe(podcast())
            repository.setPlaybackSkipOverrides("pod-1", 5_000L, 10_000L)

            val stored = podcastDao.getPodcast("pod-1")!!
            assertEquals(5_000L, stored.skipBeginningOverrideMs)
            assertEquals(10_000L, stored.skipEndingOverrideMs)
        }

    @Test
    fun clearRssNewEpisodesFlagClearsBadge() =
        runTest {
            podcastDao.upsert(
                PodcastEntity(
                    podcastId = "-4001",
                    title = "RSS",
                    author = "A",
                    imageUrl = "",
                    description = null,
                    isSubscribed = true,
                    sourceType = PodcastEntity.SOURCE_RSS,
                    rssHasNewEpisodes = true,
                ),
            )

            repository.clearRssNewEpisodesFlag("-4001")

            assertFalse(podcastDao.getPodcast("-4001")!!.rssHasNewEpisodes)
        }

    @Test
    fun updateLatestEpisodeBackfillsPodcastTitle() =
        runTest {
            repository.subscribe(podcast(id = "pod-1", title = "My Show"))
            val episode =
                cx.aswin.boxlore.core.model.Episode(
                    id = "ep-1",
                    title = "Ep 1",
                    description = "d",
                    audioUrl = "https://example.com/ep1.mp3",
                    podcastId = "",
                    podcastTitle = null,
                )

            repository.updateLatestEpisode("pod-1", episode)

            val stored = podcastDao.getPodcast("pod-1")!!.latestEpisode!!
            assertEquals("pod-1", stored.podcastId)
            assertEquals("My Show", stored.podcastTitle)
            assertFalse(podcastDao.getPodcast("pod-1")!!.rssHasNewEpisodes)
        }

    @Test
    fun updateLatestEpisodeMarkAsNewSetsSharedBadge() =
        runTest {
            repository.subscribe(podcast(id = "1258562", title = "Desi Crime"))
            val episode =
                cx.aswin.boxlore.core.model.Episode(
                    id = "-9001",
                    title = "203. Feed tip",
                    description = "d",
                    audioUrl = "https://example.com/ep.mp3",
                    podcastId = "1258562",
                )

            repository.updateLatestEpisode(
                podcastId = "1258562",
                episode = episode.copy(publishedDate = 100L),
                markAsNew = true,
            )
            repository.updateLatestEpisode(
                podcastId = "1258562",
                episode = episode.copy(id = "-9002", publishedDate = 200L),
                markAsNew = true,
            )

            assertTrue(podcastDao.getPodcast("1258562")!!.rssHasNewEpisodes)
            assertEquals("-9002", podcastDao.getPodcast("1258562")!!.latestEpisode!!.id)
        }

    @Test
    fun updateLatestEpisodeIgnoresOlderTip() =
        runTest {
            repository.subscribe(podcast(id = "pod-tip", title = "Show"))
            val newer =
                cx.aswin.boxlore.core.model.Episode(
                    id = "-9",
                    title = "Feed tip",
                    description = "d",
                    audioUrl = "https://example.com/new.mp3",
                    podcastId = "pod-tip",
                    publishedDate = 200L,
                )
            val older =
                cx.aswin.boxlore.core.model.Episode(
                    id = "1",
                    title = "PI tip",
                    description = "d",
                    audioUrl = "https://example.com/old.mp3",
                    podcastId = "pod-tip",
                    publishedDate = 100L,
                )

            repository.updateLatestEpisode(
                "pod-tip",
                older.copy(id = "0", publishedDate = 50L),
            )
            repository.updateLatestEpisode("pod-tip", newer)
            repository.updateLatestEpisode("pod-tip", older)

            val stored = podcastDao.getPodcast("pod-tip")!!.latestEpisode!!
            assertEquals("-9", stored.id)
            assertEquals(200L, stored.publishedDate)
            assertFalse(podcastDao.getPodcast("pod-tip")!!.rssHasNewEpisodes)
        }

    @Test
    fun publisherFeedAuthoritativeReplacesNewerPiCrossPromo() =
        runTest {
            repository.subscribe(podcast(id = "pod-tip", title = "Show"))
            val piCrossPromo =
                cx.aswin.boxlore.core.model.Episode(
                    id = "99",
                    title = "Cross promo",
                    description = "d",
                    audioUrl = "https://example.com/cross-promo.mp3",
                    podcastId = "pod-tip",
                    publishedDate = 200L,
                )
            val publisherTip =
                piCrossPromo.copy(
                    id = "-9",
                    title = "Publisher episode",
                    audioUrl = "https://example.com/publisher.mp3",
                    publishedDate = 100L,
                )
            repository.updateLatestEpisode("pod-tip", piCrossPromo)

            repository.updateLatestEpisode(
                podcastId = "pod-tip",
                episode = publisherTip,
                publisherFeedAuthoritative = true,
            )

            assertEquals("-9", podcastDao.getPodcast("pod-tip")!!.latestEpisode!!.id)
        }

    @Test
    fun updateLatestEpisodeSameDateDifferentIdKeepsExisting() =
        runTest {
            repository.subscribe(podcast(id = "pod-catchup", title = "Show"))
            val feedOnly =
                cx.aswin.boxlore.core.model.Episode(
                    id = "-9",
                    title = "Same drop",
                    description = "d",
                    audioUrl = "https://example.com/a.mp3",
                    podcastId = "pod-catchup",
                    publishedDate = 100L,
                )
            val piCatchUp =
                cx.aswin.boxlore.core.model.Episode(
                    id = "55",
                    title = "Same drop",
                    description = "d",
                    audioUrl = "https://example.com/a.mp3",
                    podcastId = "pod-catchup",
                    publishedDate = 100L,
                )

            repository.updateLatestEpisode("pod-catchup", feedOnly)
            repository.updateLatestEpisode("pod-catchup", piCatchUp)

            assertEquals("-9", podcastDao.getPodcast("pod-catchup")!!.latestEpisode!!.id)
        }

    @Test
    fun ensureHttpsFeedUrlWritesWhenRowExists() =
        runTest {
            repository.subscribe(podcast(id = "pod-1"))
            repository.ensureHttpsFeedUrl("pod-1", "https://feeds.example/show.xml")
            assertEquals("https://feeds.example/show.xml", podcastDao.getPodcast("pod-1")!!.feedUrl)
        }

    @Test
    fun ensureHttpsFeedUrlIgnoresHttpAndMissingRows() =
        runTest {
            repository.subscribe(podcast(id = "pod-1", feedUrl = "https://feeds.example/old.xml"))
            repository.ensureHttpsFeedUrl("pod-1", "http://insecure.example/show.xml")
            repository.ensureHttpsFeedUrl("missing", "https://feeds.example/show.xml")
            assertEquals("https://feeds.example/old.xml", podcastDao.getPodcast("pod-1")!!.feedUrl)
        }

    @Test
    fun subscribeRecoversHttpsFeedUrlWhenMissing() =
        runTest {
            var lookups = 0
            val recovering =
                SubscriptionRepository(
                    podcastDao = podcastDao,
                    lookupHttpsFeedUrl = {
                        lookups++
                        "https://feeds.example/recovered.xml"
                    },
                )
            recovering.subscribe(podcast(id = "pod-recover"))
            assertEquals(1, lookups)
            assertEquals(
                "https://feeds.example/recovered.xml",
                podcastDao.getPodcast("pod-recover")!!.feedUrl,
            )
        }

    @Test
    fun subscribePersistsSuppliedHttpsWhenStoredFeedUrlBlank() =
        runTest {
            podcastDao.upsert(
                PodcastEntity(
                    podcastId = "pod-blank",
                    title = "Show",
                    author = "Artist",
                    imageUrl = "https://example.com/pod-blank.jpg",
                    description = "desc",
                    isSubscribed = false,
                    feedUrl = "",
                ),
            )
            var lookups = 0
            val recovering =
                SubscriptionRepository(
                    podcastDao = podcastDao,
                    lookupHttpsFeedUrl = {
                        lookups++
                        "https://feeds.example/lookup.xml"
                    },
                )
            recovering.subscribe(
                podcast(id = "pod-blank", feedUrl = "https://feeds.example/supplied.xml"),
            )
            assertEquals(0, lookups)
            assertEquals(
                "https://feeds.example/supplied.xml",
                podcastDao.getPodcast("pod-blank")!!.feedUrl,
            )
        }
}
