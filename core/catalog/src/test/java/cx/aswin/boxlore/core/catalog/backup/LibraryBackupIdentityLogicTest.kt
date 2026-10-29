package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import cx.aswin.boxlore.core.database.PodcastEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The pure rules of the backup identity block and the OPML address (docs/EPISODE_IDS.md §4–§5). */
class LibraryBackupIdentityLogicTest {
    @Test
    fun `an alias row survives the backup form unchanged`() {
        val alias = alias("-1", owner = true)

        assertEquals(alias, LibraryBackupIdentityLogic.toEntity(LibraryBackupIdentityLogic.toBackup(alias)))
    }

    @Test
    fun `unresolved and ambiguous aliases keep their state and never own`() {
        val unresolved = LibraryBackupIdentityLogic.toEntity(backup("-2", key = null, state = EpisodeAliasState.UNRESOLVED, owner = true))!!
        val ambiguous = LibraryBackupIdentityLogic.toEntity(backup("-3", state = EpisodeAliasState.AMBIGUOUS, owner = true))!!

        assertEquals(EpisodeAliasState.UNRESOLVED, unresolved.state)
        assertNull(unresolved.catalogKey)
        assertFalse(unresolved.owner)
        assertEquals(EpisodeAliasState.AMBIGUOUS, ambiguous.state)
        assertFalse(ambiguous.owner)
    }

    @Test
    fun `a trusted alias without a key is restored unresolved`() {
        val restored = LibraryBackupIdentityLogic.toEntity(backup("-4", key = "  ", owner = true, recipe = RECIPE))!!

        assertEquals(EpisodeAliasState.UNRESOLVED, restored.state)
        assertNull(restored.catalogKey)
        assertNull(restored.recipeId)
        assertFalse(restored.owner)
    }

    @Test
    fun `unreadable alias rows are skipped and out-of-range recipe IDs dropped`() {
        assertNull(LibraryBackupIdentityLogic.toEntity(backup(id = " ")))
        assertNull(LibraryBackupIdentityLogic.toEntity(backup("-5").copy(podcastId = null)))
        assertNull(LibraryBackupIdentityLogic.toEntity(backup("-5").copy(provenance = null)))
        assertNull(LibraryBackupIdentityLogic.toEntity(backup("-5", state = "maybe")))
        assertNull(LibraryBackupIdentityLogic.toEntity(backup("-5", recipe = 42L))!!.recipeId)
        assertEquals("guid-a", LibraryBackupIdentityLogic.toEntity(backup("-5", key = "guid-a\n"))!!.catalogKey)
    }

    @Test
    fun `the phone's alias is kept and one naming another episode refuses the backup's`() {
        val restored = alias("-1")

        assertEquals(AliasRestoreDecision.INSERT, LibraryBackupIdentityLogic.decide(restored, null, null))
        assertEquals(AliasRestoreDecision.KEEP_PHONE, LibraryBackupIdentityLogic.decide(restored, alias("-1", owner = true), null))
        assertEquals(AliasRestoreDecision.KEEP_PHONE, LibraryBackupIdentityLogic.decide(restored, alias("-1", key = null), null))
        assertEquals(AliasRestoreDecision.REFUSE, LibraryBackupIdentityLogic.decide(restored, alias("-1", key = "guid-b"), null))
        assertEquals(AliasRestoreDecision.REFUSE, LibraryBackupIdentityLogic.decide(restored, alias("-1").copy(podcastId = "200"), null))
        assertEquals(AliasRestoreDecision.REFUSE, LibraryBackupIdentityLogic.decide(alias("-1", key = null), alias("-1").copy(podcastId = "200"), null))
    }

    @Test
    fun `a catalog row holding the ID refuses only another episode's alias`() {
        val restored = alias("-1")

        assertEquals(AliasRestoreDecision.INSERT, LibraryBackupIdentityLogic.decide(restored, null, LocalEpisodeOwner("-1", SHOW, " guid-a")))
        assertEquals(AliasRestoreDecision.INSERT, LibraryBackupIdentityLogic.decide(restored, null, LocalEpisodeOwner("-1", SHOW, "id:-1")))
        assertEquals(AliasRestoreDecision.REFUSE, LibraryBackupIdentityLogic.decide(restored, null, LocalEpisodeOwner("-1", SHOW, "guid-b")))
        assertEquals(AliasRestoreDecision.REFUSE, LibraryBackupIdentityLogic.decide(restored, null, LocalEpisodeOwner("-1", "200", "guid-a")))
    }

    @Test
    fun `group settings merge by the latest like and completion decision`() {
        val phone = EpisodeGroupStateEntity(SHOW, "guid-a", likedAt = 10L, likeClearedAt = 0L, completedAt = 0L, completeClearedAt = 30L, version = 4L)
        val restored = EpisodeGroupStateEntity(SHOW, "guid-a", likedAt = 5L, likeClearedAt = 20L, completedAt = 25L, completeClearedAt = 0L, version = 7L)

        val merged = LibraryBackupIdentityLogic.mergedGroupState(phone, restored)

        assertEquals(EpisodeGroupStateEntity(SHOW, "guid-a", 10L, 20L, 25L, 30L, 8L), merged)
        assertEquals(restored, LibraryBackupIdentityLogic.mergedGroupState(null, restored))
        assertNull(LibraryBackupIdentityLogic.toEntity(EpisodeGroupStateBackup(podcastId = SHOW, catalogKey = " ")))
    }

    @Test
    fun `renumbered shows the phone already records are kept`() {
        val phone = mapOf("100" to "200", "300" to "400")
        val restored = mapOf("100" to "999", "400" to "300", "500" to "600", "rss:x" to "700", "800" to "800")

        assertEquals(listOf("500" to "600"), LibraryBackupIdentityLogic.renumberingsToRecord(phone, restored))
        assertTrue(LibraryBackupIdentityLogic.renumberingsToRecord(phone, null).isEmpty())
    }

    @Test
    fun `opml uses the publisher address and never a boxlore address`() {
        fun show(feedUrl: String?, transportUrl: String? = null) =
            PodcastEntity(podcastId = SHOW, title = "Show", author = "Host", imageUrl = "", description = null, feedUrl = feedUrl, transportUrl = transportUrl)

        assertEquals("https://feeds.example.com/show.xml", LibraryBackupOpml.publisherFeedUrl(show(" https://feeds.example.com/show.xml "), API))
        assertEquals("http://feeds.example.com/show.xml", LibraryBackupOpml.publisherFeedUrl(show("http://feeds.example.com/show.xml"), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show(null), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show("$API/episodes?id=$SHOW"), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show("$API/v2/feed/$SHOW"), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show("https://mirror.example/v2/feed/$SHOW"), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show("https://t.example/feed", transportUrl = "https://t.example/feed"), API))
        assertNull(LibraryBackupOpml.publisherFeedUrl(show("feed://feeds.example.com/show.xml"), API))
    }

    private fun alias(id: String, key: String? = "guid-a", owner: Boolean = false) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = if (key == null) null else RECIPE,
        state = if (key == null) EpisodeAliasState.UNRESOLVED else EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = 7L,
    )

    private fun backup(
        id: String,
        key: String? = "guid-a",
        state: String = EpisodeAliasState.TRUSTED,
        owner: Boolean = false,
        recipe: Long? = RECIPE,
    ) = EpisodeAliasBackup(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = recipe,
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.CLOUD_SYNC,
        createdAt = 7L,
    )

    private companion object {
        const val SHOW = "100"
        const val RECIPE = 4_503_599_627_370_500L
        const val API = "https://api.boxlore.test"
    }
}
