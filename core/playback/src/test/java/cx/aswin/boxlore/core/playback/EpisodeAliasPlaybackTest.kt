package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey
import cx.aswin.boxlore.core.model.Podcast
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playback, history and the queue for an episode stored under two IDs (docs/EPISODE_IDS.md §5):
 * "A" owns the listener state and "B" holds the completed download.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeAliasPlaybackTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var store: EpisodeListenerStore
    private lateinit var queueRepository: QueueRepository
    private lateinit var queueCoordinator: PlaybackQueueCoordinator
    private val testDispatcher = UnconfinedTestDispatcher()
    private val playerStateFlow = MutableStateFlow(PlayerState())
    private val podcast = Podcast(SHOW, "Show", "Host", "")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        store = database.episodeListenerStore()
        val rss = RssPodcastRepository.createForTests(context = context, database = database)
        val api = NetworkModule.createBoxLoreApi("https://api.boxlore.test", NetworkModule.createCachedClient(context))
        val catalog = PodcastRepository(
            baseUrl = "https://api.boxlore.test",
            publicKey = "test-key",
            context = context,
            rssRepository = rss,
            ioDispatcher = testDispatcher,
            boxLoreApi = api,
        )
        queueRepository = QueueRepository(database, catalog)
        val controller = mock(MediaController::class.java)
        `when`(controller.mediaItemCount).thenReturn(0)
        `when`(controller.playbackParameters).thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
        val mediaHandle = PlaybackMediaControllerHandle().also { it.controller = controller }
        queueCoordinator = PlaybackQueueCoordinator(
            scope = TestScope(testDispatcher),
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            queueRepository = queueRepository,
            rankingFeedbackRepository = RankingFeedbackRepository.create(null),
            queueSkipMemory = QueueSkipMemory.fromContext(context),
            prefs = context.getSharedPreferences("alias_queue_prefs", Context.MODE_PRIVATE),
            playerDismissedKey = "player_dismissed",
            queueMaxSize = 50,
            checkSavedProgress = { _, _, _, _ -> Pair(0L, false) },
            onPlaybackStarted = {},
            storePendingEntryPoint = {},
            ensureCurrentHistoryRow = {},
            stopProgressTicker = {},
            episodeAliases = { store.memberIds(it) },
            downloadHolders = { store.downloadHolders(it) },
        )
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun offlinePlaybackReadsTheDownloadHoldersBytesWhileProgressGoesToTheOwner() = runTest(testDispatcher) {
        twoIds(ownerHistory = true, downloadOn = "B")

        // Opened from the show page (A) or from Downloads (B), both play B's downloaded bytes.
        val items = queueCoordinator.buildMediaItems(listOf(episode("A"), episode("B")), podcast, null, store.downloadHolders(listOf("A", "B")))
        assertEquals(listOf("A", "B"), items.map { it.mediaId })
        val downloadKey = EpisodeMediaCacheKey.of("B", episode("B").audioUrl)
        assertEquals(listOf(downloadKey, downloadKey), items.map { it.cacheKey() })

        // Progress of the item playing as B lands on A's row; B gets no row of its own.
        val progress = progressCoordinator()
        progress.saveProgressSnapshot(snapshot("B", positionMs = 120_000L))
        assertEquals(120_000L, database.listeningHistoryDao().getHistoryItem("A")!!.progressMs)
        assertNull(database.listeningHistoryDao().getHistoryItem("B"))

        // Finishing it completes the episode under every ID.
        progress.saveProgressSnapshot(snapshot("B", positionMs = 1_799_000L, sequence = 2L))
        assertTrue(database.listeningHistoryDao().getHistoryItem("A")!!.isCompleted)
        assertTrue(store.projectedHistory("B")!!.isCompleted)
        assertTrue(database.episodeIdentityDao().getGroupState(SHOW, KEY)!!.completedAt > 0L)
    }

    @Test
    fun anEpisodeStoredUnderOneIdKeepsItsOwnCacheKeyAndHistoryRow() = runTest(testDispatcher) {
        database.listeningHistoryDao().upsert(history("solo", progress = 10L))
        database.downloadedEpisodeDao().insert(download("solo"))

        val item = queueCoordinator.buildMediaItems(listOf(episode("solo")), podcast, null, store.downloadHolders(listOf("solo"))).single()
        assertEquals(EpisodeMediaCacheKey.of("solo", episode("solo").audioUrl), item.cacheKey())

        progressCoordinator().saveProgressSnapshot(snapshot("solo", positionMs = 60_000L))
        assertEquals(60_000L, database.listeningHistoryDao().getHistoryItem("solo")!!.progressMs)
    }

    @Test
    fun theQueueHoldsAnEpisodeOnceAndARemovalCoversEveryId() = runTest(testDispatcher) {
        twoIds(ownerHistory = true, downloadOn = null)
        queueRepository.replaceQueue(listOf(episode("A"), episode("C")))
        playerStateFlow.value = playerStateFlow.value.copy(queue = listOf(episode("A"), episode("C")))

        // Adding the same episode under its other ID is a duplicate.
        assertFalse(queueCoordinator.addToQueue(episode("B"), podcast))
        assertEquals(listOf("A", "C"), queueRepository.getQueueEpisodeSnapshot().map { it.id })

        // Removing it by its other ID removes the queued row and records both IDs.
        queueCoordinator.removeFromQueue("B")
        assertEquals(listOf("C"), playerStateFlow.value.queue.map { it.id })
        assertEquals(listOf("C"), queueRepository.getQueueEpisodeSnapshot().map { it.id })
        val metadata = queueRepository.getQueueMetadata()!!
        assertEquals(setOf("A", "B"), cx.aswin.boxlore.core.database.dao.QueueDao.parseRecentRemovedEpisodeIds(metadata.recentRemovedEpisodeIds).toSet())
        val rich = QueueRemovalIdentity.parseList(metadata.recentRemovedEpisodes)
        assertEquals(setOf("A", "B"), rich.map { it.id }.toSet())
        assertTrue(rich.all { it.show == SHOW && it.catalogKey == KEY })

        // Queueing it again under either ID clears its rich removal so other devices keep it.
        queueRepository.replaceQueue(listOf(episode("C"), episode("A")))
        assertTrue(QueueRemovalIdentity.parseList(queueRepository.getQueueMetadata()!!.recentRemovedEpisodes).isEmpty())
    }

    @Test
    fun theLikeButtonClearsALikeHeldByEitherIdAndSetsItOnBoth() = runTest(testDispatcher) {
        twoIds(ownerHistory = true, downloadOn = null)
        database.listeningHistoryDao().upsert(history("B", progress = 5L, liked = true, likedAt = 10L, playedAt = 5L))
        val historyStore = historyStore()
        assertTrue(historyStore.likedEpisodes.first().map { it.episodeId } == listOf("A"))

        historyStore.toggleLike(episode("A"), SHOW, "Show", null)
        assertFalse(database.listeningHistoryDao().getHistoryItem("A")!!.isLiked)
        assertFalse(database.listeningHistoryDao().getHistoryItem("B")!!.isLiked)
        assertTrue(historyStore.likedEpisodes.first().isEmpty())

        historyStore.toggleLike(episode("B"), SHOW, "Show", null)
        assertTrue(database.listeningHistoryDao().getHistoryItem("A")!!.isLiked)
        assertTrue(database.listeningHistoryDao().getHistoryItem("B")!!.isLiked)
        assertEquals(listOf("A"), historyStore.likedEpisodes.first().map { it.episodeId })
        assertEquals(listOf("A"), historyStore.getAllHistory().first().map { it.episodeId })
        // Library export still gets every stored row.
        assertEquals(setOf("A", "B"), historyStore.exportStoredHistory().map { it.episodeId }.toSet())
    }

    @Test
    fun markingPlayedAndUnplayedReachesEveryIdAndFiltersCountTheEpisodeOnce() = runTest(testDispatcher) {
        twoIds(ownerHistory = true, downloadOn = null)
        database.listeningHistoryDao().upsert(history("B", playedAt = 5L))
        val historyStore = historyStore()

        historyStore.toggleCompletion(episode("B"), SHOW, "Show", null)
        assertTrue(database.listeningHistoryDao().getHistoryItem("A")!!.isCompleted)
        assertTrue(database.listeningHistoryDao().getHistoryItem("B")!!.isCompleted)
        assertEquals(setOf("A", "B"), historyStore.completedEpisodeIds.first())

        historyStore.toggleCompletion(episode("A"), SHOW, "Show", null)
        assertFalse(database.listeningHistoryDao().getHistoryItem("B")!!.isCompleted)
        assertTrue(historyStore.completedEpisodeIds.first().isEmpty())
    }

    @Test
    fun theLikeButtonOnAnEpisodeStoredUnderOneIdWritesItsRowAsBefore() = runTest(testDispatcher) {
        val historyStore = historyStore()

        historyStore.toggleLike(episode("solo"), SHOW, "Show", null)
        val created = database.listeningHistoryDao().getHistoryItem("solo")!!
        assertTrue(created.isLiked)
        assertTrue(created.likedAt > 0L)
        assertEquals(0L, created.lastPlayedAt)

        historyStore.toggleLike(episode("solo"), SHOW, "Show", null)
        assertFalse(database.listeningHistoryDao().getHistoryItem("solo")!!.isLiked)
        assertNull(database.episodeIdentityDao().getGroupState(SHOW, KEY))
    }

    private fun historyStore(): PlaybackHistoryStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PlaybackHistoryStore(
            player = PlaybackHistoryPlayerDeps(context, TestScope(testDispatcher), playerStateFlow, playerStateFlow),
            data = PlaybackHistoryDataDeps(
                listeningHistoryDao = database.listeningHistoryDao(),
                listeningSessionDao = database.listeningSessionDao(),
                listeningRollupDao = database.listeningRollupDao(),
                listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
                podcastRepository = mock(PodcastRepository::class.java),
                rankingFeedbackRepository = RankingFeedbackRepository.create(null),
                listenerStore = store,
            ),
        )
    }

    private suspend fun twoIds(ownerHistory: Boolean, downloadOn: String?) {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L), alias("B", createdAt = 2L)))
        if (ownerHistory) database.listeningHistoryDao().upsert(history("A", progress = 30_000L, playedAt = 50L))
        if (downloadOn != null) database.downloadedEpisodeDao().insert(download(downloadOn))
    }

    private fun progressCoordinator() = PlaybackProgressCoordinator(
        mainDispatcher = testDispatcher,
        database = database,
        mediaSessionProvider = { null },
        isEffectiveEndLatched = { false },
        effectiveSkipEndingMs = { 0L },
        updateConsumedAudio = {},
        dispatchHeartbeatTelemetry = {},
        missingHistorySeedProvider = { null },
    )

    private fun snapshot(mediaId: String, positionMs: Long, sequence: Long = 1L) = PlaybackProgressSnapshot(
        sequence = sequence,
        episodeId = mediaId,
        positionMs = positionMs,
        durationMs = 1_800_000L,
        hasBeenPlayingFor10s = true,
        allowZeroPosition = false,
        episodeTitle = "Episode $mediaId",
        episodeImageUrl = null,
        episodeAudioUrl = "https://example.com/$mediaId.mp3",
        podcastName = "Show",
        enclosureType = null,
    )

    private fun MediaItem.cacheKey(): String? = localConfiguration?.customCacheKey

    private fun alias(id: String, owner: Boolean = false, createdAt: Long = 1L,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = KEY,
        recipeId = 4_503_599_627_370_500L,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    @Suppress("LongParameterList")
    private fun history(id: String, progress: Long = 0L, liked: Boolean = false, likedAt: Long = 0L, playedAt: Long = 1L,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_800_000L,
        isCompleted = false,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
    )

    private fun download(id: String) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Show",
        podcastImageUrl = null,
        durationMs = 1_800_000L,
        publishedDate = 1L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
    )

    private fun episode(id: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = SHOW,
        podcastTitle = "Show",
        duration = 1800,
        publishedDate = 1_700_000_000L,
    )

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
    }
}
