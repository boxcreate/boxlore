package cx.aswin.boxlore.feature.history.story

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryEntryLayoutTest {
    @Test
    fun theTitleStopsShortOfTheCoversItShows() {
        // Regression: "Your October story" ran under the fanned covers. The furthest cover in (the
        // second, turned 12°, 84dp from the edge and 62dp wide) reaches about 158dp in, shadow included.
        val three = StoryEntryLayout.titleEnd(listOf("a", "b", "c"))
        assertTrue(three in 160f..175f, "Clear of the second cover: $three")
        // A phone card (360dp screen, 16dp margins) leaves the title at least 150dp: two lines for "Your September story".
        assertTrue(328f - StoryEntryLayout.TITLE_START - three >= 140f)
        // With only the top show, the title may run further.
        val one = StoryEntryLayout.titleEnd(listOf("a"))
        assertTrue(one < three)
        // A missing cover isn't drawn, so it takes no room; with none the title only keeps the card's margin.
        assertEquals(one, StoryEntryLayout.titleEnd(listOf("a", null, null)))
        assertEquals(StoryEntryLayout.TITLE_START, StoryEntryLayout.titleEnd(emptyList()))
    }
}
