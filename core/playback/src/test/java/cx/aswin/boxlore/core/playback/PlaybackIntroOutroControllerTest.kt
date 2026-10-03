package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackIntroOutroControllerTest {
    private fun history(lastPlayedAt: Long = System.currentTimeMillis() - 60_000L) = ListeningHistoryEntity(
        episodeId = "123", podcastId = "show", episodeTitle = "Halfway episode",
        episodeImageUrl = null, podcastImageUrl = null, episodeAudioUrl = "https://example.com/audio.mp3",
        podcastName = "Show", progressMs = 50_000L, durationMs = 100_000L,
        isCompleted = false, isLiked = false, lastPlayedAt = lastPlayedAt,
    )

    @Before fun clearBefore() {
        PlaybackActivationRequest.clear()
        SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    @After fun clearAfter() {
        PlaybackActivationRequest.clear()
        SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    private data class CompletionCallbacks(
        val onCompletion: (String, Long) -> Job? = { _, _ -> null },
        val onExhausted: (Player) -> Boolean = { false },
    )

    private suspend fun TestScope.fixture(
        positionMs: Long = 100L,
        saved: ListeningHistoryEntity = history(),
        trimMs: Long = 0L,
        staleRestart: Boolean = true,
        entryPoint: String? = null,
        resolvePodcast: suspend () -> String? = { null },
        callbacks: CompletionCallbacks = CompletionCallbacks(),
    ): Pair<PlaybackIntroOutroController, Player> {
        val dao = mock(ListeningHistoryDao::class.java)
        val db = mock(BoxLoreDatabase::class.java)
        `when`(db.listeningHistoryDao()).thenReturn(dao)
        `when`(dao.getHistoryItem("123")).thenReturn(saved)
        val player = mock(Player::class.java)
        `when`(player.currentPosition).thenReturn(positionMs)
        `when`(player.duration).thenReturn(100_000L)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        val controller = PlaybackIntroOutroController(
            scope = this, database = db,
            globalSkipBeginningMs = { trimMs }, globalSkipEndingMs = { 0L },
            staleRestartEnabled = { staleRestart }, lifecycleEpisodeId = { it?.mediaId },
            findPodcastIdForEpisode = { resolvePodcast() }, onActiveDurationResolved = { _, _ -> },
            onNaturalCompletion = callbacks.onCompletion,
            onClearEndOfEpisodeSleep = { SleepTimerHolder.sleepAtEndOfEpisode = false },
            onContextQueueExhausted = callbacks.onExhausted,
        )
        val extras = android.os.Bundle().apply { entryPoint?.let { putString("entry_point", it) } }
        val item = MediaItem.Builder().setMediaId("123").setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder().setExtras(extras).build(),
        ).build()
        `when`(player.currentMediaItem).thenReturn(item)
        controller.onMediaActivated(player, item)
        return controller to player
    }
    private fun checkActivation(positionMs: Long) = runTest {
        val (_, player) = fixture(positionMs)
        advanceUntilIdle()
        verify(player).seekTo(50_000L)
    }

    @Test fun `fresh unfinished episode resumes when auto transition reports exactly zero`() = checkActivation(0L)

    @Test fun `automatic activation at one millisecond restores saved progress`() = checkActivation(1L)

    @Test fun `automatic activation at one hundred milliseconds restores saved progress`() = checkActivation(100L)

    @Test fun `implicit seven day boundary is correct`() {
        val now = 1_700_000_000_000L
        fun resolve(age: Long) = PlaybackSkipPolicy.resolveInitialPosition(
            explicitPositionMs = null,
            savedProgressMs = 50_000L,
            isCompleted = false,
            skipBeginningMs = 0L,
            lastPlayedAtMs = now - age,
            staleRestartEnabled = true,
            nowMs = now,
        ).positionMs
        assertEquals(50_000L, resolve(60_000L))
        assertEquals(50_000L, resolve(PlaybackSkipPolicy.STALE_RESUME_MS))
        assertEquals(0L, resolve(PlaybackSkipPolicy.STALE_RESUME_MS + 1L))
    }

    @Test fun `intentional zero never resumes recent saved progress`() = runTest {
        PlaybackActivationRequest.set("123", 0L)
        val (_, player) = fixture(positionMs = 0L)
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `implicit zero restart retains intro trim without resurrecting saved progress`() = runTest {
        PlaybackActivationRequest.set("123", 0L, applyIntroTrim = true)
        val (_, player) = fixture(positionMs = 0L, trimMs = 10_000L)
        advanceUntilIdle()
        verify(player).seekTo(10_000L)
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `chosen positive seek is not replaced with saved progress`() = runTest {
        PlaybackActivationRequest.set("123", 35_000L)
        val (_, player) = fixture(positionMs = 35_000L)
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `stale implicit episode starts over`() = runTest {
        val (_, player) = fixture(saved = history(System.currentTimeMillis() - PlaybackSkipPolicy.STALE_RESUME_MS - 60_000L))
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `history explicit resume bypasses stale cutoff`() = runTest {
        val (_, player) = fixture(
            saved = history(System.currentTimeMillis() - PlaybackSkipPolicy.STALE_RESUME_MS - 60_000L),
            entryPoint = "library_history",
        )
        advanceUntilIdle()
        verify(player).seekTo(50_000L)
    }

    @Test fun `disabled stale restart resumes old progress`() = runTest {
        val (_, player) = fixture(
            saved = history(System.currentTimeMillis() - PlaybackSkipPolicy.STALE_RESUME_MS - 60_000L),
            staleRestart = false,
        )
        advanceUntilIdle()
        verify(player).seekTo(50_000L)
    }

    @Test fun `completed episode does not resume completion position`() = runTest {
        val (_, player) = fixture(saved = history().copy(isCompleted = true))
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `saved seek waits for ready and a known duration`() = runTest {
        val (controller, player) = fixture()
        `when`(player.duration).thenReturn(androidx.media3.common.C.TIME_UNSET)
        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
        `when`(player.duration).thenReturn(100_000L)
        `when`(player.playbackState).thenReturn(Player.STATE_READY)
        controller.onReadyOrPlaying(player)
        verify(player).seekTo(50_000L)
    }

    @Test fun `user seek while loading cancels the pending resume`() = runTest {
        val (controller, player) = fixture()
        controller.onSeekDiscontinuity(20_000L, 100_000L, "user")
        advanceUntilIdle()
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `listen time update waits for the old resume policy to resolve`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val (controller, player) = fixture(
            saved = history(System.currentTimeMillis() - PlaybackSkipPolicy.STALE_RESUME_MS - 60_000L).copy(podcastId = ""),
            resolvePodcast = {
                ready.await()
                null
            },
        )
        var canRefreshListenTime = false
        launch { canRefreshListenTime = controller.awaitActivationConfiguration("123") }
        runCurrent()
        assertFalse(canRefreshListenTime)
        ready.complete(Unit)
        advanceUntilIdle()
        assertTrue(canRefreshListenTime)
        verify(player, never()).seekTo(50_000L)
    }

    @Test fun `replaced activation cannot refresh the previous episode listen time`() = runTest {
        val ready = CompletableDeferred<Unit>()
        val (controller, _) = fixture(saved = history().copy(podcastId = ""), resolvePodcast = {
            ready.await()
            null
        })
        var canRefreshListenTime = true
        launch { canRefreshListenTime = controller.awaitActivationConfiguration("123") }
        runCurrent()
        controller.reset(MediaItem.Builder().setMediaId("456").build(), 0L)
        ready.complete(Unit)
        advanceUntilIdle()
        assertFalse(canRefreshListenTime)
    }

    @Test fun `natural context end claims completion once and delegates terminal refill`() = runTest {
        var completions = 0
        var refills = 0
        val (controller, player) = fixture(
            callbacks = CompletionCallbacks(
            onCompletion = { _, _ ->
                completions++
                null
            },
            onExhausted = {
                refills++
                true
            },
        )
        )
        advanceUntilIdle()
        controller.onNaturalStateEnded(player)
        controller.onNaturalStateEnded(player)
        assertEquals(1, completions)
        assertEquals(2, refills)
        verify(player, never()).stop()
    }

    @Test fun `end of episode sleep prevents context fallback`() = runTest {
        var refills = 0
        val (controller, player) = fixture(
            callbacks = CompletionCallbacks(onExhausted = {
            refills++
            true
        })
        )
        advanceUntilIdle()
        SleepTimerHolder.sleepAtEndOfEpisode = true
        controller.onNaturalStateEnded(player)
        assertEquals(0, refills)
        assertFalse(SleepTimerHolder.sleepAtEndOfEpisode)
        verify(player).pause()
    }

    @Test fun `positive transport seek while loading supersedes an implicit zero trim`() = runTest {
        PlaybackActivationRequest.set("123", 0L, applyIntroTrim = true)
        val (controller, player) = fixture(positionMs = 0L, trimMs = 10_000L)
        controller.onSeekDiscontinuity(35_000L, 100_000L, "transition")
        advanceUntilIdle()
        verify(player, never()).seekTo(10_000L)
        verify(player, never()).seekTo(50_000L)
    }
}
