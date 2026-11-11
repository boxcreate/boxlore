package cx.aswin.boxlore.core.playback.service

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A seek while playing must not reach the car as "stopped, then started": Android Auto redraws on
 * that and sends the open list back to the top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeekSettleWindowTest {
    private var nowMs = 10_000L
    private var settling = 0
    private val window = SeekSettleWindow(now = { nowMs }, onSettling = { settling++ })

    @Test
    fun aSeekWhilePlayingKeepsReportingPlayingUntilItSettles() {
        window.onSeek(wasPlaying = true)

        assertEquals(Player.STATE_READY, window.reported(Player.STATE_BUFFERING))
        nowMs += SeekSettleWindow.SETTLE_MS - 1
        assertEquals(Player.STATE_READY, window.reported(Player.STATE_BUFFERING))
    }

    @Test
    fun bufferingThatOutlastsTheSeekIsReported() {
        window.onSeek(wasPlaying = true)
        nowMs += SeekSettleWindow.SETTLE_MS

        assertEquals(Player.STATE_BUFFERING, window.reported(Player.STATE_BUFFERING))
    }

    @Test
    fun bufferingWithNoSeekOrAfterASeekWhileNotPlayingIsReported() {
        assertEquals(Player.STATE_BUFFERING, window.reported(Player.STATE_BUFFERING))

        window.onSeek(wasPlaying = true)
        window.onSeek(wasPlaying = false)

        assertEquals(Player.STATE_BUFFERING, window.reported(Player.STATE_BUFFERING))
    }

    @Test
    fun otherStatesAreNeverChanged() {
        window.onSeek(wasPlaying = true)

        listOf(Player.STATE_IDLE, Player.STATE_READY, Player.STATE_ENDED).forEach { state ->
            assertEquals(state, window.reported(state))
        }
    }

    // ---- the session's player ----

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = PlaybackServicePlayerFactory(context, CoroutineScope(Dispatchers.Unconfined))
    private val player = mock(Player::class.java)
    private var configuredSeeks = 0

    private fun sessionPlayer(): Player = factory.createForwardingPlayer(
        player = player,
        seekForwardMs = { 30_000L },
        seekBackMs = { 10_000L },
        onSeekByConfiguredIncrement = { _, _, _ -> configuredSeeks++ },
        onSkipNext = {},
        seekSettle = window,
    )

    @Test
    fun everyKindOfSeekWhilePlayingHidesItsBuffering() {
        `when`(player.isPlaying).thenReturn(true)
        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        val sessionPlayer = sessionPlayer()
        assertEquals(Player.STATE_BUFFERING, sessionPlayer.playbackState)

        listOf<(Player) -> Unit>({ it.seekForward() }, { it.seekBack() }, { it.seekTo(90_000L) }).forEach { seek ->
            nowMs += SeekSettleWindow.SETTLE_MS
            assertEquals(Player.STATE_BUFFERING, sessionPlayer.playbackState)
            seek(sessionPlayer)
            assertEquals(Player.STATE_READY, sessionPlayer.playbackState)
        }

        assertEquals(3, settling)
        assertEquals(2, configuredSeeks)
        verify(player).seekTo(90_000L)
    }

    @Test
    fun aSeekWhilePausedChangesNothing() {
        `when`(player.isPlaying).thenReturn(false)
        `when`(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        val sessionPlayer = sessionPlayer()

        sessionPlayer.seekForward()

        assertEquals(Player.STATE_BUFFERING, sessionPlayer.playbackState)
        assertEquals(0, settling)
    }
}
