package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The show ID parse and the namespace entry point. The shared fixture cases stay in
 * `core/rss/src/test` (EpisodeRecipeIdTest), next to the fixture the scripts copy reads.
 */
class EpisodeRecipeIdNamespaceTest {
    @Test
    fun parsesCanonicalShowIds() {
        assertEquals(1L, EpisodeRecipeId.showIdOrNull("1"))
        assertEquals(3_391_039L, EpisodeRecipeId.showIdOrNull("3391039"))
        assertEquals(EpisodeRecipeId.SHOW_ID_MAX, EpisodeRecipeId.showIdOrNull("9007199254740991"))
    }

    @Test
    fun rejectsWhatTheScriptsCopyRejects() {
        val rejected = listOf(
            "",
            "0",
            "0123",
            "+5",
            "-5",
            " 5",
            "5 ",
            "5\n",
            " 5",
            "9007199254740992",
            "9999999999999999",
            "10000000000000000",
            "99999999999999999999",
            "rss:abc123",
            "12a",
            "1.5",
            "1e3",
            "١٢", // Arabic-Indic digits
            "１", // fullwidth 1
        )
        for (value in rejected) {
            assertNull(EpisodeRecipeId.showIdOrNull(value), "accepted \"$value\"")
        }
    }

    @Test
    fun namespaceEntryPointMintsTheRecipeId() {
        val key = "https://api.spreaker.com/episode/75827097"
        assertEquals(8_825_297_254_050_678L, EpisodeRecipeId.forNamespace(3_391_039L, key))
        assertEquals(EpisodeRecipeId.recipeId(3_391_039L, key), EpisodeRecipeId.forNamespace(3_391_039L, key))
        val parsed = requireNotNull(EpisodeRecipeId.showIdOrNull("3391039"))
        assertEquals(8_825_297_254_050_678L, EpisodeRecipeId.forNamespace(parsed, key))
    }

    @Test
    fun namespaceEntryPointRefusesWhatTheRecipeRefuses() {
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.forNamespace(0, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.forNamespace(-3_391_039, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.forNamespace(EpisodeRecipeId.SHOW_ID_MAX + 1, "a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.forNamespace(75_075, " a") }
        assertThrows<IllegalArgumentException> { EpisodeRecipeId.forNamespace(75_075, "") }
    }
}
