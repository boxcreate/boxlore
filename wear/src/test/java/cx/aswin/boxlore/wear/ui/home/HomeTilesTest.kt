package cx.aswin.boxlore.wear.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTilesTest {
    @Test
    fun `with a phone linked and a mix, three full rows and Settings by itself`() {
        assertEquals(
            listOf(
                listOf(HomeLink.Shows, HomeLink.JumpBackIn),
                listOf(HomeLink.NewEpisodes, HomeLink.Search),
                listOf(HomeLink.DailyMix, HomeLink.Downloads),
                listOf(HomeLink.Settings),
            ),
            HomeTiles.rows(phoneLinked = true, hasDailyMix = true),
        )
    }

    @Test
    fun `a watch that never met a phone hides both tiles and leaves no gap`() {
        assertEquals(
            listOf(listOf(HomeLink.Shows, HomeLink.JumpBackIn), listOf(HomeLink.Search, HomeLink.Downloads), listOf(HomeLink.Settings)),
            HomeTiles.rows(phoneLinked = false, hasDailyMix = false),
        )
    }

    @Test
    fun `with only one of the two hidden the rows stay full`() {
        assertEquals(
            listOf(listOf(HomeLink.Shows, HomeLink.JumpBackIn), listOf(HomeLink.NewEpisodes, HomeLink.Search), listOf(HomeLink.Downloads, HomeLink.Settings)),
            HomeTiles.rows(phoneLinked = true, hasDailyMix = false),
        )
        assertEquals(
            listOf(listOf(HomeLink.Shows, HomeLink.JumpBackIn), listOf(HomeLink.Search, HomeLink.DailyMix), listOf(HomeLink.Downloads, HomeLink.Settings)),
            HomeTiles.rows(phoneLinked = false, hasDailyMix = true),
        )
    }
}
