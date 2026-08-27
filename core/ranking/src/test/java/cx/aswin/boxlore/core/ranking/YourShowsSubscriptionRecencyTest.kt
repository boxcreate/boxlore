package cx.aswin.boxlore.core.ranking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YourShowsSubscriptionRecencyTest {
    private val nowMs = 1_700_000_000_000L

    @Test
    fun `floor stays near top for three days then decays`() {
        val nowFloor = YourShowsSubscriptionRecency.floor(nowMs, nowMs)
        val dayFloor =
            YourShowsSubscriptionRecency.floor(nowMs - 24L * 3_600_000L, nowMs)
        val threeDayFloor =
            YourShowsSubscriptionRecency.floor(nowMs - 72L * 3_600_000L, nowMs)
        val weekFloor =
            YourShowsSubscriptionRecency.floor(nowMs - 7L * 24L * 3_600_000L, nowMs)

        assertEquals(YourShowsSubscriptionRecency.PEAK_FLOOR, nowFloor, 1e-9)
        assertTrue(dayFloor > 0.95)
        assertEquals(YourShowsSubscriptionRecency.WINDOW_END_FLOOR, threeDayFloor, 1e-9)
        assertTrue(weekFloor < 0.40)
        assertTrue(nowFloor > dayFloor)
        assertTrue(dayFloor > threeDayFloor)
    }

    @Test
    fun `floor lifts a buried deterministic score for a fresh sub`() {
        val lifted =
            YourShowsSubscriptionRecency.apply(
                score = -0.8,
                subscribedAt = nowMs,
                nowMs = nowMs,
            )
        assertEquals(YourShowsSubscriptionRecency.PEAK_FLOOR, lifted, 1e-9)
    }

    @Test
    fun `floor does not outrank a stronger native score`() {
        assertEquals(
            0.99,
            YourShowsSubscriptionRecency.apply(
                score = 0.99,
                subscribedAt = nowMs,
                nowMs = nowMs,
            ),
            1e-9,
        )
    }

    @Test
    fun `missing subscribe time does not lift the score`() {
        assertEquals(
            -0.4,
            YourShowsSubscriptionRecency.apply(
                score = -0.4,
                subscribedAt = 0L,
                nowMs = nowMs,
            ),
            1e-9,
        )
    }
}
