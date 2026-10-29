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
        assertEquals("global", ContentRegions.briefingMarket("global"))
        assertEquals("global", ContentRegions.briefingMarket(" GLOBAL "))
        assertEquals("gb", ContentRegions.briefingMarket("uk"))
    }

    @Test
    fun englishCanBeSwitchedOffOnlyForDeepPoolLanguages() {
        assertEquals(setOf("fr", "de", "es", "pt", "ru", "nl"), ContentRegions.ENGLISH_OFF_LANGUAGES)
        assertTrue(ContentRegions.canSwitchEnglishOff(listOf("en", "de")))
        assertTrue(ContentRegions.canSwitchEnglishOff(listOf("de-DE", "fr")))
        assertFalse(ContentRegions.canSwitchEnglishOff(listOf("en", "hi")))
        assertFalse(ContentRegions.canSwitchEnglishOff(listOf("de", "zh")))
        assertFalse(ContentRegions.canSwitchEnglishOff(listOf("en")))
        assertFalse(ContentRegions.canSwitchEnglishOff(emptyList()))
    }

    @Test
    fun normalizeDropsEnglishOnlyWhenSwitchedOffAndAllowed() {
        assertEquals(listOf("de"), ContentRegions.normalizeLanguages(listOf("en", "de"), "de", englishOff = true))
        assertEquals(listOf("en", "hi"), ContentRegions.normalizeLanguages(listOf("hi"), "in", englishOff = true))
        assertEquals(listOf("en", "de"), ContentRegions.normalizeLanguages(listOf("de"), "de"))
        // English's slot goes to a fourth pick, and comes back when English is on again.
        val four = listOf("de", "fr", "es", "nl")
        assertEquals(four, ContentRegions.normalizeLanguages(four, "de", englishOff = true))
        assertEquals(listOf("en", "de", "fr", "es"), ContentRegions.normalizeLanguages(four, "de"))
    }
}
