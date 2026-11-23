package cx.aswin.boxlore.tv.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YourShowsRulesTest {
    private fun shows(count: Int) = (1..count).toList()

    @Test
    fun `rows grow one at a time up to three`() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 3), listOf(0, 1, 8, 9, 16, 17, 24, 200).map { YourShowsRules.rowCount(it) })
    }

    @Test
    fun `shows fill rows of eight, left to right, and Home stops at three rows`() {
        assertEquals(listOf((1..8).toList(), listOf(9, 10)), YourShowsRules.arrange(shows(10)))
        // A listener with 200 subscriptions gets 24 on Home; All shows opens the rest in Library.
        assertEquals(listOf(8, 8, 8), YourShowsRules.arrange(shows(200)).map { it.size })
    }

    // The TV's content area between the rail and the safe area is 788 dp wide (960 − 124 − 48).
    @Test
    fun `a full row fits the screen at rest and with one cover widened into its name`() {
        val width = 788f
        assertTrue(YourShowsRules.rowWidth(YourShowsRules.PER_ROW, withPill = false) <= width)
        assertTrue(YourShowsRules.rowWidth(YourShowsRules.PER_ROW, withPill = true) <= width)
    }

    @Test
    fun `up and down keep the column, or land on a shorter row's last cover`() {
        val sizes = listOf(8, 8, 3)
        assertEquals(0, YourShowsRules.sameColumn(sizes, 1, 0))
        assertEquals(5, YourShowsRules.sameColumn(sizes, 1, 5))
        assertEquals(2, YourShowsRules.sameColumn(sizes, 2, 6))
        assertEquals(null, YourShowsRules.sameColumn(sizes, 3, 0))
        assertEquals(null, YourShowsRules.sameColumn(sizes, -1, 0))
    }

    @Test
    fun `no shows, no rows`() {
        assertEquals(emptyList<List<Int>>(), YourShowsRules.arrange(emptyList<Int>()))
    }

    @Test
    fun `the player clock reads minutes and seconds, and hours when there are some`() {
        assertEquals("0:00", TimeRules.clock(0))
        assertEquals("14:02", TimeRules.clock(842))
        assertEquals("1:02:05", TimeRules.clock(3725))
        assertEquals(1 to 5, TimeRules.split(65))
    }
}
