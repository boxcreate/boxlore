package cx.aswin.boxlore.feature.history.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InsightsLayoutTest {
    private val keys = listOf("time", "type", "top_shows", "when", "where", "mosaic", "records", "genres", "milestone")

    @Test
    fun `one column lists every card on its own row in order`() {
        assertEquals(keys.map { listOf(it) }, InsightsLayout.rows(keys, columns = 1))
    }

    @Test
    fun `two columns pair cards in order while the month and records keep rows of their own`() {
        assertEquals(
            listOf(listOf("time", "type"), listOf("top_shows", "when"), listOf("where"), listOf("mosaic"), listOf("records"), listOf("genres", "milestone")),
            InsightsLayout.rows(keys, columns = 2),
        )
    }

    @Test
    fun `a card is found in its row both ways`() {
        val two = InsightsLayout.rows(keys, columns = 2)
        assertEquals(1, InsightsLayout.rowOf(two, "when"))
        assertEquals(3, InsightsLayout.rowOf(InsightsLayout.rows(keys, columns = 1), "when"))
        assertEquals(-1, InsightsLayout.rowOf(two, "missing"))
    }

    @Test
    fun `two columns start at 1000dp of content`() {
        assertEquals(1, InsightsLayout.columns(999f))
        assertEquals(2, InsightsLayout.columns(1_000f))
    }
}
