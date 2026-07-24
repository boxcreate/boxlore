package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RegionAliasHelpersTest {
    @Test
    fun canonicalRegionCode_mapsAliases() {
        assertEquals("gb", canonicalRegionCode("uk"))
        assertEquals("gb", canonicalRegionCode("GB"))
        assertEquals("in", canonicalRegionCode("ind"))
        assertEquals("in", canonicalRegionCode(" IN "))
        assertEquals("us", canonicalRegionCode("us"))
        assertEquals("fr", canonicalRegionCode("fr"))
        assertNull(canonicalRegionCode("zz"))
    }

    @Test
    fun regionDisplayLabel_usesAliasesAndUsaFallback() {
        assertEquals("UK", regionDisplayLabel("uk"))
        assertEquals("India", regionDisplayLabel("ind"))
        assertEquals("USA", regionDisplayLabel("us"))
        assertEquals("USA", regionDisplayLabel("not-a-region"))
    }
}
