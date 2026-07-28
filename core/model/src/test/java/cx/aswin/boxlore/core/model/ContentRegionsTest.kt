package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentRegionsTest {
    @Test
    fun canonicalize_mapsAliases() {
        assertEquals("gb", ContentRegions.canonicalize("uk"))
        assertEquals("in", ContentRegions.canonicalize("ind"))
        assertEquals("de", ContentRegions.canonicalize("de"))
        assertEquals("us", ContentRegions.canonicalize("zz"))
        assertNull(ContentRegions.canonicalizeOrNull("zz"))
    }

    @Test
    fun normalizeLanguages_forcesEnAndDefaults() {
        assertEquals(listOf("en", "hi"), ContentRegions.normalizeLanguages(emptyList(), "in"))
        assertEquals(listOf("en", "zh"), ContentRegions.normalizeLanguages(listOf("zh"), "sg"))
        assertEquals(listOf("en"), ContentRegions.normalizeLanguages(listOf("en"), "fr"))
    }

    @Test
    fun languageGroupsPutsRecommendedFirst() {
        val groups = ContentRegions.languageGroupsForCountry("sg")
        assertEquals(listOf("en", "zh"), groups.recommended)
        assertTrue("hi" in groups.more)
        assertTrue("zh" !in groups.more)
    }

    @Test
    fun expandLanguagesForQuery_addsIndonesianInTag() {
        assertEquals(listOf("en", "id", "in"), ContentRegions.expandLanguagesForQuery(listOf("en", "id")))
    }

    @Test
    fun isOffMarketLanguage() {
        assertTrue(ContentRegions.isOffMarketLanguage("ru", "in"))
        assertFalse(ContentRegions.isOffMarketLanguage("hi", "in"))
        assertFalse(ContentRegions.isOffMarketLanguage("en", "us"))
    }

    @Test
    fun briefingMarket_mapsNonCoreToGlobal() {
        assertEquals("global", ContentRegions.briefingMarket("de"))
        assertEquals("gb", ContentRegions.briefingMarket("uk"))
    }
}
