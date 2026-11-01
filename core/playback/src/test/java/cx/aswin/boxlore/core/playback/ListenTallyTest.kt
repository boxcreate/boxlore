package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.analytics.ListenSeeks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [ListenTally] on a fake clock: heard audio from the position, seeks, buffering and time taken. */
class ListenTallyTest {
    private var now = 1_000_000L

    /** Plays for [seconds], sampling once a second like the service ticker. */
    private fun ListenTally.play(seconds: Int, speed: Float = 1f,) {
        repeat(seconds) {
            now += 1_000L
            samplePosition(now, lastPositionMs + (1_000L * speed).toLong(), isPlaying = true, speed = speed)
        }
    }

    @Test
    fun `a ten minute listen hears ten minutes`() {
        val tally = ListenTally("l1", startedAtMs = now, startPositionMs = 0L, startWaitMs = 800L)

        tally.play(600)

        assertEquals(600_000L, tally.heardMs)
        assertEquals(600_000L, tally.elapsedMs(now))
        assertEquals(600_000L, tally.heardWallMs)
        assertEquals(800L, tally.startWaitMs)
    }

    @Test
    fun `at double speed ten minutes of clock hear twenty minutes of audio`() {
        val tally = ListenTally("l1", startedAtMs = now, startPositionMs = 0L, startWaitMs = null)

        tally.play(600, speed = 2f)

        assertEquals(1_200_000L, tally.heardMs)
        assertEquals(600_000L, tally.heardWallMs)
        assertEquals(600_000L, tally.elapsedMs(now))
    }

    @Test
    fun `paused time, seeks and jumps aren't heard`() {
        val tally = ListenTally("l1", startedAtMs = now, startPositionMs = 30_000L, startWaitMs = null)
        tally.play(10)

        now += 5_000L
        tally.samplePosition(now, tally.lastPositionMs, isPlaying = false, speed = 1f)

        tally.countSeek(fromMs = 40_000L, toMs = 160_000L)
        tally.moveTo(now, 160_000L)
        tally.play(10)
        tally.countSeek(fromMs = 170_000L, toMs = 140_000L)
        tally.moveTo(now, 140_000L)

        now += 1_000L
        tally.samplePosition(now, 400_000L, isPlaying = true, speed = 1f)

        assertEquals(20_000L, tally.heardMs)
        assertEquals(ListenSeeks(forward = 1, back = 1, forwardMs = 120_000L, backMs = 30_000L), tally.seeks)
    }

    @Test
    fun `buffering spells add up, and finishing closes one still running`() {
        val tally = ListenTally("l1", startedAtMs = now, startPositionMs = 0L, startWaitMs = null)

        tally.bufferingStarted(now)
        now += 6_000L
        assertEquals(6_000L, tally.bufferingEnded(now))
        assertNull(tally.bufferingEnded(now), "no spell is running")

        tally.bufferingStarted(now)
        now += 1_000L
        tally.bufferingStarted(now)
        now += 1_000L
        tally.finish(now)

        assertEquals(2, tally.bufferingCount)
        assertEquals(8_000L, tally.bufferingMs)
    }
}
