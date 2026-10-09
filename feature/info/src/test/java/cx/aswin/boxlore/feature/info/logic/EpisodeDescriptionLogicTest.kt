package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeDescriptionLogicTest {
    @Test
    fun `chapter links preserve fractional start times`() {
        val chapters = listOf(Chapter(0.0, "Intro"), Chapter(65.5, "Discussion"))
        assertEquals(0L, chapterLinkPositionMs("play-position:0", chapters))
        assertEquals(65_500L, chapterLinkPositionMs("play-position:65", chapters))
        listOf("play-position:oops", "play-position:30", "https://example.org/65", "play-position:-1").forEach {
            assertNull(chapterLinkPositionMs(it, chapters))
        }
        assertNull(chapterLinkPositionMs("play-position:0", listOf(Chapter(Double.NaN, "Invalid"))))
    }

    @Test
    fun `passing sliver idle and missing section do not count as recommendation scrolling`() {
        assertFalse(relatedSectionScrollEngaged(true, 950, 600, 0, 1000))
        assertFalse(relatedSectionScrollEngaged(true, -550, 600, 0, 1000))
        assertFalse(relatedSectionScrollEngaged(false, 200, 600, 0, 1000))
        assertFalse(relatedSectionScrollEngaged(true, null, 600, 0, 1000))
        assertFalse(relatedSectionScrollEngaged(true, 0, 600, 0, 0))
        assertTrue(relatedSectionScrollEngaged(true, 500, 600, 0, 1000))
    }

    @Test
    fun `section taller than viewport can count when it fills the viewport`() {
        assertTrue(relatedSectionScrollEngaged(true, -100, 1600, 0, 700))
        assertFalse(relatedSectionScrollEngaged(true, 650, 1600, 0, 700))
    }
}
