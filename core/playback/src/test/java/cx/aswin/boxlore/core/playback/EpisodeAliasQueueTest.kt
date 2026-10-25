package cx.aswin.boxlore.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.KEY
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.RECIPE
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.SHOW
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.download
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.episode
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.history
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.twoIds
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryHost
import cx.aswin.boxlore.core.playback.service.auto.AutoMediaResolver
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The queue and the media items playback builds for an episode stored under two IDs, owner "A"
 * and "B" (docs/EPISODE_IDS.md §5): removals stay in step between the player and Room, the plain
 * and rich removal lists stay in step, and every path that builds a media item through the
 * production lookups plays the completed download of whichever ID holds it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeAliasQueueTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var queueRepository: QueueRepository
    private lateinit var controller: MediaController
    private lateinit var mediaHandle: PlaybackMediaControllerHandle
    private lateinit var queueCoordinator: PlaybackQueueCoordinator
    private val testDispatcher = UnconfinedTestDispatcher()
    private val playerStateFlow = MutableStateFlow(PlayerState())
    private val podcast = Podcast(SHOW, "Show", "Host", "")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Queries run on the calling thread, so a coroutine a helper launches finishes before the test checks it.
        database = EpisodeAliasFixtures.directDatabase(context)
        val store = database.episodeListenerStore()
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
        controller = mock(MediaController::class.java)
        `when`(controller.mediaItemCount).thenReturn(0)
        `when`(controller.playbackParameters).thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
        mediaHandle = PlaybackMediaControllerHandle().also { it.controller = controller }
        queueCoordinator = PlaybackQueueCoordinator(
            scope = TestScope(testDispatcher),
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            queueRepository = queueRepository,
            rankingFeedbackRepository = RankingFeedbackRepository.create(null),
            queueSkipMemory = QueueSkipMemory.fromContext(context),
            prefs = context.getSharedPreferences("alias_queue_test_prefs", Context.MODE_PRIVATE),
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
    fun removingAnEpisodeKeepsTheIdThatIsPlayingInRoomAsInThePlayer() = runTest(testDispatcher) {
        twoIds(database)
        queueRepository.replaceQueue(listOf(episode("A"), episode("B"), episode("C")))
        playerStateFlow.value = PlayerState(currentEpisode = episode("A"), queue = listOf(episode("A"), episode("B"), episode("C")))

        queueCoordinator.removeFromQueue("B")

        assertEquals(listOf("A", "C"), playerStateFlow.value.queue.map { it.id })
        assertEquals(listOf("A", "C"), queueRepository.getQueueEpisodeSnapshot().map { it.id })
        assertEquals(listOf("B"), plainRemovals())
    }

    @Test
    fun onlyQueuedIdsAreRemovedAndRecordedAndAnEpisodeStoredUnderOneIdAsBefore() = runTest(testDispatcher) {
        twoIds(database)
        queueRepository.replaceQueue(listOf(episode("A"), episode("solo")))

        queueRepository.removeFromQueue("A")
        queueRepository.removeFromQueue("solo")

        assertTrue(queueRepository.getQueueEpisodeSnapshot().isEmpty())
        // B was never queued here, so it isn't added to the list older builds read.
        assertEquals(listOf("A", "solo"), plainRemovals())
        assertEquals(
            listOf(QueueRemovalIdentity("A", RECIPE, SHOW, KEY), QueueRemovalIdentity("solo")),
            QueueRemovalIdentity.parseList(queueRepository.getQueueMetadata()!!.recentRemovedEpisodes),
        )
    }

    @Test
    fun queueingAnEpisodeAgainUnderOneIdClearsTheRemovalsOfEveryId() = runTest(testDispatcher) {
        twoIds(database)
        queueRepository.replaceQueue(listOf(episode("A"), episode("B"), episode("C")))
        queueRepository.removeFromQueue("A")
        assertEquals(setOf("A", "B"), plainRemovals().toSet())

        // Undo (or adding it again) restores the episode under B only.
        queueRepository.replaceQueue(listOf(episode("C"), episode("B")))

        assertTrue(plainRemovals().isEmpty())
        assertTrue(QueueRemovalIdentity.parseList(queueRepository.getQueueMetadata()!!.recentRemovedEpisodes).isEmpty())
    }

    @Test
    fun aPulledQueueStateKeepsTheRichRemovalsInStepWithThePlainList() = runTest(testDispatcher) {
        twoIds(database)
        val rich = QueueRemovalIdentity.serializeList(listOf(QueueRemovalIdentity("A", RECIPE, SHOW, KEY), QueueRemovalIdentity("-7"), QueueRemovalIdentity("-8")))
        val plain = QueueDao.serializeRecentRemovedEpisodeIds(listOf("A", "-7", "-8"))
        queueRepository.applyRemoteQueueState(emptyList(), QueueMetadataEntity(id = 1, recentRemovedEpisodeIds = plain, recentRemovedEpisodes = rich))
        assertEquals(rich, queueRepository.getQueueMetadata()!!.recentRemovedEpisodes)

        // Another device queued the episode again under B and dropped -8 from the plain list.
        queueRepository.applyRemoteQueueState(
            items = listOf(QueueItem(episodeId = "B", title = "Episode B", podcastId = SHOW, podcastTitle = "Show", imageUrl = null, audioUrl = "https://example.com/B.mp3", duration = 1800, pubDate = 1L, description = null, position = 0)),
            metadata = queueRepository.getQueueMetadata()!!.copy(recentRemovedEpisodeIds = QueueDao.serializeRecentRemovedEpisodeIds(listOf("A", "-7"))),
        )

        assertEquals(listOf(QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(queueRepository.getQueueMetadata()!!.recentRemovedEpisodes))
    }

    @Test
    fun everyPathThatBuildsAMediaItemPlaysTheDownloadOfTheIdThatHoldsIt() = runTest(testDispatcher) {
        twoIds(database)
        database.listeningHistoryDao().upsert(history("A", progress = 30_000L, playedAt = 50L))
        database.downloadedEpisodeDao().insert(download("B"))
        val downloadKey = EpisodeMediaCacheKey.of("B", episode("B").audioUrl)
        val items = itemsCaptor()

        queueCoordinator.playQueue(listOf(episode("A")), podcast)
        verify(controller).setMediaItems(items.capture(), anyInt(), anyLong())
        assertEquals("A", items.value.single().mediaId)
        assertEquals(downloadKey, items.value.single().localConfiguration?.customCacheKey)

        playerStateFlow.value = PlayerState()
        queueCoordinator.addToQueue(episode("A"), podcast)
        val added = ArgumentCaptor.forClass(MediaItem::class.java)
        verify(controller).addMediaItem(added.capture())
        assertEquals(downloadKey, added.value.localConfiguration?.customCacheKey)

        playerStateFlow.value = PlayerState()
        queueCoordinator.addToQueueNext(episode("A"), podcast)
        val next = ArgumentCaptor.forClass(MediaItem::class.java)
        verify(controller).addMediaItem(anyInt(), next.capture())
        assertEquals(downloadKey, next.value.localConfiguration?.customCacheKey)

        playerStateFlow.value = PlayerState()
        queueCoordinator.addEpisodesAfterCurrent(listOf(episode("A")), podcast)
        val after = itemsCaptor()
        verify(controller).addMediaItems(anyInt(), after.capture())
        assertEquals(downloadKey, after.value.single().localConfiguration?.customCacheKey)
    }

    @Test
    fun resumingAfterTheServiceStoppedPlaysTheDownloadOfTheIdThatHoldsIt() = runTest(testDispatcher) {
        twoIds(database)
        database.downloadedEpisodeDao().insert(download("B"))
        val store = database.episodeListenerStore()
        playerStateFlow.value = PlayerState(currentEpisode = episode("A"), currentPodcast = podcast)
        val transport = PlaybackTransportHelper(
            scope = this,
            playerStateFlow = playerStateFlow,
            mediaHandle = mediaHandle,
            storePendingEntryPoint = {},
            resolveInitialSeekMs = { _, _ -> 0L },
            resolvePersistedResumePositionMs = { _, _ -> null },
            playQueue = { _, _, _, _, _, _ -> error("Resume must reload the saved item") },
            downloadHolders = { store.downloadHolders(it) },
        )

        transport.resume()

        val item = ArgumentCaptor.forClass(MediaItem::class.java)
        verify(controller).setMediaItem(item.capture(), anyLong())
        assertEquals("A", item.value.mediaId)
        assertEquals(EpisodeMediaCacheKey.of("B", episode("A").audioUrl), item.value.localConfiguration?.customCacheKey)
    }

    @Test
    fun androidAutoPlaysTheDownloadOfTheIdThatHoldsIt() = runTest(testDispatcher) {
        twoIds(database)
        database.downloadedEpisodeDao().insert(download("B"))
        val host = mock(AutoBrowseLibraryHost::class.java)
        `when`(host.database).thenReturn(database)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.queueRepository).thenReturn(queueRepository)
        val audio = "https://example.com/A.mp3"
        val item = MediaItem.Builder().setMediaId("episode:A").setUri(Uri.parse(audio)).build()

        val resolved = AutoMediaResolver(host).resolveMediaItem(item)

        assertEquals("episode:A", resolved.mediaId)
        assertEquals(EpisodeMediaCacheKey.of("B", audio), resolved.localConfiguration?.customCacheKey)
        // An episode stored under one ID keeps its own key.
        val solo = AutoMediaResolver(host).resolveMediaItem(MediaItem.Builder().setMediaId("solo").setUri(Uri.parse(audio)).build())
        assertEquals(EpisodeMediaCacheKey.of("solo", audio), solo.localConfiguration?.customCacheKey)
    }

    private suspend fun plainRemovals(): List<String> =
        QueueDao.parseRecentRemovedEpisodeIds(queueRepository.getQueueMetadata()?.recentRemovedEpisodeIds)

    @Suppress("UNCHECKED_CAST")
    private fun itemsCaptor(): ArgumentCaptor<List<MediaItem>> = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MediaItem>>
}
