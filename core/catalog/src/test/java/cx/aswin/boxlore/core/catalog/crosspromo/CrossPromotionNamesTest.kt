package cx.aswin.boxlore.core.catalog.crosspromo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossPromotionNamesTest {
    @Test
    fun `decorations around a promoted name are removed`() {
        mapOf(
            "Blood Trails, Season Three" to "Blood Trails",
            "The Village: Season 2" to "The Village",
            "'Back Issue' from Pineapple Street Studios" to "Back Issue",
            "MADAM RAM Starring Toni Collette" to "MADAM RAM",
            "The Review, a new podcast from The Atlantic" to "The Review",
            "RESTART a new podcast from the makers of Tumanbay" to "RESTART",
            "[REDACTED] S1E1: False Start (Part 1)" to "[REDACTED]",
            "Hi Nay: Ep 1 – Bulok" to "Hi Nay",
            "Canada By Night - 1.01 - Welcome to New Haven" to "Canada By Night",
            "Dustwalker (trailer swap)" to "Dustwalker",
            "Our New Show: Edging" to "Edging",
            "Jill's New Podcast, We the Sunlight, with Juls Bindi" to "We the Sunlight",
            "CRIMES OF REDDIT, Sabrina’s new show with Alexis Linkletter" to "CRIMES OF REDDIT",
            "Legacy Talk is Back for Season 2!" to "Legacy Talk",
            "Not A Diving Podcast #010 Appleblim" to "Not A Diving Podcast",
            "An all-new season of ‘Dateline: Missing In America’" to "Dateline: Missing In America",
            "Episodes 1 & 2 of Victorian Gothic: The Hauntings are Live!" to "Victorian Gothic: The Hauntings",
            "In The Cards Trailer" to "In The Cards",
        ).forEach { (raw, name) -> assertEquals(name, CrossPromotionNames.clean(raw), raw) }
    }

    @Test
    fun `seasons episodes and cue words alone are not names`() {
        listOf("Season 3", "Episode 4", "our new series", "Feed Drop", "Mini-episode", "Bonus").forEach {
            assertNull(CrossPromotionNames.clean(it), it)
        }
    }

    @Test
    fun `host series are related while explicit names stay exact`() {
        assertTrue(CrossPromotionNames.related("Nightingale Halloween", "Nightingale"))
        assertTrue(CrossPromotionNames.related("Insomniac", "Monster Presents: Insomniac"))
        assertTrue(CrossPromotionNames.related("New York Times Narrated", "The New York Times Narrated"))
        assertFalse(CrossPromotionNames.related("Edging", "Thirteen"))
        assertFalse(CrossPromotionNames.sameShow("Serial", "Serial Killers"))
        assertTrue(CrossPromotionNames.sameShow("The Daily", "Daily Podcast"))
    }

    @Test
    fun `shorter forms cover a colon subtitle and a host`() {
        assertEquals(listOf("Fiasco: Benghazi", "Fiasco"), CrossPromotionNames.variants("Fiasco: Benghazi"))
        assertEquals(listOf("NeferTV with Ann Foster", "NeferTV"), CrossPromotionNames.variants("NeferTV with Ann Foster"))
    }
}
