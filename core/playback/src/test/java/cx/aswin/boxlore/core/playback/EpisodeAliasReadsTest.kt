package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.SHOW
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.episode
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.history
import cx.aswin.boxlore.core.playback.EpisodeAliasFixtures.twoIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What playback reads for an episode stored under two IDs, owner "A" and another ID "B", when it
 * plays as "B" (docs/EPISODE_IDS.md §5): the start position, a restored session and the screens'
 * lookups all read the episode's projected state, and nothing writes a stale row under "B".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeAliasReadsTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val dao get() = database.listeningHistoryDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = EpisodeAliasFixtures.directDatabase(context)
        PlaybackActivationRequest.clear()
    }

    @After
    fun tearDown() {
        PlaybackActivationRequest.clear()
        database.close()
    }

    @Test
    fun anEpisodeStartingUnderAnotherIdResumesFromTheOwnersPositionAndTheNextTickKeepsIt() = runTest {
        twoIds(database)
        val ownerPosition = 600_000L
        dao.upsert(history("A", progress = ownerPosition, playedAt = System.currentTimeMillis() - 60_000L))

        // Queue auto-advance or Android Auto starts B with no explicit position and a skip-beginning setting.
        val player = player(positionMs = 0L)
        val controller = introController(skipBeginningMs = 30_000L)
        val item = MediaItem.Builder().setMediaId("B").build()
        `when`(player.currentMediaItem).thenReturn(item)
        controller.onMediaActivated(player, item)
        advanceUntilIdle()

        verify(player).seekTo(ownerPosition)
        verify(player, never()).seekTo(30_000L)

        // The next progress tick of the item playing as B lands on A and keeps the listener's place.
        progressCoordinator().saveProgressSnapshot(snapshot("B", positionMs = ownerPosition + 10_000L))
        assertEquals(ownerPosition + 10_000L, dao.getHistoryItem("A")!!.progressMs)
        assertNull(dao.getHistoryItem("B"))
    }

    @Test
    fun anEpisodeStoredUnderOneIdStartsFromItsOwnRowAsBefore() = runTest {
        dao.upsert(history("solo", progress = 50_000L, playedAt = System.currentTimeMillis() - 60_000L))

        val player = player(positionMs = 100L)
        val item = MediaItem.Builder().setMediaId("solo").build()
        `when`(player.currentMediaItem).thenReturn(item)
        introController().onMediaActivated(player, item)
        advanceUntilIdle()

        verify(player).seekTo(50_000L)
    }

    @Test
    fun aRestoreUnderAnIdThatFinishedTheEpisodeKeepsItCompletedAndAddsNoRow() = runTest {
        twoIds(database)
        // The episode played to the end as B, so A (the owner) holds the completion and B has no row.
        dao.upsert(history("A", completed = true, liked = true, likedAt = 5L, playedAt = 100L))

        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = "B",
                currentItem = null,
                listeningHistoryDao = dao,
                podcastRepository = mock(PodcastRepository::class.java),
                savedQueue = listOf(episode("B")),
                listenerStore = database.episodeListenerStore(),
            )!!

        assertEquals("B", restored.episode.id)
        assertEquals("B", restored.lastSession.episodeId)
        assertTrue(restored.lastSession.isCompleted)
        assertTrue(restored.lastSession.isLiked)
        assertNull(dao.getHistoryItem("B"))
        assertTrue(database.episodeListenerStore().projectedHistory("B")!!.isCompleted)
    }

    @Test
    fun aRestoreWithNoRowUnderAnyIdCreatesItUnderTheOwner() = runTest {
        twoIds(database)
        database.downloadedEpisodeDao().insert(EpisodeAliasFixtures.download("A"))

        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = "B",
                currentItem = null,
                listeningHistoryDao = dao,
                podcastRepository = mock(PodcastRepository::class.java),
                savedQueue = listOf(episode("B")),
                listenerStore = database.episodeListenerStore(),
            )!!

        assertEquals("B", restored.episode.id)
        assertFalse(restored.lastSession.isCompleted)
        assertEquals("A", dao.getRecentHistoryList(10).single().episodeId)
    }

    @Test
    fun aSeekSavedFromAPageShowingAnotherIdKeepsTheOwnersLike() = runTest {
        twoIds(database)
        dao.upsert(history("A", progress = 10L, liked = true, likedAt = 50L, playedAt = 40L))
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, this)

        historyStore.savePlaybackState(progressInput("B", positionMs = 90_000L, isLiked = false))

        val owner = dao.getHistoryItem("A")!!
        assertEquals(90_000L, owner.progressMs)
        assertTrue(owner.isLiked)
        assertEquals(50L, owner.likedAt)
        assertNull(dao.getHistoryItem("B"))

        // An episode stored under one ID writes the page's values as before.
        historyStore.savePlaybackState(progressInput("solo", positionMs = 7L, isLiked = true))
        assertTrue(dao.getHistoryItem("solo")!!.isLiked)
    }

    @Test
    fun screensLookingAnEpisodeUpByAnotherIdSeeItsLikeAndProgress() = runTest {
        twoIds(database)
        dao.upsertAll(listOf(history("A", progress = 600_000L, liked = true, likedAt = 9L, playedAt = 30L), history("solo", progress = 5L, playedAt = 20L)))
        val historyStore = EpisodeAliasFixtures.historyStore(context, database, this)

        assertEquals(setOf("A", "B"), historyStore.likedEpisodeIds.first())
        val byId = historyStore.historyForEpisodeLookup.first().associateBy { it.episodeId }
        assertEquals(setOf("A", "B", "solo"), byId.keys)
        assertEquals(600_000L, byId.getValue("B").progressMs)
        // Lists still count the episode once.
        assertEquals(listOf("A", "solo"), historyStore.getAllHistory().first().map { it.episodeId })
    }

    @Test
    fun theAndroidAutoLastSessionIsTheEpisodesProjectedState() = runTest {
        twoIds(database)
        dao.upsertAll(listOf(history("B", progress = 70L, playedAt = 90L), history("A", completed = true, playedAt = 95L)))

        // B's unfinished row belongs to an episode finished later under A, so nothing is left to resume.
        assertNull(database.episodeListenerStore().lastPlayedSession(unfinishedOnly = true))
        val latest = database.episodeListenerStore().lastPlayedSession(unfinishedOnly = false)!!
        assertEquals("A", latest.episodeId)
        assertTrue(latest.isCompleted)
    }

    private fun kotlinx.coroutines.test.TestScope.introController(skipBeginningMs: Long = 0L) = PlaybackIntroOutroController(
        scope = this,
        database = database,
        globalSkipBeginningMs = { skipBeginningMs },
        globalSkipEndingMs = { 0L },
        staleRestartEnabled = { true },
        lifecycleEpisodeId = { it?.mediaId },
        findPodcastIdForEpisode = { null },
        onActiveDurationResolved = { _, _ -> },
        onNaturalCompletion = { _, _ -> null },
        onClearEndOfEpisodeSleep = {},
    )

    private fun player(positionMs: Long): Player {
        val player = mock(Player::class.java)
        `when`(player.currentPosition).thenReturn(positionMs)
        `when`(player.duration).thenReturn(1_800_000L)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        return player
    }

    private fun kotlinx.coroutines.test.TestScope.progressCoordinator() = PlaybackProgressCoordinator(
        mainDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler),
        database = database,
        mediaSessionProvider = { null },
        isEffectiveEndLatched = { false },
        effectiveSkipEndingMs = { 0L },
        updateConsumedAudio = {},
        dispatchHeartbeatTelemetry = {},
        missingHistorySeedProvider = { null },
    )

    private fun snapshot(mediaId: String, positionMs: Long) = PlaybackProgressSnapshot(
        sequence = 1L,
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

    private fun progressInput(id: String, positionMs: Long, isLiked: Boolean) = ListeningHistoryUpsertLogic.ProgressSaveInput(
        podcastId = SHOW,
        episodeId = id,
        positionMs = positionMs,
        durationMs = 1_800_000L,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        isCompleted = false,
        isLiked = isLiked,
        lastPlayedAt = 1_000L,
        enclosureType = null,
        episodeDescription = null,
    )
}
