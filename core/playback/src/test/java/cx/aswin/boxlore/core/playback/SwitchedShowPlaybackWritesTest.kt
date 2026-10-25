package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.SHOW
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.alias
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.history
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Writes for an episode of a show the listener switched to its RSS feed (docs/EPISODE_IDS.md §5,
 * decision D8): "A" is the Podcast Index show's ID and owner, "-1" the RSS show's linked ID.
 * Playing, seeking, liking or marking "-1" writes the owner's row and leaves it under the Podcast
 * Index show, with its own episode metadata, so sync and later links still find it there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwitchedShowPlaybackWritesTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val dao get() = database.listeningHistoryDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = EpisodeAliasFixtures.directDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aSeekAndALikeOnTheLinkedFeedIdKeepTheOwnersRowUnderItsShow() = runTest {
        linkFeedId()
        val owned = history("A", progress = 10L, playedAt = 40L)
        dao.upsert(owned)
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, this)

        historyStore.savePlaybackState(feedProgress(positionMs = 90_000L))
        historyStore.toggleLike(feedEpisode(), RSS_SHOW, "Feed show", "https://img.example/feed.jpg")

        val owner = dao.getHistoryItem("A")!!
        assertEquals(90_000L, owner.progressMs)
        assertTrue(owner.isLiked)
        assertEquals(
            listOf(owned.podcastId, owned.podcastName, owned.podcastImageUrl, owned.episodeTitle, owned.episodeAudioUrl),
            listOf(owner.podcastId, owner.podcastName, owner.podcastImageUrl, owner.episodeTitle, owner.episodeAudioUrl),
        )
        assertNull(dao.getHistoryItem(FEED_ID))
    }

    @Test
    fun aMissingOwnerRowIsCreatedUnderTheOwnersShow() = runTest {
        linkFeedId()
        database.podcastDao().upsert(PodcastEntity(podcastId = SHOW, title = "Index show", author = "", imageUrl = "https://img.example/show.jpg", description = null))
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, this)

        historyStore.toggleCompletion(feedEpisode(), RSS_SHOW, "Feed show", "https://img.example/feed.jpg")

        val owner = dao.getHistoryItem("A")!!
        assertTrue(owner.isCompleted)
        assertEquals(listOf(SHOW, "Index show", "https://img.example/show.jpg"), listOf(owner.podcastId, owner.podcastName, owner.podcastImageUrl))
        assertNull(dao.getHistoryItem(FEED_ID))
    }

    @Test
    fun listeningInsightsCountTheOldShowsListeningForTheFeedShow() = runTest {
        linkFeedId()
        dao.upsert(history("A", progress = 600_000L, playedAt = 40L))
        database.podcastDao().upsert(PodcastEntity(podcastId = SHOW, title = "Index show", author = "", imageUrl = "", description = null))
        database.podcastDao().upsert(
            PodcastEntity(podcastId = RSS_SHOW, title = "Feed show", author = "", imageUrl = "", description = null, isSubscribed = true, sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = SHOW),
        )
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, this)

        val summary = historyStore.observeInsights(ListeningPeriod.ALL).first()

        assertEquals(RSS_SHOW, summary.topShow?.podcastId)
    }

    /** "A" owns the episode; "-1" is the feed show's ID, linked to it under the Podcast Index show. */
    private suspend fun linkFeedId() {
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(alias("A", owner = true), alias(FEED_ID, createdAt = 2L).copy(provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK)),
        )
        assertEquals(listOf("A", FEED_ID), database.episodeListenerStore().memberIds(FEED_ID))
    }

    private fun feedEpisode() = EpisodeAliasFixtures.episode(FEED_ID).copy(podcastId = RSS_SHOW, podcastTitle = "Feed show", title = "Feed title")

    private fun feedProgress(positionMs: Long) = ListeningHistoryUpsertLogic.ProgressSaveInput(
        podcastId = RSS_SHOW,
        episodeId = FEED_ID,
        positionMs = positionMs,
        durationMs = 1_800_000L,
        episodeTitle = "Feed title",
        episodeImageUrl = null,
        podcastImageUrl = "https://img.example/feed.jpg",
        episodeAudioUrl = "https://feed.example/1.mp3",
        podcastName = "Feed show",
        isCompleted = false,
        isLiked = false,
        lastPlayedAt = 1_000L,
    )

    private companion object {
        const val RSS_SHOW = "rss:feed"
        const val FEED_ID = "-1"
    }
}
