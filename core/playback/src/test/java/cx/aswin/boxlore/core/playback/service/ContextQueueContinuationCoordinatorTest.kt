package cx.aswin.boxlore.core.playback.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.SleepTimerHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
class ContextQueueContinuationCoordinatorTest {
    @Test fun `receiver finished idle completes context but pause stop and errors do not`() {
        assertTrue(shouldCompleteRemoteContext(Player.STATE_IDLE, true, false, true))
        assertFalse(shouldCompleteRemoteContext(Player.STATE_READY, true, false, true))
        assertFalse(shouldCompleteRemoteContext(Player.STATE_IDLE, true, false, false))
        assertFalse(shouldCompleteRemoteContext(Player.STATE_IDLE, true, true, true))
        assertFalse(shouldCompleteRemoteContext(Player.STATE_IDLE, false, false, true))
    }

    @Test fun `asynchronous Cast insertion waits for the next item before advancing`() = runTest {
        val player = player()
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, {}, { _, _ -> true }, { it.stop() })
        coordinator.onExhausted(player)
        runCurrent()
        verify(player, never()).play()
        advanceTimeBy(200L)
        `when`(player.hasNextMediaItem()).thenReturn(true)
        advanceUntilIdle()
        verify(player).seekToNextMediaItem()
        verify(player).play()
        verify(player, never()).stop()
    }

    @Before fun resetSleep() {
        SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    @After fun clearSleep() {
        SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    private fun player(context: Boolean = true): Player = mock(Player::class.java).also { player ->
        val extras = Bundle().apply {
            if (context) putString("source_entry_point", PlaybackQueueContext.NEW_EPISODES)
        }
        val item = MediaItem.Builder().setMediaId("123")
            .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()
        `when`(player.currentMediaItem).thenReturn(item)
        `when`(player.playWhenReady).thenReturn(true)
        `when`(player.hasNextMediaItem()).thenReturn(false)
    }

    @Test fun `completion persists before one refill and terminal playback advances`() = runTest {
        val player = player()
        val completed = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, { completed.await() }, { _, _ ->
            calls++
            `when`(player.hasNextMediaItem()).thenReturn(true)
            true
        }, { it.stop() })
        assertTrue(coordinator.onExhausted(player))
        assertTrue(coordinator.onExhausted(player))
        runCurrent()
        assertEquals(0, calls)
        completed.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, calls)
        verify(player).seekToNextMediaItem()
        verify(player).prepare()
        verify(player).play()
        verify(player, never()).stop()
    }

    @Test fun `empty fallback stops terminal context playback`() = runTest {
        val player = player()
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, {}, { _, _ -> false }, { it.stop() })
        assertTrue(coordinator.onExhausted(player))
        advanceUntilIdle()
        verify(player).stop()
        verify(player, never()).play()
    }

    @Test fun `new queue invalidates suspended recommendation result`() = runTest {
        val player = player()
        val loaded = CompletableDeferred<Unit>()
        var generation = 1L
        var accepted = true
        val coordinator = ContextQueueContinuationCoordinator(this, { generation }, {}, { _, isCurrent ->
            loaded.await()
            accepted = isCurrent()
            true
        }, { it.stop() })
        coordinator.onExhausted(player)
        runCurrent()
        generation++
        loaded.complete(Unit)
        advanceUntilIdle()
        assertFalse(accepted)
        verify(player, never()).play()
        verify(player, never()).stop()
    }

    @Test fun `pause during refill prevents automatic restart`() = runTest {
        val player = player()
        val loaded = CompletableDeferred<Unit>()
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, {}, { _, _ ->
            loaded.await()
            true
        }, { it.stop() })
        coordinator.onExhausted(player)
        runCurrent()
        `when`(player.playWhenReady).thenReturn(false)
        loaded.complete(Unit)
        advanceUntilIdle()
        verify(player, never()).play()
        verify(player, never()).stop()
    }

    @Test fun `sleep timer and ordinary queues do not request context continuation`() = runTest {
        var calls = 0
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, {}, { _, _ ->
            calls++
            true
        }, { it.stop() })
        assertFalse(coordinator.onExhausted(player(context = false)))
        SleepTimerHolder.sleepAtEndOfEpisode = true
        assertFalse(coordinator.onExhausted(player()))
        advanceUntilIdle()
        assertEquals(0, calls)
    }

    @Test fun `failed refill stops instead of leaving ended player ready to play`() = runTest {
        val player = player()
        val coordinator = ContextQueueContinuationCoordinator(this, { 1L }, {}, { _, _ -> error("offline") }, { it.stop() })
        coordinator.onExhausted(player)
        advanceUntilIdle()
        verify(player).stop()
        verify(player, never()).play()
    }
}
