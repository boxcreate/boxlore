package cx.aswin.boxlore.core.rss

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

class EpisodeRecipeIdTest {
    private val fixtureBytes: ByteArray = requireNotNull(
        javaClass.classLoader?.getResource(FIXTURE_PATH),
    ) { "Missing $FIXTURE_PATH" }.readBytes()
    private val fixture: JsonObject = JsonParser.parseString(fixtureBytes.toString(Charsets.UTF_8)).asJsonObject

    @Test
    fun fixtureIsTheSharedCopy() {
        val sha = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
        assertEquals(FIXTURE_SHA256, sha)
        assertEquals(EpisodeRecipeId.RECIPE, fixture["recipe"].asString)
        assertEquals(EpisodeRecipeId.MIN, fixture["recipeIdMin"].asLong)
        assertEquals(EpisodeRecipeId.MAX, fixture["recipeIdMax"].asLong)
    }

    @TestFactory
    fun fixtureCases(): List<DynamicTest> = fixture["cases"].asJsonArray.map { element ->
        val case = element.asJsonObject
        DynamicTest.dynamicTest(case["name"].asString) {
            val showId = case["showId"].asLong
            val guid = case.stringOrNull("guid")
            val enclosure = case.stringOrNull("enclosureUrl")
            val key = case.stringOrNull("catalogKey")
            val id = case["recipeId"].takeUnless { it.isJsonNull }?.asLong

            assertEquals(key, EpisodeRecipeId.catalogKey(guid, enclosure))
            assertEquals(id, EpisodeRecipeId.forItem(showId, guid, enclosure))
            if (id != null) {
                assertEquals(id, EpisodeRecipeId.recipeId(showId, requireNotNull(key)))
                assertTrue(EpisodeRecipeId.isRecipeId(id))
            }
        }
    }

    @Test
    fun trimsExactlyTheFixtureSet() {
        val ranges = fixture["trimRanges"].asJsonArray.map { range ->
            val bounds = range.asString.split('-').map { it.toInt(radix = 16) }
            bounds.first()..bounds.last()
        }
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            if (c.isSurrogate()) continue
            val expected = ranges.any { code in it }
            assertEquals(expected, EpisodeRecipeId.isTrimChar(c), "U+%04X".format(code))
        }
    }

    /**
     * The explicit set must match what the app's stored keys were trimmed with: Kotlin's
     * `trim()` on Android. That is the JVM's whitespace plus U+180E, which Android's libcore
     * hard-codes as whitespace (checked on an Android 14 runtime and in AOSP 10 through main).
     */
    @Test
    fun matchesKotlinTrimOnAndroid() {
        for (code in 0..0xFFFF) {
            val c = code.toChar()
            if (c.isSurrogate()) continue
            val androidWhitespace = c.isWhitespace() || c == MONGOLIAN_VOWEL_SEPARATOR
            assertEquals(androidWhitespace, EpisodeRecipeId.isTrimChar(c), "U+%04X".format(code))
        }
        assertEquals("x", EpisodeRecipeId.trimKey("\u180Ex\u180E"))
    }

    @Test
    fun refusesNonCanonicalKeysAndShows() {
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(75075, " a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(75075, "") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(0, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(-3391039, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(EpisodeRecipeId.SHOW_ID_MAX + 1, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.recipeId(Long.MAX_VALUE, "a") }
        assertTrue(EpisodeRecipeId.isRecipeId(EpisodeRecipeId.recipeId(EpisodeRecipeId.SHOW_ID_MAX, "a")))
        assertNull(EpisodeRecipeId.forItem(75075, " ", null))
    }

    @Test
    fun legacyIdsAreOutsideTheRecipeRange() {
        assertFalse(EpisodeRecipeId.isRecipeId(48_291_034_567L))
        assertFalse(EpisodeRecipeId.isRecipeId(-4_611_686_018_427_387_904L))
        assertFalse(EpisodeRecipeId.isRecipeId(EpisodeRecipeId.MIN - 1))
        assertFalse(EpisodeRecipeId.isRecipeId(EpisodeRecipeId.MAX + 1))
        assertTrue(EpisodeRecipeId.isRecipeId(8_825_297_254_050_678L))
    }

    private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    private companion object {
        const val FIXTURE_PATH = "fixtures/episode-recipe-vectors.json"
        const val MONGOLIAN_VOWEL_SEPARATOR = '\u180E'

        // Copies of the fixture in other repositories must hash to this value too.
        const val FIXTURE_SHA256 = "e272d820972c4f9d7cd215c510bfdb7effa382ceb1982617419279189a5ae292"
    }
}
