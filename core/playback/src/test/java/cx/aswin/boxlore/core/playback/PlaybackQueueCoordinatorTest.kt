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
        val api = NetworkModule.createBoxLoreApi("https://api.boxlore.test", context)
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
            )
    }

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
