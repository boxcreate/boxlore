package cx.aswin.boxlore.core.playback

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackTaskRemovalPolicyTest {
    @Test
    fun `active playback keeps the service running`() {
        val plan =
            PlaybackTaskRemovalPolicy.plan(
                hasPlayer = true,
                playWhenReady = true,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
            )

        assertTrue(plan.keepServiceRunning)
        assertFalse(plan.persistBeforeStop)
    }

    @Test
    fun `paused player persists before service stop`() {
        val plan =
            PlaybackTaskRemovalPolicy.plan(
                hasPlayer = true,
                playWhenReady = false,
                mediaItemCount = 1,
                playbackState = Player.STATE_READY,
            )

        assertFalse(plan.keepServiceRunning)
        assertTrue(plan.persistBeforeStop)
    }

    @Test
    fun `idle or ended player with media persists before service stop`() {
        listOf(Player.STATE_IDLE, Player.STATE_ENDED).forEach { state ->
            val plan =
                PlaybackTaskRemovalPolicy.plan(
                    hasPlayer = true,
                    playWhenReady = true,
                    mediaItemCount = 1,
                    playbackState = state,
                )

            assertFalse(plan.keepServiceRunning)
            assertTrue(plan.persistBeforeStop)
        }
    }

    @Test
    fun `missing player or empty playlist stops without a meaningless save`() {
        val missingPlayer =
            PlaybackTaskRemovalPolicy.plan(
                hasPlayer = false,
                playWhenReady = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_IDLE,
            )
        val emptyPlaylist =
            PlaybackTaskRemovalPolicy.plan(
                hasPlayer = true,
                playWhenReady = false,
                mediaItemCount = 0,
                playbackState = Player.STATE_READY,
            )

        assertFalse(missingPlayer.persistBeforeStop)
        assertFalse(missingPlayer.keepServiceRunning)
        assertFalse(emptyPlaylist.persistBeforeStop)
        assertFalse(emptyPlaylist.keepServiceRunning)
    }
}
