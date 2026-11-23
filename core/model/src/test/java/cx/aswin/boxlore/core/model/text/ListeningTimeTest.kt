package cx.aswin.boxlore.core.model.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningTimeTest {
    @Test fun `a partial minute counts as a whole one`() {
        assertEquals(1L, ListeningTime.minutesLeft(positionMs = 0L, durationMs = 59_500L))
        assertEquals(60L, ListeningTime.minutesLeft(positionMs = 30_000L, durationMs = 3_600_000L))
        assertEquals(22L, ListeningTime.minutesLeft(positionMs = 0L, durationMs = 21 * 60_000L + 1L))
    }

    @Test fun `whole hours stay whole`() {
        assertEquals(60L, ListeningTime.minutesLeft(durationSec = 3_600L, progress = 0f))
        assertEquals(30L, ListeningTime.minutesLeft(durationSec = 3_600L, progress = 0.5f))
    }

    @Test fun `stored progress and a live position give the same minutes`() {
        for ((durationSec, progress) in listOf(1_200L to 0.51f, 2_700L to 0.13f, 10_560L to 0.5f, 59L to 0.2f)) {
            val positionMs = (durationSec * 1_000L * progress.toDouble()).toLong()
            assertEquals(ListeningTime.minutesLeft(positionMs, durationSec * 1_000L), ListeningTime.minutesLeft(durationSec, progress))
        }
        // 14 of 20 minutes stored as a share (0.7f) sits a hair under 14 minutes; it still leaves 6, as the mini player says.
        assertEquals(6L, ListeningTime.minutesLeft(positionMs = 840_000L, durationMs = 1_200_000L))
        assertEquals(6L, ListeningTime.minutesLeft(durationSec = 1_200L, progress = 14f / 20f))
    }

    @Test fun `unknown lengths give nothing and the end still leaves a minute`() {
        assertNull(ListeningTime.minutesLeft(positionMs = 0L, durationMs = 0L))
        assertNull(ListeningTime.minutesLeft(positionMs = 10L, durationMs = -5L))
        assertNull(ListeningTime.minutesLeft(durationSec = 0L, progress = 0.5f))
        assertEquals(1L, ListeningTime.minutesLeft(positionMs = 90_000L, durationMs = 60_000L))
        assertEquals(1L, ListeningTime.minutesLeft(durationSec = 600L, progress = 1f))
        assertEquals(10L, ListeningTime.minutesLeft(durationSec = 600L, progress = Float.NaN))
    }
}
