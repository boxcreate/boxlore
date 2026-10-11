package cx.aswin.boxlore.core.designsystem.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.BriefingArtwork
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class BriefingArtworkTest {
    @Test
    fun `each region has seven distinct weekday covers and its own fixed player square`() {
        val monday = LocalDate.parse("2026-10-05")
        val regions = listOf("global", "in", "gb", "us")
        val allCovers = regions.flatMap { region ->
            val covers = (0L..6L).map { briefingCoverResource(region, monday.plusDays(it).toString()) }
            assertEquals(7, covers.toSet().size)
            assertFalse(covers.contains(briefingPlayerResource(region)))
            covers
        }
        assertEquals(28, allCovers.toSet().size)
        assertEquals(4, regions.map(::briefingPlayerResource).toSet().size)
    }

    @Test
    fun `cover follows edition weekday across weeks rather than the current day`() {
        assertEquals(R.drawable.briefing_cover_global_01, briefingCoverResource("global", "2026-10-05"))
        assertEquals(R.drawable.briefing_cover_global_07, briefingCoverResource("global", "2026-10-11"))
        assertEquals(briefingCoverResource("in", "2026-10-06"), briefingCoverResource("in", "2026-10-13"))
    }

    @Test
    fun `player artwork uses named square resources instead of changing numeric resource ids`() {
        assertEquals("android.resource://cx.aswin.boxlore/drawable/daily_briefing_india", briefingPlayerArtworkUri("cx.aswin.boxlore", "IND"))
        assertEquals("android.resource://cx.aswin.boxlore.test/drawable/daily_briefing_uk", briefingPlayerArtworkUri("cx.aswin.boxlore.test", "gb"))
        assertEquals("android.resource://cx.aswin.boxlore/drawable/daily_briefing_usa", briefingPlayerArtworkUri("cx.aswin.boxlore", "us"))
        assertEquals("android.resource://cx.aswin.boxlore/drawable/daily_briefing_global", briefingPlayerArtworkUri("cx.aswin.boxlore", "unknown"))
    }

    @Test
    fun `every shared alias resolves the same cover square and localizable label`() {
        BriefingArtwork.entries.forEach { artwork ->
            val canonical = artwork.regionAliases.first()
            artwork.regionAliases.forEach { alias ->
                assertEquals(briefingPlayerResource(canonical), briefingPlayerResource(alias.uppercase(java.util.Locale.ROOT)))
                assertEquals(briefingCoverResource(canonical, "2026-10-08"), briefingCoverResource(alias, "2026-10-08"))
                assertEquals(briefingRegionLabel(canonical), briefingRegionLabel(alias))
            }
        }
    }

    @Test
    fun `legacy region aliases and invalid dates have deterministic artwork`() {
        listOf("IND" to "in", "UK" to "gb", "USA" to "us", "unknown" to "global").forEach { (alias, region) ->
            assertEquals(briefingCoverResource(region, "2026-10-08"), briefingCoverResource(alias, "2026-10-08"))
            assertEquals(briefingPlayerResource(region), briefingPlayerResource(alias))
        }
        listOf("", "not-a-date", "2026-02-30").forEach { date ->
            assertEquals(R.drawable.briefing_cover_uk_01, briefingCoverResource("gb", date))
        }
    }
}
