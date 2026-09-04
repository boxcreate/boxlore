package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.media3.session.MediaController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SameShowContinuationCoordinatorTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastRepository: PodcastRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var queueCoordinator: PlaybackQueueCoordinator
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mediaHandle: PlaybackMediaControllerHandle
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val playerStateFlow = MutableStateFlow(PlayerState())

    private val feedId = "rss:-1001"
    private val dbPodcastId = "rss:-1001"

    private suspend fun insertTestShow(type: String = "serial", count: Int = 4,) {
        val podcastEntity =
            PodcastEntity(
                podcastId = dbPodcastId,
                title = "Test Show",
                author = "Test Artist",
                imageUrl = "https://example.com/art.jpg",
                description = "Show description",
                type = type,
                sourceType = PodcastEntity.SOURCE_RSS,
            )
        database.podcastDao().upsert(podcastEntity)

        val episodeEntities =
            (1..count).map { i ->
                RssEpisodeEntity(
                    episodeId = "-$i",
                    podcastId = dbPodcastId,
                    guid = "guid-$i",
                    title = "Episode $i",
                    description = "Desc $i",
                    audioUrl = "https://example.com/$i.mp3",
                    imageUrl = "https://example.com/art.jpg",
                    duration = 1800,
                    publishedDate = i * 1000L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = i,
                    episodeType = "full",
                    enclosureType = "audio/mpeg",
                )
            }
        database.rssEpisodeDao().upsertAll(episodeEntities)
    }

    private fun createEpisode(id: String, pubDate: Long = 1000L, contextSourceId: String? = "home_for_you",): Episode = Episode(
        id = id,
        title = "Episode $id",
        description = "Desc $id",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = feedId,
        podcastTitle = "Test Show",
        publishedDate = pubDate,
        contextSourceId = contextSourceId,
    )

    private fun createPodcast(type: String = "serial"): Podcast = Podcast(
        id = feedId,
        title = "Test Show",
        artist = "Test Artist",
        imageUrl = "https://example.com/art.jpg",
        type = type,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val rss = RssPodcastRepository.createForTests(context = context, database = database)
        val api = NetworkModule.createBoxLoreApi("http://localhost/", context)
        podcastRepository =
            PodcastRepository(
                baseUrl = "http://localhost/",
                publicKey = "test-key",
                context = context,
                rssRepository = rss,
                ioDispatcher = testDispatcher,
                boxLoreApi = api,
            )
        queueRepository = QueueRepository(database, podcastRepository)
        userPreferencesRepository = UserPreferencesRepository(context)

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
                prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE),
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
    fun `evaluate sets visible continuation state when recommendation origin has candidates`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 4)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        val current = createEpisode("-1", pubDate = 1000L)
        val podcast = createPodcast(type = "serial")

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = listOf(current),
            )

        coordinator.evaluate(current, sameShowQueueOnly = false)

        val state = playerStateFlow.value.sameShowContinuation
        assertTrue(state.visible)
        assertEquals(3, state.availableCount)
        assertEquals(listOf("-2", "-3", "-4"), state.nextEpisodes.map { it.id })
        assertEquals("Test Show", state.podcastTitle)
    }

    @Test
    fun `dismissBanner hides continuation and prevents re-evaluation on same track`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 3)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        val current = createEpisode("-1", pubDate = 1000L)
        val podcast = createPodcast()

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = listOf(current),
            )

        coordinator.evaluate(current, sameShowQueueOnly = false)
        assertTrue(playerStateFlow.value.sameShowContinuation.visible)

        coordinator.dismissBanner()
        assertFalse(playerStateFlow.value.sameShowContinuation.visible)

        // Re-evaluating on the same track keeps it hidden
        coordinator.evaluate(current, sameShowQueueOnly = false)
        assertFalse(playerStateFlow.value.sameShowContinuation.visible)
    }

    @Test
    fun `track change resets dismissal and re-evaluates`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 4)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        val ep1 = createEpisode("-1", pubDate = 1000L)
        val ep2 = createEpisode("-2", pubDate = 2000L)
        val podcast = createPodcast()

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = ep1,
                currentPodcast = podcast,
                queue = listOf(ep1),
            )

        coordinator.evaluate(ep1, sameShowQueueOnly = false)
        assertTrue(playerStateFlow.value.sameShowContinuation.visible)

        coordinator.dismissBanner()
        assertFalse(playerStateFlow.value.sameShowContinuation.visible)

        // Track changes to ep2
        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = ep2,
                queue = listOf(ep2),
            )
        coordinator.evaluate(ep2, sameShowQueueOnly = false)

        // Dismissal was reset for new track
        assertTrue(playerStateFlow.value.sameShowContinuation.visible)
        assertEquals(2, playerStateFlow.value.sameShowContinuation.availableCount)
    }

    @Test
    fun `addContinuationEpisodes inserts episodes tagged with SOURCE_SAME_PODCAST after current and hides banner`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 3)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        val playing = createEpisode("-1", pubDate = 1000L)
        val existingQueued = createEpisode("ep-other", pubDate = 500L, contextSourceId = "sub")
        val podcast = createPodcast()

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = playing,
                currentPodcast = podcast,
                queue = listOf(playing, existingQueued),
            )

        coordinator.evaluate(playing, sameShowQueueOnly = false)
        assertTrue(playerStateFlow.value.sameShowContinuation.visible)
        assertEquals(2, playerStateFlow.value.sameShowContinuation.availableCount)

        coordinator.addContinuationEpisodes()

        // Banner is dismissed
        assertFalse(playerStateFlow.value.sameShowContinuation.visible)

        // Queue now has -2 and -3 inserted right after -1, pushing ep-other down
        val newQueue = playerStateFlow.value.queue
        assertEquals(4, newQueue.size)
        assertEquals("-1", newQueue[0].id)
        assertEquals("-2", newQueue[1].id)
        assertEquals("-3", newQueue[2].id)
        assertEquals("ep-other", newQueue[3].id)

        // Inserted episodes are tagged with SOURCE_SAME_PODCAST
        assertEquals(SmartQueueEngine.SOURCE_SAME_PODCAST, newQueue[1].contextSourceId)
        assertEquals(SmartQueueEngine.SOURCE_SAME_PODCAST, newQueue[2].contextSourceId)
    }

    @Test
    fun `evaluate strictly hides banner when available candidates is 0`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 1)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        // User is playing the only episode in the feed (final episode)
        val current = createEpisode("-1", pubDate = 1000L)
        val podcast = createPodcast()

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = listOf(current),
            )

        coordinator.evaluate(current, sameShowQueueOnly = false)

        val state = playerStateFlow.value.sameShowContinuation
        assertFalse(state.visible)
        assertEquals(0, state.availableCount)
    }

    @Test
    fun `addContinuationEpisodes deduplicates against existing items in queue`() = runTest(testDispatcher) {
        insertTestShow(type = "serial", count = 4)
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        val current = createEpisode("-1", pubDate = 1000L)
        // Suppose -2 was already added to the queue by smart refill or user
        val alreadyQueued = createEpisode("-2", pubDate = 2000L)
        val other = createEpisode("other-ep", pubDate = 500L)
        val podcast = createPodcast()

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = listOf(current, alreadyQueued, other),
                // Banner was evaluated before -2 was queued:
                sameShowContinuation =
                SameShowContinuationState(
                    visible = true,
                    podcastTitle = "Test Show",
                    availableCount = 3,
                    nextEpisodes =
                    listOf(
                        createEpisode("-2", pubDate = 2000L),
                        createEpisode("-3", pubDate = 3000L),
                        createEpisode("-4", pubDate = 4000L),
                    ),
                ),
            )

        coordinator.addContinuationEpisodes()

        val queue = playerStateFlow.value.queue
        // -2 was already in queue, so it should not be inserted again
        assertEquals(listOf("-1", "-3", "-4", "-2", "other-ep"), queue.map { it.id })
        assertEquals(5, queue.size)
    }

    @Test
    fun `addEpisodesAfterCurrent preserves playing episode at index 0 when queue was empty`() = runTest(testDispatcher) {
        val current = createEpisode("-1", pubDate = 1000L)
        val podcast = createPodcast()
        val episodesToAdd =
            listOf(
                createEpisode("-2", pubDate = 2000L),
                createEpisode("-3", pubDate = 3000L),
            )

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = emptyList(),
            )

        queueCoordinator.addEpisodesAfterCurrent(episodesToAdd, podcast)

        val queue = playerStateFlow.value.queue
        assertEquals(3, queue.size)
        assertEquals("-1", queue[0].id)
        assertEquals("-2", queue[1].id)
        assertEquals("-3", queue[2].id)
    }

    @Test
    fun `addContinuationEpisodes fails and leaves banner visible when controller is unavailable`() = runTest(testDispatcher) {
        mediaHandle.controller = null

        val current = createEpisode("-1", pubDate = 1000L)
        insertTestShow(type = "serial", count = 4)
        val podcast = createPodcast()
        val coordinator =
            SameShowContinuationCoordinator(
                scope = testScope,
                playerState = playerStateFlow,
                playerStateFlow = playerStateFlow,
                podcastRepository = podcastRepository,
                userPreferencesRepository = userPreferencesRepository,
                queueCoordinator = queueCoordinator,
            )

        playerStateFlow.value =
            playerStateFlow.value.copy(
                currentEpisode = current,
                currentPodcast = podcast,
                queue = listOf(current),
                sameShowContinuation =
                SameShowContinuationState(
                    visible = true,
                    podcastTitle = "Test Show",
                    availableCount = 2,
                    nextEpisodes = listOf(createEpisode("-2"), createEpisode("-3")),
                ),
            )

        val success = coordinator.addContinuationEpisodes()

        org.junit.Assert.assertFalse(success)
        org.junit.Assert.assertTrue(playerStateFlow.value.sameShowContinuation.visible)
        assertEquals(1, playerStateFlow.value.queue.size)
    }
}
