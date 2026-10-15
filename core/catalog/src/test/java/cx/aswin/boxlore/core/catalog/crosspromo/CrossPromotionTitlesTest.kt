package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.model.CrossPromotionIndicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which rule each title wording reaches, the cue and explicitness it carries, and the titles it refuses. */
class CrossPromotionTitlesTest {
    private fun candidate(title: String) = requireNotNull(CrossPromotionTitles.candidate(title)) { title }

    @Test
    fun `feed exchanges name another show outright`() {
        mapOf(
            "Feed Drop: Serial" to "Serial",
            "RQ Network Feed Drop – Hi Nay: Ep 1 – Bulok" to "Hi Nay",
            "John recommends....A New Day with Gerry Hussey" to "A New Day with Gerry Hussey",
            "Burned by a Paper Sun - Feed Drop" to "Burned by a Paper Sun",
            "Enjoy this showdown from our friends at Smash Boom Best!" to "Smash Boom Best",
            "NEW SHOW ANNOUNCEMENT | Victorian Gothic: The Hauntings" to "Victorian Gothic: The Hauntings",
        ).forEach { (title, name) ->
            val result = candidate(title)
            assertEquals(TitleCue.EXCHANGE, result.cue, title)
            assertEquals(name, result.name, title)
        }
    }

    @Test
    fun `delimited introductions and new show wording are explicit`() {
        mapOf(
            "Introducing: Drilled" to "Drilled",
            "Listen Now: Don’t Cross Kat" to "Don’t Cross Kat",
            "Special Bonus - Introducing: Civilian or Spy" to "Civilian or Spy",
            "We Made a New Podcast Called Conversations with Ghosts! You Can Listen NOW!" to "Conversations with Ghosts",
            "I think you might like Multispective Podcast" to "Multispective Podcast",
            "Comfort Eating with Grace Dent: episode one of a new podcast" to "Comfort Eating with Grace Dent",
            "The Guardian’s new podcast series about AI: Black Box – prologue" to "Black Box",
        ).forEach { (title, name) ->
            val result = candidate(title)
            assertEquals(TitleCue.INTRODUCTION, result.cue, title)
            assertTrue(result.explicit, title)
            assertEquals(name, result.name, title)
        }
    }

    @Test
    fun `presenters coming soon and trailers need evidence`() {
        val presented = candidate("Wondery Presents: Bunga Bunga")
        assertEquals("Wondery", presented.presenter)
        assertEquals(CrossPromotionIndicator.TITLE_PRESENTS_PATTERN, presented.indicator)
        assertFalse(presented.explicit)
        assertEquals("The Daily", candidate("From the creators of Serial: The Daily").name)
        listOf("Coming Soon: Shell Game", "In The Cards Trailer", "The Princess Quest [SEASON 1 - Trailer]").forEach { title ->
            val result = candidate(title)
            assertEquals(TitleCue.INTRODUCTION, result.cue, title)
            assertFalse(result.explicit, title)
        }
    }

    @Test
    fun `weak and seamless wording keep their own cues`() {
        assertEquals(TitleCue.WEAK, candidate("BONUS: Murder & Magnolias").cue)
        assertEquals(TitleCue.WEAK, candidate("Culpable Returns").cue)
        assertEquals("Culpable", candidate("Culpable Returns").name)
        assertEquals(TitleCue.SEAMLESS, candidate("Introducing Ice Lions").cue)
        assertEquals(TitleCue.SEAMLESS, candidate("Listen to Infamous: From Conned to Cupid").cue)
        // A presenter after "Introducing" is part of the name, not a network.
        val gangster = candidate("Introducing Gangster Presents: The Story of Ronnie Biggs")
        assertEquals(TitleCue.SEAMLESS, gangster.cue)
        assertNull(gangster.presenter)
    }

    @Test
    fun `numbered mixes topics and seasons are refused`() {
        listOf(
            "Deep Ghosh presents United We Rise Podcast Nr. 197",
            "DJ MAJESTY PRESENTS AFROPIANO FREEMIX 05-14-2026",
            "Listen to This Before You Go Back to School",
            "Introducing Season 3",
            "Episode 47: Morning headlines",
        ).forEach { assertNull(CrossPromotionTitles.candidate(it), it) }
    }

    @Test
    fun `pieces offer the title, its quotes and the show after in`() {
        val pieces = CrossPromotionTitles.pieces("The Dark Secrets of \"The White Devil\" in Smoke Screen: Betrayal on the Bayou")
        assertTrue("The White Devil" in pieces, pieces.toString())
        assertTrue("Smoke Screen: Betrayal on the Bayou" in pieces, pieces.toString())
    }
}
