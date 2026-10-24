package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.media3.session.MediaController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackQueueCoordinatorTest {
    @Test
    fun restoredContextQueueKeepsOrderAndShowMetadataInMediaItems() = runTest(testDispatcher) {
        val episodes = PlaybackQueueContext.episodes(listOf(testEpisode("rss:1", "rss:show-1"), testEpisode("2", "show-2")))
        queueRepository.replaceQueue(episodes)
        val restored = queueRepository.getQueueEpisodeSnapshot()
        val items = queueCoordinator.buildMediaItems(restored, cx.aswin.boxlore.core.model.Podcast("fallback", "Fallback", "Host", ""), null)
        assertEquals(listOf("rss:1", "2"), items.map { it.mediaId })
        assertEquals(listOf("Show rss:show-1", "Show show-2"), items.map { it.mediaMetadata.subtitle.toString() })
        assertTrue(items.all(PlaybackQueueContext::isContextItem))
    }

    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastRepository: PodcastRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var queueCoordinator: PlaybackQueueCoordinator
    private lateinit var mediaHandle: PlaybackMediaControllerHandle
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val playerStateFlow = MutableStateFlow(PlayerState())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        val rss = RssPodcastRepository.createForTests(context = context, database = database)
        val api = NetworkModule.createBoxLoreApi("https://api.boxlore.test", NetworkModule.createCachedClient(context))
        podcastRepository =
            PodcastRepository(
                baseUrl = "https://api.boxlore.test",
                publicKey = "test-key",
                context = context,
                rssRepository = rss,
                ioDispatcher = testDispatcher,
                boxLoreApi = api,
            )
        queueRepository = QueueRepository(database, podcastRepository)

        val mockController = mock(MediaController::class.java)
        `when`(mockController.mediaItemCount).thenReturn(0)
        `when`(mockController.playbackParameters).thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
        mediaHandle = PlaybackMediaControllerHandle()
        mediaHandle.controller = mockController

        queueCoordinator =
            PlaybackQueueCoordinator(
                scope = testScope,
                playerStateFlow = playerStateFlow,
                mediaHandle = mediaHandle,
                queueRepository = queueRepository,
                rankingFeedbackRepository = RankingFeedbackRepository.create(null),
                queueSkipMemory = QueueSkipMemory.fromContext(context),
                prefs = context.getSharedPreferences("test_queue_prefs", Context.MODE_PRIVATE),
                playerDismissedKey = "player_dismissed",
                queueMaxSize = 50,
                checkSavedProgress = { _, _, _, _ -> Pair(0L, false) },
                onPlaybackStarted = {},
                storePendingEntryPoint = {},
                ensureCurrentHistoryRow = {},
                stopProgressTicker = {},
                onQueueStarted = { queueStarts++ },
            )
    }

    /** Listener-started queues counted through the coordinator's hook. */
    private var queueStarts = 0

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun removeFromQueueRemovesTargetAndPreservesUnrelatedItems() = runTest(testDispatcher) {
        val ep1 = testEpisode("1", "pod-1")
        val ep2 = testEpisode("2", "pod-1")
        val ep3 = testEpisode("3", "pod-1")

        // Seed initial items in queue repository and playerStateFlow
        queueRepository.replaceQueue(listOf(ep1, ep2, ep3))
        playerStateFlow.value = playerStateFlow.value.copy(queue = listOf(ep1, ep2, ep3))

        val initialSeq = queueRepository.getQueueMetadata()?.queueSequence ?: 0L

        // Remove only 2 via the coordinator
        val removed = queueCoordinator.removeFromQueue("2")

        assertNotNull(removed)
        assertEquals("2", removed?.episode?.id)

        // Verify in-memory player state retains 1 and 3 in order
        val memoryQueue = playerStateFlow.value.queue
        assertEquals(listOf("1", "3"), memoryQueue.map { it.id })

        // Verify database persistence retains 1 and 3
        val dbQueue = queueRepository.queue.first()
        assertEquals(listOf("1", "3"), dbQueue.map { it.id.toString() })

        val snapshot = queueRepository.getQueueEpisodeSnapshot()
        assertEquals(listOf("1", "3"), snapshot.map { it.id })

        assertNotNull(queueRepository.getQueueItemByEpisodeId("1"))
        assertNull(queueRepository.getQueueItemByEpisodeId("2"))
        assertNotNull(queueRepository.getQueueItemByEpisodeId("3"))

        // Verify metadata sequence bumped exactly once and tombstone recorded
        val meta = queueRepository.getQueueMetadata()!!
        assertEquals(initialSeq + 1L, meta.queueSequence)
        assertTrue(meta.isDirty)
        assertEquals(listOf("2"), cx.aswin.boxlore.core.database.dao.QueueDao.parseRecentRemovedEpisodeIds(meta.recentRemovedEpisodeIds))
    }

    @Test
    fun episodesWithoutAnyArtworkStillBuildQueueItems() = runTest(testDispatcher) {
        // Regression (0.0.25 NPE "uriString"): no episode, show or podcast artwork reached Uri.parse(null).
        val bare = testEpisode("9", "pod-9").copy(imageUrl = null, podcastImageUrl = null)
        val podcast = cx.aswin.boxlore.core.model.Podcast("pod-9", "Show", "Host", "")

        val items = queueCoordinator.buildMediaItems(listOf(bare), podcast, null)

        assertNull(items.single().mediaMetadata.artworkUri)
        assertTrue(queueCoordinator.addToQueue(bare, podcast))
        queueCoordinator.addToQueueNext(testEpisode("10", "pod-9").copy(imageUrl = null, podcastImageUrl = null), podcast)
        assertEquals(listOf("9", "10"), playerStateFlow.value.queue.map { it.id })
    }

    @Test
    fun playNextOnQueuedEpisodeDoesNotDuplicateIt() = runTest(testDispatcher) {
        val podcast = cx.aswin.boxlore.core.model.Podcast("pod-1", "Show", "Host", "https://example.com/a.jpg")
        val current = testEpisode("1", "pod-1")
        val queued = testEpisode("2", "pod-1")
        playerStateFlow.value = playerStateFlow.value.copy(currentEpisode = current, queue = listOf(current, queued))

        queueCoordinator.addToQueueNext(queued, podcast)
        queueCoordinator.addToQueueNext(queued, podcast)

        assertEquals(listOf("1", "2"), playerStateFlow.value.queue.map { it.id })
    }

    @Test
    fun everyListenerStartedQueueResetsStreamFailureCounting() = runTest(testDispatcher) {
        // Regression: only PlaybackRepository.playQueue reset the skip guard, so a fresh episode tapped
        // after two failures could stop on its first failure.
        val podcast = cx.aswin.boxlore.core.model.Podcast("pod-1", "Show", "Host", "https://example.com/a.jpg")
        val episodes = listOf(testEpisode("1", "pod-1"), testEpisode("2", "pod-1"))

        queueCoordinator.playEpisode(episodes.first(), podcast)
        queueCoordinator.playFromQueueIndex("2", episodes, podcast)
        queueCoordinator.playQueue(episodes, podcast, 1)
        advanceUntilIdle()

        assertEquals(3, queueStarts)
    }

    private fun testEpisode(id: String, podcastId: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "Description $id",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = podcastId,
        podcastTitle = "Show $podcastId",
        duration = 1800,
        publishedDate = 1700000000L,
    )
}
