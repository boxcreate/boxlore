package cx.aswin.boxlore.core.testing.i18n

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every translation in every module (`res/values-<language>`) keeps the English placeholders, uses
 * the plural forms its language needs, keeps "boxlore" lowercase, and translates nothing marked
 * `translatable="false"` or missing from the English. Missing translations are allowed; lint warns
 * about them. Rules: docs/i18n/TRANSLATING.md.
 */
class TranslationResourcesGuardTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: generateSequence(File(".").canonicalFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `every module's translations match their English`() {
        val resDirs = I18nSources.moduleDirs(projectRoot).map { File(it, "src/main/res") }.filter { it.isDirectory }
        require(resDirs.isNotEmpty()) { "No res folders under $projectRoot" }
        val problems = resDirs.flatMap { TranslationResources.problems(it, it.parentFile.parentFile.parentFile.relativeTo(projectRoot).path) }
        assertTrue(problems.isEmpty(), "Translations must match their English (docs/i18n/TRANSLATING.md):\n" + problems.joinToString("\n"))
    }

    @Test
    fun `values folders are read for their language`() {
        assertNull(TranslationResources.localeOf("values"))
        assertNull(TranslationResources.localeOf("values-night"))
        assertNull(TranslationResources.localeOf("values-v31"))
        assertNull(TranslationResources.localeOf("values-night-v31"))
        assertNull(TranslationResources.localeOf("values-car"))
        assertNull(TranslationResources.localeOf("values-sw600dp"))
        assertEquals("es", TranslationResources.localeOf("values-es"))
        assertEquals("pt-rBR", TranslationResources.localeOf("values-pt-rBR"))
        assertEquals("de", TranslationResources.localeOf("values-de-night"))
        assertEquals("b+es+419", TranslationResources.localeOf("values-b+es+419"))
        assertEquals("pt", TranslationResources.languageOf("pt-rBR"))
        assertEquals("es", TranslationResources.languageOf("b+es+419"))
    }

    @Test
    fun `placeholders cover format arguments and named tokens, not literal percents`() {
        assertEquals(listOf("%1\$s", "%2\$d"), TranslationResources.placeholders("%2\$d of %1\$s"))
        assertEquals(listOf("%.1f", "%d", "{month}"), TranslationResources.placeholders("%d in {month}, %.1f h"))
        assertEquals(emptyList<String>(), TranslationResources.placeholders("100%% done"))
    }

    @Test
    fun `good translations pass`() {
        val res = fixture(
            "values" to """
                <string name="title">Your shows on boxlore</string>
                <string name="count">%1${'$'}d of %2${'$'}d</string>
                <string name="key" translatable="false">boxlore_key</string>
                <plurals name="episodes"><item quantity="one">%d episode</item><item quantity="other">%d episodes</item></plurals>
                <string-array name="moods"><item>Calm</item><item>Bright</item></string-array>
            """,
            "values-es" to """
                <string name="title">Tus programas en boxlore</string>
                <string name="count">%1${'$'}d de %2${'$'}d</string>
                <plurals name="episodes"><item quantity="one">%d episodio</item><item quantity="many">%d de episodios</item><item quantity="other">%d episodios</item></plurals>
                <string-array name="moods"><item>Tranquilo</item><item>Alegre</item></string-array>
            """,
            "values-de" to """
                <plurals name="episodes"><item quantity="one">Eine Folge</item><item quantity="other">%d Folgen</item></plurals>
            """,
        )
        assertEquals(emptyList<String>(), TranslationResources.problems(res, "m"))
    }

    @Test
    fun `each broken rule is reported`() {
        val res = fixture(
            "values" to """
                <string name="title">Your shows on boxlore</string>
                <string name="count">%1${'$'}d of %2${'$'}d in {month}</string>
                <string name="key" translatable="false">boxlore_key</string>
                <plurals name="episodes"><item quantity="one">%d episode</item><item quantity="other">%d episodes</item></plurals>
                <string-array name="moods"><item>Calm</item><item>Bright</item></string-array>
            """,
            "values-fr" to """
                <string name="title">Vos émissions sur Boxlore</string>
                <string name="count">%1${'$'}d sur %2${'$'}s en {mois}</string>
                <string name="key">clé</string>
                <string name="gone">Ancien</string>
                <plurals name="episodes"><item quantity="few">%d épisodes</item><item quantity="other">épisodes</item></plurals>
                <string-array name="moods"><item>Calme</item></string-array>
            """,
        )
        val problems = TranslationResources.problems(res, "m").joinToString("\n")
        listOf(
            "string title: the English says \"boxlore\"",
            "string count: placeholders",
            "string key: marked translatable=\"false\"",
            "string gone: no English string",
            "plurals episodes: missing quantity \"one\"",
            "plurals episodes: quantity \"few\" isn't used in fr",
            "plurals episodes[other]: placeholders [] differ",
            "string-array moods: 1 items, the English has 2",
        ).forEach { assertTrue(it in problems, "Expected \"$it\" in:\n$problems") }
    }

    private fun fixture(vararg folders: Pair<String, String>): File {
        val res = Files.createTempDirectory("i18n-res").toFile()
        folders.forEach { (folder, body) ->
            File(res, folder).apply { mkdirs() }.resolve("strings.xml").writeText(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>${body.trimIndent()}</resources>\n",
            )
        }
        return res
    }
}
