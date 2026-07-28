package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentLanguageSelectionTest {
    @Test
    fun applyToggle_englishLockIsNoOp() {
        assertNull(
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en", "hi"),
                languageCode = "en",
                country = "in",
            ),
        )
    }

    @Test
    fun applyToggle_addsLanguageWhenUnderCapacity() {
        assertEquals(
            listOf("en", "hi", "fr"),
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en", "hi"),
                languageCode = "fr",
                country = "in",
            ),
        )
    }

    @Test
    fun applyToggle_deselectsAtCapacity() {
        assertEquals(
            listOf("en", "hi", "fr"),
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en", "hi", "fr", "de"),
                languageCode = "de",
                country = "de",
            ),
        )
    }

    @Test
    fun applyToggle_blocksAddAtCapacity() {
        assertNull(
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en", "hi", "fr", "de"),
                languageCode = "nl",
                country = "nl",
            ),
        )
    }

    @Test
    fun applyToggle_normalizesBcp47Tags() {
        assertEquals(
            listOf("en", "pt"),
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en"),
                languageCode = "pt-BR",
                country = "br",
            ),
        )
    }

    @Test
    fun applyToggle_canonicalizesCountryAlias() {
        val result =
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en"),
                languageCode = "hi",
                country = "ind",
            )
        assertEquals(listOf("en", "hi"), result)
    }

    @Test
    fun applyToggle_offMarketLanguageStillAllowed() {
        val result =
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en"),
                languageCode = "ru",
                country = "in",
            )
        assertEquals(listOf("en", "ru"), result)
        assertTrue(ContentRegions.isOffMarketLanguage("ru", "in"))
    }

    @Test
    fun applyToggle_rejectsInvalidLanguageCode() {
        assertNull(
            ContentLanguageSelection.applyToggle(
                selectedLanguages = listOf("en"),
                languageCode = "xx",
                country = "us",
            ),
        )
    }
}
