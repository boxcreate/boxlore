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

    @Test
    fun aNarrowCardDropsCoversBeforeTheTitleLosesTheMonth() {
        val three = listOf("a", "b", "c")
        // A 360dp phone (328dp card) keeps all three covers; a 400dp phone too.
        assertEquals(three, StoryEntryLayout.coversShown(328f, three))
        assertEquals(three, StoryEntryLayout.coversShown(368f, three))
        // A 280dp window (248dp card): no covers, the title gets the width.
        assertEquals(emptyList<String?>(), StoryEntryLayout.coversShown(248f, three))
        // In between, the second cover (the furthest in) goes first and the top show stays.
        val middle = StoryEntryLayout.coversShown(300f, three)
        assertEquals(listOf("a", null, "c"), middle)
        // Whatever is shown, the title keeps its minimum width, or has every bit of the card there is.
        listOf(248f, 280f, 300f, 328f, 600f).forEach { width ->
            val shown = StoryEntryLayout.coversShown(width, three)
            assertTrue(shown.isEmpty() || StoryEntryLayout.titleWidth(width, shown) >= StoryEntryLayout.MIN_TITLE_WIDTH, "At $width: $shown")
        }
    }
}
