package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomePinnedShowsTest {
    @Test
    fun `sanitize caps at five and drops blanks`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            HomePinnedShows.sanitize(listOf("a", "b", "a", "", "c", "d", "e", "f")),
        )
    }

    @Test
    fun `toggle pins until capacity then refuses`() {
        var ids = emptyList<String>()
        var result: HomePinnedShows.ToggleResult
        listOf("1", "2", "3", "4", "5").forEach { id ->
            val next = HomePinnedShows.toggle(ids, id)
            ids = next.first
            result = next.second
            assertEquals(HomePinnedShows.ToggleResult.Pinned, result)
        }
        val sixth = HomePinnedShows.toggle(ids, "6")
        assertEquals(ids, sixth.first)
        assertEquals(HomePinnedShows.ToggleResult.AtCapacity, sixth.second)

        val unpinned = HomePinnedShows.toggle(ids, "1")
        assertEquals(listOf("2", "3", "4", "5"), unpinned.first)
        assertEquals(HomePinnedShows.ToggleResult.Unpinned, unpinned.second)
    }

    @Test
    fun `toggle ignores blank id`() {
        val result = HomePinnedShows.toggle(listOf("a"), "  ")
        assertEquals(listOf("a"), result.first)
        assertEquals(HomePinnedShows.ToggleResult.Unpinned, result.second)
    }
}
