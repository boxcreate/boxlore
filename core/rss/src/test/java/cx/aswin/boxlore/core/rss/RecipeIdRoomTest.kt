package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New episodes of followed shows take recipe IDs, through the real refresh on a real Room database
 * (the DAOs, the REPLACE writes, the unique (show, key) index, the alias table). Every test checks
 * that no stored ID changes. Clashes are forced with the test-only [RecipeIdHash], because a real
 * recipe clash can't be produced on demand (docs/EPISODE_IDS.md §4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeIdRoomTest : RoomCatalogTestBase() {
    @Test
    fun aNewEpisodeOfAFollowedShowTakesItsRecipeIdWithATrustedAlias() = runBlocking {
        follow(SHOW, epNs = null)
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        val recipe = recipe(SHOW, "g1")
        assertEquals(listOf(recipe), ids(SHOW))
        val alias = requireNotNull(alias(recipe))
        assertEquals(EpisodeAliasState.TRUSTED, alias.state)
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, alias.provenance)
        assertEquals("g1", alias.catalogKey)
        assertEquals(recipe.toLong(), alias.recipeId)
        assertTrue(alias.owner)
        assertEquals("the namespace is stored where none was", SHOW.toLong(), database.localEpisodeCatalogDao().getFeed(SHOW)?.epNs)
        assertEquals(SHOW.toLong(), database.podcastDao().getEpNs(SHOW))
    }

    @Test
    fun storedRowsKeepTheirIdsWhateverTheyHold() = runBlocking {
        follow(SHOW)
        val servedHere = recipe(SHOW, "g-served")
        val servedElsewhere = "4600000000000001"
        val p4aReserved = p4aReservedId(SHOW, "g-res")
        val stored =
            listOf(
                "5001" to "g-pi",
                "-77" to "g-neg",
                servedHere to "g-served",
                servedElsewhere to "g-other",
                p4aReserved to "g-res",
            )
        database.localEpisodeCatalogDao().upsertEpisodes(stored.map { (id, key) -> row(id, SHOW, key) })
        feed.items = stored.map { Item(it.second) } + Item("g-new")

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        val first = rows(SHOW)
        assertTrue(refresh(SHOW, url = MOVED_URL) is RefreshOutcome.Success)

        for ((id, key) in stored) assertEquals("$key keeps its ID", id, rows(SHOW)[key])
        assertEquals("a new key takes its recipe ID", recipe(SHOW, "g-new"), rows(SHOW)["g-new"])
        assertEquals("nothing moves on a second read from a new address", first, rows(SHOW))
        // Rows written before aliases existed get one; a served ID that isn't its key's recipe is ambiguous.
        assertEquals(EpisodeAliasProvenance.FEED_STORED_ROW, alias("5001")?.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, alias(servedHere)?.state)
        assertEquals(EpisodeAliasState.AMBIGUOUS, alias(servedElsewhere)?.state)
        assertFalse(alias(servedElsewhere)!!.owner)
    }

    @Test
    fun aForcedSameShowCollisionReservesAnIdWithAnAliasAndIsStableAcrossRefreshesMovesAndEviction() = runBlocking {
        follow(SHOW)
        feed.items = listOf(Item("a"), Item("b"))
        val repo = repo(FORCED_HASH)

        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        val reserved = StickyEpisodeIdentity.reservedEpisodeId(SHOW, "b", 1)
        val expected = mapOf("a" to FORCED.toString(), "b" to reserved)
        assertEquals("both episodes survive", expected, rows(SHOW))
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, alias(FORCED.toString())?.provenance)
        val reservedAlias = requireNotNull(alias(reserved))
        assertEquals(EpisodeAliasProvenance.FEED_RESERVED, reservedAlias.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, reservedAlias.state)
        assertEquals("b", reservedAlias.catalogKey)

        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        assertEquals(expected, rows(SHOW))
        assertTrue(refresh(SHOW, url = MOVED_URL, repo = repo) is RefreshOutcome.Success)
        assertEquals("an address move keeps the reserved ID", expected, rows(SHOW))

        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(ids(SHOW).isEmpty())
        assertNotNull("aliases survive eviction", alias(reserved))
        feed.items = listOf(Item("b"), Item("a"))
        assertTrue(refresh(SHOW, url = MOVED_URL, repo = repo) is RefreshOutcome.Success)
        assertEquals("a re-read keeps both IDs through their aliases, whatever the order", expected, rows(SHOW))
    }

    @Test
    fun aForcedCrossShowCollisionKeepsEachShowsEpisodeEvenAfterEviction() = runBlocking {
        follow(SHOW)
        follow(OTHER_SHOW)
        feed.items = listOf(Item("x"))
        val repo = repo(FORCED_HASH)

        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        assertTrue(refresh(OTHER_SHOW, repo = repo) is RefreshOutcome.Success)
        assertEquals(mapOf("x" to FORCED.toString()), rows(SHOW))
        val reserved = StickyEpisodeIdentity.reservedEpisodeId(OTHER_SHOW, "x", 1)
        assertEquals(mapOf("x" to reserved), rows(OTHER_SHOW))
        assertEquals(OTHER_SHOW, alias(reserved)?.podcastId)

        // With the first show evicted, its alias still holds the ID: a third show can't take it.
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        follow(THIRD_SHOW)
        assertTrue(refresh(THIRD_SHOW, repo = repo) is RefreshOutcome.Success)
        assertEquals(mapOf("x" to StickyEpisodeIdentity.reservedEpisodeId(THIRD_SHOW, "x", 1)), rows(THIRD_SHOW))
        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        assertEquals(mapOf("x" to FORCED.toString()), rows(SHOW))
        assertEquals(mapOf("x" to reserved), rows(OTHER_SHOW))
    }

    @Test
    fun anEvictedCatalogIsReadBackUnderItsOldIdsThroughTheirAliases() = runBlocking {
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", SHOW, "g1"), row("5001", SHOW, "g2")))
        seedAlias("-77", SHOW, "g1")
        seedAlias("5001", SHOW, "g2")
        history("-77", SHOW)

        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertNotNull(alias("-77"))
        feed.items = listOf(Item("g1"), Item("g2"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to "-77", "g2" to "5001"), rows(SHOW))
        assertNotNull("listener state stays on its ID", database.listeningHistoryDao().getHistoryItem("-77"))
    }

    @Test
    fun aChangedGuidIsANewEpisodeAndTheOldRowAndItsHistoryStayApart() = runBlocking {
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", SHOW, "g1", audio = SAME_AUDIO, title = "Same title")))
        seedAlias("-77", SHOW, "g1")
        history("-77", SHOW, audio = SAME_AUDIO, title = "Same title")
        feed.items = listOf(Item("g2", title = "Same title", audio = SAME_AUDIO))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        val newId = recipe(SHOW, "g2")
        assertEquals(mapOf("g1" to "-77", "g2" to newId), rows(SHOW))
        assertNotNull("the old episode keeps its history", database.listeningHistoryDao().getHistoryItem("-77"))
        assertNull("nothing moves to the new episode", database.listeningHistoryDao().getHistoryItem(newId))
        assertEquals(listOf(newId), database.episodeIdentityDao().aliasesForKey(SHOW, "g2").map { it.aliasId })
        assertEquals(listOf("-77"), database.episodeIdentityDao().aliasesForKey(SHOW, "g1").map { it.aliasId })
    }

    @Test
    fun aServerNamespaceThatDiffersChangesNoIdAndNoStoredNamespace() = runBlocking {
        follow(SHOW, epNs = 555L)
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW, serverEpNs = 999L) is RefreshOutcome.Success)
        assertEquals("the stored namespace decides", mapOf("g1" to EpisodeRecipeId.forNamespace(555L, "g1").toString()), rows(SHOW))
        val first = rows(SHOW)

        feed.items = listOf(Item("g1"), Item("g2"))
        assertTrue(refresh(SHOW, serverEpNs = 999L) is RefreshOutcome.Success)
        assertEquals(first["g1"], rows(SHOW)["g1"])
        assertEquals(EpisodeRecipeId.forNamespace(555L, "g2").toString(), rows(SHOW)["g2"])
        assertEquals(555L, database.podcastDao().getEpNs(SHOW))
        assertEquals(555L, database.localEpisodeCatalogDao().getFeed(SHOW)?.epNs)
    }

    @Test
    fun showsWithoutARecipeNamespaceNeverGetRecipeIds() = runBlocking {
        feed.items = listOf(Item("g1"))
        val rss = refresh("rss:" + "a".repeat(64))
        assertTrue(rss is RefreshOutcome.Failure)
        assertTrue(database.localEpisodeCatalogDao().listIdentities("rss:" + "a".repeat(64)).isEmpty())

        // A show ID that isn't canonical can't take recipe IDs either: it keeps phone mints, no alias.
        assertTrue(refresh("0100") is RefreshOutcome.Success)
        val id = ids("0100").single()
        assertTrue(id.toLong() < 0L)
        assertNull(alias(id))
    }

    @Test
    fun aGuidlessItemWithNoPlayableEnclosureKeepsAPhoneMintWithoutAnAlias() = runBlocking {
        // No GUID and no playable <enclosure> (media:content only): keyed on the parser's audio pick,
        // a phone-only key outside the identity contract.
        follow(SHOW)
        feed.items = listOf(Item(guid = null, audio = "https://cdn.example/no-guid.mp3", enclosure = null))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        val id = ids(SHOW).single()
        assertTrue(id.toLong() < 0L)
        assertNull(alias(id))
    }

    @Test
    fun aTakenAliasIdFallsBackToTheRecipeIdWhichNeverTakesOwnership() = runBlocking {
        // The evicted row's ID now belongs to another show's catalog row: the item takes its recipe
        // ID (free), not a reserved one, and the old ID (which may hold listener state) stays the owner.
        follow(SHOW)
        seedAlias("-77", SHOW, "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", OTHER_SHOW, "other")))
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to recipe(SHOW, "g1")), rows(SHOW))
        assertEquals("other", database.localEpisodeCatalogDao().getEpisode("-77")?.guid)
        assertTrue(alias("-77")!!.owner)
        val recipeAlias = requireNotNull(alias(recipe(SHOW, "g1")))
        assertFalse(recipeAlias.owner)
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, recipeAlias.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, recipeAlias.state)
    }

    @Test
    fun aTakenAliasIdWhoseRecipeIdIsTakenTooGetsAReservedId() = runBlocking {
        follow(SHOW)
        seedAlias("-77", SHOW, "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", OTHER_SHOW, "other")))
        feed.items = listOf(Item("g1"))
        val repo = repo(FORCED_HASH)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row(FORCED.toString(), THIRD_SHOW, "third")))

        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)

        val reserved = StickyEpisodeIdentity.reservedEpisodeId(SHOW, "g1", 1)
        assertEquals(mapOf("g1" to reserved), rows(SHOW))
        assertFalse(alias(reserved)!!.owner)
        assertEquals(EpisodeAliasProvenance.FEED_RESERVED, alias(reserved)!!.provenance)
    }

    @Test
    fun forcedClashesKeepDownloadsAndQueueEntriesOfBothEpisodes() = runBlocking {
        follow(SHOW)
        follow(OTHER_SHOW)
        feed.items = listOf(Item("a"), Item("b"))
        val repo = repo(FORCED_HASH)
        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        feed.items = listOf(Item("x"))
        assertTrue(refresh(OTHER_SHOW, repo = repo) is RefreshOutcome.Success)
        val sameShow = rows(SHOW)
        val crossShow = rows(OTHER_SHOW)
        val ids = mapOf("a" to sameShow.getValue("a"), "b" to sameShow.getValue("b"), "x" to crossShow.getValue("x"))
        assertEquals(3, ids.values.toSet().size)
        for ((key, id) in ids) {
            val show = if (key == "x") OTHER_SHOW else SHOW
            completedDownload(id, show, title = "Ep $key")
            queue(id, show, audio = "https://cdn.example/$key.mp3", title = "Ep $key")
        }
        val downloads = ids.values.associateWith { database.downloadedEpisodeDao().getDownload(it) }
        val queued = database.queueDao().getAllQueueItemsSync().sortedBy { it.episodeId }

        feed.items = listOf(Item("a"), Item("b"))
        assertTrue(refresh(SHOW, repo = repo) is RefreshOutcome.Success)
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        feed.items = listOf(Item("b"), Item("a"))
        assertTrue(refresh(SHOW, url = MOVED_URL, repo = repo) is RefreshOutcome.Success)
        feed.items = listOf(Item("x"))
        assertTrue(refresh(OTHER_SHOW, repo = repo) is RefreshOutcome.Success)

        assertEquals(sameShow, rows(SHOW))
        assertEquals(crossShow, rows(OTHER_SHOW))
        for ((id, download) in downloads) {
            assertEquals("download $id survives unchanged", download, database.downloadedEpisodeDao().getDownload(id))
        }
        assertEquals("queue entries survive unchanged", queued, database.queueDao().getAllQueueItemsSync().sortedBy { it.episodeId })
    }

    @Test
    fun aGuidlessItemWithAPlayableEnclosureTakesTheRecipeIdOfThatEnclosure() = runBlocking {
        follow(SHOW)
        val enclosure = "https://cdn.example/declared.mp3"
        feed.items = listOf(Item(guid = null, audio = "https://cdn.example/picked.mp3", enclosure = enclosure))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf(enclosure to recipe(SHOW, enclosure)), rows(SHOW))
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, alias(recipe(SHOW, enclosure))?.provenance)
    }
}
