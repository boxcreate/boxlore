package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BriefingEditionTest {
    @Test fun `saved global overrides US discovery without changing discovery region`() {
        assertEquals("global", BriefingEdition.resolve("global", "us"))
        assertEquals("global", BriefingEdition.resolve("global", "in"))
    }

    @Test fun `existing listeners follow app region until they choose a default`() {
        assertEquals("us", BriefingEdition.resolve(null, "us"))
        assertEquals("gb", BriefingEdition.resolve(BriefingEdition.FOLLOW_APP, "uk"))
        assertEquals("global", BriefingEdition.resolve(null, "de"))
    }

    @Test fun `explicit entry regions beat the saved default`() {
        assertEquals("in", BriefingEdition.resolve("global", "us", "in"))
        assertEquals("gb", BriefingEdition.resolve("global", "us", "UK"))
        assertEquals("global", BriefingEdition.resolve("global", "us", " "))
    }

    @Test fun `invalid defaults fail back to app region and aliases remain compatible`() {
        assertEquals(BriefingEdition.FOLLOW_APP, BriefingEdition.sanitize("bad-region"))
        assertEquals("gb", BriefingEdition.sanitize(" UK "))
        assertEquals("in", BriefingEdition.sanitize("IND"))
        assertEquals("us", BriefingEdition.resolve("bad-region", "us"))
    }
}
