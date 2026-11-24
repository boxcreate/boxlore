package cx.aswin.boxlore.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home brings back the same content after its grid changes column count, not the same item number. */
class GridPlaceTest {
    @Test
    fun `Explore comes back on the row holding the show the listener reached, in both directions`() {
        val place = GridPlace(columns = 2)
        // Two columns: the third row holds shows 5 and 6.
        place.record(index = 10, offset = 30, key = exploreRowKey(2), columns = 2)
        val sideways = place.targetFor(newColumns = 5)
        assertEquals(exploreRowKey(0), sideways?.key)
        assertEquals(0, sideways?.offset)
        // Five columns: show 5 is in the first row. Turning back returns to its row, not the first one.
        place.columns = 5
        place.record(index = 8, offset = 0, key = exploreRowKey(0), columns = 5)
        assertEquals(exploreRowKey(2), place.targetFor(newColumns = 2)?.key)
    }

    @Test
    fun `other sections come back by key, even when their index changes`() {
        val place = GridPlace(columns = 2)
        place.record(index = 7, offset = 40, key = "because_you_like", columns = 2)
        val target = place.targetFor(newColumns = 3)
        assertEquals(GridTarget(7, 40, "because_you_like"), target)
        // On tablets Your shows and the Brief share one item, so the section is one place earlier.
        val tablet = listOf(4 to "hero", 5 to "your_shows", 6 to "because_you_like")
        assertEquals(6, target?.let { gridTargetIndex(it, tablet) })
    }

    @Test
    fun `an Explore row off screen is found from the rows on screen`() {
        val visible = listOf(9 to "discover_header", 10 to exploreRowKey(0))
        assertEquals(11, gridTargetIndex(GridTarget(12, 0, exploreRowKey(1)), visible))
        assertNull(gridTargetIndex(GridTarget(3, 0, "your_shows"), visible))
    }

    @Test
    fun `a grid at the top stays at the top`() {
        val place = GridPlace(columns = 2)
        place.record(index = 0, offset = 0, key = "hero", columns = 2)
        assertNull(place.targetFor(newColumns = 5))
    }
}
