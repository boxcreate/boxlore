package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLanguageTest {
    @Test
    fun `the language list leads with the system choice and names each language in itself`() {
        assertEquals(
            listOf("", "en", "es", "pt-BR", "de", "fr"),
            AppLanguage.entries.map { it.tag },
        )
        assertEquals(
            listOf(null, "English", "Español", "Português (Brasil)", "Deutsch", "Français"),
            AppLanguage.entries.map { it.nativeName },
        )
        assertEquals(AppLanguage.entries.drop(2), AppLanguage.translated)
    }

    @Test
    fun `stored and system tags map to a choice`() {
        val table = mapOf(
            null to AppLanguage.SYSTEM,
            "" to AppLanguage.SYSTEM,
            "en" to AppLanguage.ENGLISH,
            "en-GB" to AppLanguage.ENGLISH,
            "es" to AppLanguage.SPANISH,
            "es-419" to AppLanguage.SPANISH,
            "pt-BR" to AppLanguage.PORTUGUESE_BRAZIL,
            "pt-br" to AppLanguage.PORTUGUESE_BRAZIL,
            "pt" to AppLanguage.PORTUGUESE_BRAZIL,
            "pt-PT" to AppLanguage.SYSTEM,
            "de-AT" to AppLanguage.GERMAN,
            "fr-CA" to AppLanguage.FRENCH,
            "hi-IN" to AppLanguage.SYSTEM,
        )
        table.forEach { (tag, expected) -> assertEquals("tag $tag", expected, AppLanguage.fromTag(tag)) }
    }

    @Test
    fun `the phone's language list suggests a translation first`() {
        val table = mapOf(
            listOf("es-ES") to AppLanguage.SPANISH,
            listOf("es-US") to AppLanguage.SPANISH,
            listOf("es-419") to AppLanguage.SPANISH,
            listOf("en-US", "es-MX") to AppLanguage.SPANISH,
            listOf("pt-BR") to AppLanguage.PORTUGUESE_BRAZIL,
            listOf("pt") to AppLanguage.PORTUGUESE_BRAZIL,
            listOf("de-DE") to AppLanguage.GERMAN,
            listOf("de-CH") to AppLanguage.GERMAN,
            listOf("fr-FR") to AppLanguage.FRENCH,
            listOf("fr-BE") to AppLanguage.FRENCH,
            listOf("fr-CA") to AppLanguage.FRENCH,
            listOf("en-CA", "fr-CA") to AppLanguage.FRENCH,
            listOf("de-LU", "fr-LU") to AppLanguage.GERMAN,
            listOf("it-CH", "fr-CH") to AppLanguage.FRENCH,
            // Portugal's Portuguese is not offered, but a later language still counts.
            listOf("pt-PT", "es-ES") to AppLanguage.SPANISH,
        )
        table.forEach { (tags, expected) -> assertEquals("locales $tags", expected, suggest(tags)) }
    }

    @Test
    fun `without a language match the phone's region decides`() {
        val spanish = listOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PR", "PA", "UY")
        spanish.forEach { region -> assertEquals("en-$region", AppLanguage.SPANISH, suggest(listOf("en-$region"))) }
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, suggest(listOf("en-BR")))
        listOf("DE", "AT", "LI").forEach { assertEquals("en-$it", AppLanguage.GERMAN, suggest(listOf("en-$it"))) }
        listOf("FR", "MC").forEach { assertEquals("en-$it", AppLanguage.FRENCH, suggest(listOf("en-$it"))) }
        // The region is the first locale's, not a later one's.
        assertNull(suggest(listOf("en-US", "en-MX")))
        assertEquals(AppLanguage.GERMAN, AppLanguageMatcher.forRegion("at"))
    }

    @Test
    fun `regions with several languages and other places suggest nothing`() {
        listOf("CH", "BE", "CA", "LU", "US", "GB", "IN", "PT", "IT", "JP").forEach { region ->
            assertNull("en-$region", suggest(listOf("en-$region")))
        }
        assertNull(suggest(listOf("it-CH")))
        assertNull(suggest(listOf("nl-BE")))
        assertNull(suggest(listOf("pt-PT")))
        assertNull(suggest(listOf("pt-AO")))
        assertNull(suggest(listOf("en")))
        assertNull(suggest(emptyList()))
    }

    @Test
    fun `the picked language is stored, and unset until picked`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = BoxcastPrefs(context)
        assertNull(prefs.getAppLanguageTag())
        prefs.setAppLanguageTag(AppLanguage.SYSTEM.tag)
        assertEquals("", BoxcastPrefs(context).getAppLanguageTag())
        prefs.setAppLanguageTag(AppLanguage.GERMAN.tag)
        assertEquals("de", BoxcastPrefs(context).getAppLanguageTag())
    }

    @Test
    fun `the screens show the picked translation, or with System default the first phone language boxlore has`() {
        fun shown(choice: AppLanguage, vararg tags: String) = AppLanguageMatcher.shown(choice, tags.map(Locale::forLanguageTag))
        assertEquals(AppLanguage.GERMAN, shown(AppLanguage.GERMAN, "en-IN"))
        assertNull(shown(AppLanguage.ENGLISH, "es-ES"))
        assertEquals(AppLanguage.SPANISH, shown(AppLanguage.SYSTEM, "es-MX", "en-US"))
        // English comes first, so the screens are English even with Spanish lower in the list.
        assertNull(shown(AppLanguage.SYSTEM, "en-IN", "es-ES"))
        // A language boxlore lacks is skipped, as Android does.
        assertEquals(AppLanguage.FRENCH, shown(AppLanguage.SYSTEM, "hi-IN", "fr-FR"))
        assertNull(shown(AppLanguage.SYSTEM, "hi-IN"))
        assertNull(shown(AppLanguage.SYSTEM, "pt-PT"))
        assertNull(shown(AppLanguage.SYSTEM))
    }

    private fun suggest(tags: List<String>): AppLanguage? = AppLanguageMatcher.suggest(tags.map(Locale::forLanguageTag))
}
