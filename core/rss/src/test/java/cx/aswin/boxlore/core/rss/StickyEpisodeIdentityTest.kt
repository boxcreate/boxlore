package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StickyEpisodeIdentityTest {
    private val namespace = "rss:" + "a".repeat(64)

    @Test
    fun i1_guidIsPreferredCatalogKey() {
        assertEquals(
            "guid-1",
            StickyEpisodeIdentity.catalogKey("guid-1", "https://cdn.example/a.mp3"),
        )
    }

    @Test
    fun i2_enclosureBecomesKeyWhenGuidBlank() {
        assertEquals(
            "https://cdn.example/a.mp3",
            StickyEpisodeIdentity.catalogKey("  ", "https://cdn.example/a.mp3"),
        )
    }

    @Test
    fun i3_skipWhenNeitherGuidNorEnclosure() {
        assertNull(StickyEpisodeIdentity.requireCatalogKey(null, "  "))
        assertNull(
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                recipeId = RECIPE,
                rssNamespaceId = namespace,
                guid = null,
                enclosureUrl = "",
                publishedDate = 1L,
                title = "Nope",
            ),
        )
    }

    @Test
    fun i4_aNewItemTakesTheRecipeIdTheCallerComputed() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                recipeId = RECIPE,
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 1L,
                title = "Ep",
            )
        assertEquals(RECIPE.toString(), id)
    }

    @Test
    fun i5_elseMintsNegativeOnce() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                recipeId = null,
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 1L,
                title = "Ep",
            )
        assertTrue(id!!.toLong() < 0L)
        val again =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                recipeId = null,
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 1L,
                title = "Ep",
            )
        assertEquals(id, again)
    }

    @Test
    fun i6_existingIdNeverChangesOnRefresh() {
        val minted = "-99"
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = minted,
                recipeId = RECIPE,
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 2L,
                title = "Renamed",
            )
        assertEquals(minted, id)
    }

    @Test
    fun i7_neverUpgradeAStoredIdToARecipeId() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = "-42",
                recipeId = RECIPE,
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 1L,
                title = "Ep",
            )
        assertEquals("-42", id)
    }

    @Test
    fun i8_guidChangeDoesNotStealOldId() {
        val oldKey = StickyEpisodeIdentity.catalogKey("old-guid", "https://cdn.example/a.mp3")
        val newKey = StickyEpisodeIdentity.catalogKey("new-guid", "https://cdn.example/a.mp3")
        assertNotEquals(oldKey, newKey)
    }

    @Test
    fun i9_duplicateGuidIsFirstWins() {
        val seen = mutableSetOf<String>()
        assertTrue(StickyEpisodeIdentity.firstWinsExisting(seen, "g"))
        assertTrue(!StickyEpisodeIdentity.firstWinsExisting(seen, "g"))
    }

    @Test
    fun catalogKeyTrimsLikeAndroidOnEveryRuntime() {
        // Android's trim() removes U+180E; the desktop JVM's doesn't. Keys must match the device.
        assertEquals("guid-1", StickyEpisodeIdentity.catalogKey("\u180Eguid-1\u00A0", null))
        assertEquals("\uFEFFguid-1", StickyEpisodeIdentity.catalogKey("\uFEFFguid-1", null))
        assertNull(StickyEpisodeIdentity.catalogKey("\u180E\u3000", " "))
    }

    @Test
    fun reservedIdsAreNegativeStableAndInTheShowsNamespace() {
        val first = StickyEpisodeIdentity.reservedEpisodeId("100", "guid-1", 1)
        assertTrue(first.toLong() < 0L)
        assertEquals(first, StickyEpisodeIdentity.reservedEpisodeId("100", "guid-1", 1))
        assertNotEquals(first, StickyEpisodeIdentity.reservedEpisodeId("100", "guid-1", 2), "each attempt is another ID")
        assertNotEquals(first, StickyEpisodeIdentity.reservedEpisodeId("200", "guid-1", 1), "each show is its own namespace")
        assertNotEquals(first, StickyEpisodeIdentity.reservedEpisodeId("100", "guid-2", 1))
    }

    @Test
    fun i10_seasonIsNotPartOfCatalogKey() {
        val key = StickyEpisodeIdentity.catalogKey("guid-1", "https://cdn.example/a.mp3")
        assertEquals("guid-1", key)
    }

    private companion object {
        const val RECIPE = 4_503_599_627_370_500L
    }
}
