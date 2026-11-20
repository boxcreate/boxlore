package cx.aswin.boxlore.core.designsystem.components

import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.ContentLanguageSelection
import cx.aswin.boxlore.core.model.text.resolve
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What "Include English" says in each state of the languages sheet (D9), and the names it uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentLanguagesSheetCopyTest {
    private fun text(languages: List<String>, englishOff: Boolean): String {
        val state = ContentLanguageSelection.englishSwitch(languages, englishOff)
        return includeEnglishSupportingText(state, languages.filter { it != "en" }) { contentLanguageName(it, Locale.ENGLISH) }
            .resolve(ApplicationProvider.getApplicationContext<android.content.Context>())
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
        assertEquals(
            "There are far fewer German shows, so recommendations won’t match your taste as closely. If there aren’t enough, boxlore adds a few in English.",
            text(listOf("de"), englishOff = true),
        )
        assertEquals(
            "There are far fewer shows in your languages, so recommendations won’t match your taste as closely. If there aren’t enough, boxlore adds a few in English.",
            text(listOf("de", "fr"), englishOff = true),
        )
    }

    @Test
    fun namesKeepBoxloresEnglishLabelsAndFollowOtherLanguages() {
        assertEquals("German", contentLanguageName("de", Locale.ENGLISH))
        assertEquals("allemand", contentLanguageName("de", Locale.FRENCH))
        assertEquals("Deutsch", contentLanguageName("de", Locale.GERMAN))
        assertEquals("Allemand", capitalizedIn(contentLanguageName("de", Locale.FRENCH), Locale.FRENCH))
        assertEquals("USA", contentRegionName("us", Locale.ENGLISH))
        assertEquals("UK", contentRegionName("uk", Locale.ENGLISH))
        assertEquals("Deutschland", contentRegionName("de", Locale.GERMAN))
        assertEquals("Royaume-Uni", contentRegionName("gb", Locale.FRENCH))
    }
}
