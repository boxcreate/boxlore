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
                piMatchId = "99",
                rssNamespaceId = namespace,
                guid = null,
                enclosureUrl = "",
                publishedDate = 1L,
                title = "Nope",
            ),
        )
    }

    @Test
    fun i4_firstInsertKeepsPositivePiId() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                piMatchId = "1258562",
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 1L,
                title = "Ep",
            )
        assertEquals("1258562", id)
    }

    @Test
    fun i5_elseMintsNegativeOnce() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = null,
                piMatchId = null,
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
                piMatchId = null,
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
                piMatchId = "1258562",
                rssNamespaceId = namespace,
                guid = "g",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 2L,
                title = "Renamed",
            )
        assertEquals(minted, id)
    }

    @Test
    fun i7_neverUpgradeNegativeToPi() {
        val id =
            StickyEpisodeIdentity.assignEpisodeId(
                existingId = "-42",
                piMatchId = "7",
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
    fun i10_seasonIsNotPartOfCatalogKey() {
        val key = StickyEpisodeIdentity.catalogKey("guid-1", "https://cdn.example/a.mp3")
        assertEquals("guid-1", key)
    }
}
