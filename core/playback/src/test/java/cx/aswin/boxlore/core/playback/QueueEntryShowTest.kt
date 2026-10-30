package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.playback.service.auto.AutoMediaItemFactory
import cx.aswin.boxlore.core.playback.service.refillMediaItem
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An episode that joins the queue without its show attached plays under its own show, never the
 * show of the episode before it (a Conspiracy Theories episode handing over to a queued
 * The Vergecast episode showed "Conspiracy Theories", also after a restart).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueEntryShowTest {
    private val conspiracy = Podcast("1001", "Conspiracy Theories", "Spotify Studios", "https://example.com/ct.jpg")
    private val vergecast = Podcast("2002", "The Vergecast", "The Verge", "https://example.com/verge.jpg")
    private val playing = Episode(
        id = "11",
        title = "The Roswell Incident",
        description = "",
        audioUrl = "https://example.com/11.mp3",
        podcastId = conspiracy.id,
        podcastTitle = conspiracy.title,
        duration = 1800,
    )

    /** A show-page episode as `EpisodeItem.toEpisode()` maps it without feed fields: no show attached. */
    private val vergeLatest = showless("22", "The latest Vergecast")

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val playerStateFlow = MutableStateFlow(PlayerState())
    private val items = mutableListOf<MediaItem>()
    private var index = 0
    private var storedLookup: suspend (String) -> Podcast? = { null }
    private val podcastRepository = mock(PodcastRepository::class.java)
    private lateinit var database: BoxLoreDatabase
    private lateinit var queueRepository: QueueRepository
    private lateinit var coordinator: PlaybackQueueCoordinator
    private lateinit var bridge: PlaybackMediaControllerBridge

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = EpisodeAliasFixtures.directDatabase(context)
        queueRepository = QueueRepository(database, podcastRepository)
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, scope, playerStateFlow)
        val mediaHandle = PlaybackMediaControllerHandle().apply { controller = fakeController() }
        coordinator =
            PlaybackQueueCoordinator(
                scope = scope,
                playerStateFlow = playerStateFlow,
                mediaHandle = mediaHandle,
                queueRepository = queueRepository,
                rankingFeedbackRepository = RankingFeedbackRepository.create(null),
                queueSkipMemory = QueueSkipMemory.fromContext(context),
                prefs = context.getSharedPreferences("queue_entry_show_prefs", Context.MODE_PRIVATE),
                playerDismissedKey = "player_dismissed",
                queueMaxSize = 50,
                checkSavedProgress = { _, _, _, _ -> Pair(0L, false) },
                onPlaybackStarted = {},
                storePendingEntryPoint = {},
                ensureCurrentHistoryRow = { historyStore.ensureCurrentHistoryRow() },
                stopProgressTicker = {},
            )
        bridge =
            PlaybackMediaControllerBridge(
                context = context,
                scope = scope,
                playerStateFlow = playerStateFlow,
                mediaHandle = mediaHandle,
                queueRepository = queueRepository,
                currentSkipBehavior = { "skip" },
                onPlaybackStarted = {},
                startProgressTicker = {},
                stopProgressTicker = {},
                cancelSleepTimer = {},
                syncQueueToDb = { coordinator.syncQueueToDb() },
                reconcileQueueWithController = {},
                markEpisodeAsCompleted = { _, _ -> },
                findPodcastIdForEpisode = { null },
                hasActiveCastSession = { false },
                findStoredPodcast = { storedLookup(it) },
            )
        playerStateFlow.value = PlayerState(currentEpisode = playing, currentPodcast = conspiracy, queue = listOf(playing))
        items += MediaItem.Builder().setMediaId(playing.id).setMediaMetadata(MediaMetadata.Builder().setTitle(playing.title).build()).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun playNextFromAnotherShowKeepsItsShowAfterAdvancing() = runTest(dispatcher) {
        coordinator.addToQueueNext(vergeLatest, vergecast)
        advanceTo(1)

        assertVergecastIsCurrent()
    }

    @Test
    fun addToQueueFromAnotherShowKeepsItsShowAfterAdvancing() = runTest(dispatcher) {
        coordinator.addToQueue(vergeLatest, vergecast)
        advanceTo(1)

        assertVergecastIsCurrent()
    }

    @Test
    fun aShowlessListKeepsItsShowAfterAnEpisodeFromAnotherShow() = runTest(dispatcher) {
        // Play All on a show page (or a show's downloads) whose episodes carry no show fields.
        val first = showless("31", "Area 51")
        val second = showless("32", "The Bermuda Triangle")
        coordinator.playQueue(listOf(first, second), conspiracy, 0)
        coordinator.addToQueueNext(vergeLatest, vergecast)
        advanceTo(1)
        assertEquals(vergecast.title, playerStateFlow.value.currentPodcast?.title)

        advanceTo(2)

        val state = playerStateFlow.value
        assertEquals(second.id, state.currentEpisode?.id)
        assertEquals(conspiracy.title, state.currentPodcast?.title)
        assertEquals(conspiracy.id, state.currentPodcast?.id)
    }

    @Test
    fun refilledEpisodeRebuiltFromItsMediaItemKeepsItsShow() = runTest(dispatcher) {
        // The service appends a Smart Queue pick; Room has no row for it yet, so the UI rebuilds the
        // entry from the media item alone.
        items += refillMediaItem(vergePick())
        coordinator.reconcileQueueSnapshot(items.map { it.mediaId })
        advanceTo(1)

        assertVergecastIsCurrent()
    }

    @Test
    fun refilledEpisodeWithoutAnyQueueEntryKeepsItsShowNotItsHost() = runTest(dispatcher) {
        // The pick becomes current before the UI saw it: neither memory nor Room holds an entry.
        items += refillMediaItem(vergePick())
        advanceTo(1)

        assertVergecastIsCurrent()
    }

    @Test
    fun androidAutoItemRebuiltFromThePlayerTakesItsShowNotItsSubtitle() = runTest(dispatcher) {
        // Android Auto subtitles add the duration ("The Vergecast · 1h 0m").
        items += AutoMediaItemFactory.fromEpisode(vergeLatest.copy(podcastTitle = vergecast.title), source = "test", artworkUri = null)
        coordinator.reconcileQueueSnapshot(items.map { it.mediaId.removePrefix("episode:") })
        advanceTo(1)

        assertEquals(vergecast.title, playerStateFlow.value.currentPodcast?.title)
        assertEquals(vergecast.title, playerStateFlow.value.currentEpisode?.podcastTitle)
    }

    @Test
    fun aMixedDownloadsQueuePlaysEachEpisodeUnderItsOwnShow() = runTest(dispatcher) {
        // Downloads > Play all starts every download under one "All Downloads" placeholder.
        val allDownloads = Podcast("downloads_all", "All Downloads", "", "")
        val first = playing.copy(id = "41")
        val second = vergeLatest.copy(podcastId = vergecast.id, podcastTitle = vergecast.title)
        coordinator.playQueue(listOf(first, second), allDownloads, 0)
        advanceUntilIdle()

        assertEquals(conspiracy.title, playerStateFlow.value.currentPodcast?.title)
        // The session saved for the first episode names its own show, never the placeholder.
        val saved = database.listeningHistoryDao().getHistoryItem(first.id)!!
        assertEquals(conspiracy.id, saved.podcastId)
        assertEquals(conspiracy.title, saved.podcastName)

        advanceTo(1)

        assertEquals(vergecast.title, playerStateFlow.value.currentPodcast?.title)
        assertEquals(vergecast.id, playerStateFlow.value.currentPodcast?.id)
    }

    @Test
    fun aTransitionTheQueueMovedPastNeverOverwritesTheNewerOne() = runTest(dispatcher) {
        val next = showless("31", "Area 51")
        coordinator.playQueue(listOf(playing, next), conspiracy, 0)
        coordinator.addToQueueNext(vergeLatest, vergecast)
        val lookup = CompletableDeferred<Podcast?>()
        storedLookup = { id -> if (id == vergecast.id) lookup.await() else null }

        advanceTo(1) // waits on the Vergecast lookup
        advanceTo(2) // the listener skips again before it finishes
        lookup.complete(vergecast)
        advanceUntilIdle()

        val state = playerStateFlow.value
        assertEquals(next.id, state.currentEpisode?.id)
        assertEquals(conspiracy.title, state.currentPodcast?.title)
        assertEquals(listOf(next.id), state.queue.map { it.id })
    }

    @Test
    fun restoreRightAfterAdvancingReadsTheShowFromTheSavedQueue() = runTest(dispatcher) {
        coordinator.addToQueueNext(vergeLatest, vergecast)
        advanceTo(1)

        // A restart before any history row exists rebuilds the session from the saved queue.
        val restored = restoreAfterRestart()
        assertEquals(vergecast.title, restored.podcast.title)
        assertEquals(vergecast.id, restored.podcast.id)
    }

    @Test
    fun restoreAfterRestartShowsTheEpisodesOwnShow() = runTest(dispatcher) {
        coordinator.addToQueueNext(vergeLatest, vergecast)
        advanceTo(1)
        // The listener starts it again from the queue; the player hands playQueue its current show
        // (PlaybackTransportHelper.reinitializePlaybackIfEmpty does the same), which records the session.
        val state = playerStateFlow.value
        coordinator.playQueue(state.queue, state.currentPodcast!!, 0)
        advanceUntilIdle()

        val restored = restoreAfterRestart()
        assertEquals(vergecast.title, restored.podcast.title)
        assertEquals(vergecast.id, restored.podcast.id)
        assertEquals(vergecast.title, restored.episode.podcastTitle)
    }

    private fun showless(id: String, title: String) =
        Episode(id = id, title = title, description = "", audioUrl = "https://example.com/$id.mp3", duration = 3600)

    private fun vergePick() =
        QueueEntry(EpisodeItem(id = 22L, title = vergeLatest.title, enclosureUrl = vergeLatest.audioUrl), vergecast, SmartQueueEngine.SOURCE_SUBSCRIPTION)

    /** After a restart nothing is in memory: the session and queue come back from Room. */
    private suspend fun restoreAfterRestart(): RestoredSessionData = PlaybackSessionRestoreHelper.resolveRestoredSession(
        targetEpisodeId = vergeLatest.id,
        currentItem = null,
        listeningHistoryDao = database.listeningHistoryDao(),
        podcastRepository = podcastRepository,
        savedQueue = queueRepository.getQueueEpisodeSnapshot(),
        listenerStore = database.episodeListenerStore(),
    )!!

    private fun TestScope.advanceTo(position: Int) {
        index = position
        bridge.onMediaItemTransition(items[position], Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()
    }

    private suspend fun assertVergecastIsCurrent() {
        val state = playerStateFlow.value
        assertEquals(vergeLatest.id, state.currentEpisode?.id)
        assertEquals(vergecast.title, state.currentPodcast?.title)
        assertEquals(vergecast.id, state.currentPodcast?.id)
        assertEquals(vergecast.id, state.currentEpisode?.podcastId)
        // The saved queue keeps the show for a restart.
        val row = queueRepository.getQueueItemByEpisodeId(vergeLatest.id)!!
        assertEquals(vergecast.id, row.podcastId)
        assertEquals(vergecast.title, row.podcastTitle)
    }

    private fun fakeController(): MediaController = mock(MediaController::class.java).also { controller ->
        `when`(controller.mediaItemCount).thenAnswer { items.size }
        `when`(controller.currentMediaItemIndex).thenAnswer { index }
        `when`(controller.currentMediaItem).thenAnswer { items.getOrNull(index) }
        `when`(controller.getMediaItemAt(anyInt())).thenAnswer { items[it.getArgument(0)] }
        `when`(controller.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        doAnswer {
            items += it.getArgument<MediaItem>(0)
            null
        }.`when`(controller).addMediaItem(any(MediaItem::class.java))
        doAnswer {
            items.add(it.getArgument(0), it.getArgument(1))
            null
        }.`when`(controller).addMediaItem(anyInt(), any(MediaItem::class.java))
        doAnswer {
            items.clear()
            items += it.getArgument<List<MediaItem>>(0)
            index = it.getArgument(1)
            null
        }.`when`(controller).setMediaItems(anyList(), anyInt(), anyLong())
    }
}
