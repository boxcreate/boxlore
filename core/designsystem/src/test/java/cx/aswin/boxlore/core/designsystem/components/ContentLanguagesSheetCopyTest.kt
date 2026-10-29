package cx.aswin.boxlore.core.designsystem.components

import cx.aswin.boxlore.core.model.ContentLanguageSelection
import org.junit.Assert.assertEquals
import org.junit.Test

/** What "Include English" says in each state of the languages sheet (D9). */
class ContentLanguagesSheetCopyTest {
    private fun text(languages: List<String>, englishOff: Boolean): String {
        val state = ContentLanguageSelection.englishSwitch(languages, englishOff)
        return includeEnglishSupportingText(state, languages.filter { it != "en" })
    }

    @Test
    fun keptOnNamesTheLanguagesThatKeepIt() {
        assertEquals(
            "Stays on with Hindi. You can turn it off when you pick only German, French, Spanish, Dutch, Portuguese or Russian.",
            text(listOf("en", "hi"), englishOff = false),
        )
        assertEquals("Stays on with Hindi and Chinese.", text(listOf("en", "de", "hi", "zh"), englishOff = true).substringBefore(" You"))
    }

    @Test
    fun withOnlyEnglishItSaysWhenItCanBeTurnedOff() {
        assertEquals(
            "You can turn it off when you pick only German, French, Spanish, Dutch, Portuguese or Russian.",
            text(listOf("en"), englishOff = false),
        )
    }

    @Test
    fun onAndAllowedItSaysWhatTurningItOffDoes() {
        assertEquals("Turn it off to get only shows in German and French.", text(listOf("en", "de", "fr"), englishOff = false))
    }

    @Test
    fun offItSaysEnglishFillsThinResults() {
        assertEquals("If there aren’t enough German shows, boxlore adds a few in English.", text(listOf("de"), englishOff = true))
        assertEquals("If there aren’t enough shows in your languages, boxlore adds a few in English.", text(listOf("de", "fr"), englishOff = true))
    }
}
