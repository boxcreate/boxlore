package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BriefingArtworkTest {
    @Test
    fun `region aliases retain stable player resource names`() {
        val expected = mapOf(
            "global" to "global",
            "in" to "india",
            "ind" to "india",
            "gb" to "uk",
            "uk" to "uk",
            "us" to "usa",
            "usa" to "usa",
        )
        expected.forEach { (region, name) ->
            assertEquals(
                "android.resource://cx.aswin.boxlore/drawable/daily_briefing_$name",
                BriefingArtwork.forRegion(region.uppercase()).uri("cx.aswin.boxlore"),
            )
        }
    }

    @Test
    fun `unknown UI regions use global and URIs respect the installed package`() {
        assertEquals(
            "android.resource://example.test/drawable/daily_briefing_global",
            BriefingArtwork.forRegion("unknown").uri("example.test"),
        )
    }
}
