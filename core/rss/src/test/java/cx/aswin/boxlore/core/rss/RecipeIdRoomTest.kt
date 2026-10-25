package cx.aswin.boxlore.core.rss

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
class RecipeIdRoomTest {
    private lateinit var database: BoxLoreDatabase
    private val feed = ScriptedFeedClient()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

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
    fun anOrphanWithNoRowAndNoAliasIsStillRematchedToItsEpisode() = runBlocking {
        // A restored history row whose catalog row never came back: the existing rematch still finds it.
        follow(SHOW)
        history("-55", SHOW, audio = SAME_AUDIO, title = "Same title")
        feed.items = listOf(Item("g9", title = "Same title", audio = SAME_AUDIO))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertNull(database.listeningHistoryDao().getHistoryItem("-55"))
        assertNotNull(database.listeningHistoryDao().getHistoryItem(recipe(SHOW, "g9")))
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
    fun anItemOutsideTheIdentityContractKeepsAPhoneMintWithoutAnAlias() = runBlocking {
        follow(SHOW)
        feed.items = listOf(Item(guid = null, audio = "https://cdn.example/no-guid.mp3"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        val id = ids(SHOW).single()
        assertTrue(id.toLong() < 0L)
        assertNull(alias(id))
    }

    @Test
    fun aHealedLegacyRowKeepsItsIdAndItsUnresolvedAliasBecomesTrusted() = runBlocking {
        follow(SHOW)
        val namespace = RssIdGenerator.podcastId(feedUrl(SHOW))
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row(minted, SHOW, "id:$minted")))
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(EpisodeAliasEntity(minted, SHOW, null, null, EpisodeAliasState.UNRESOLVED, false, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY, 1L)),
        )
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to minted), rows(SHOW))
        val alias = requireNotNull(alias(minted))
        assertEquals(EpisodeAliasState.TRUSTED, alias.state)
        assertEquals(EpisodeAliasProvenance.HEAL_SYNTHETIC_KEY, alias.provenance)
        assertEquals("g1", alias.catalogKey)
        assertEquals(EpisodeRecipeId.forNamespace(SHOW.toLong(), "g1"), alias.recipeId)
        assertTrue(alias.owner)
        assertEquals(1L, alias.createdAt)
    }

    @Test
    fun aReservedAliasNeverTakesOwnershipFromATrustedOwner() = runBlocking {
        // The evicted row's ID now belongs to another show's catalog row: the item takes a reserved
        // ID, and the old ID (which may hold listener state) stays the owner.
        follow(SHOW)
        seedAlias("-77", SHOW, "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", OTHER_SHOW, "other")))
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        val reserved = StickyEpisodeIdentity.reservedEpisodeId(SHOW, "g1", 1)
        assertEquals(mapOf("g1" to reserved), rows(SHOW))
        assertEquals("other", database.localEpisodeCatalogDao().getEpisode("-77")?.guid)
        assertTrue(alias("-77")!!.owner)
        assertFalse(alias(reserved)!!.owner)
        assertNotEquals(EpisodeAliasState.AMBIGUOUS, alias(reserved)!!.state)
    }

    private suspend fun refresh(
        show: String,
        url: String = feedUrl(show),
        serverEpNs: Long? = null,
        repo: LocalEpisodeCatalogRepository = repo(RecipeIdHash.RECIPE),
    ): RefreshOutcome = repo.refresh(
        LocalEpisodeCatalogPort.RefreshRequest(
            podcastIndexId = show,
            feedUrl = url,
            reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
            serverEpNs = serverEpNs,
        ),
    )

    private fun repo(hash: RecipeIdHash) = LocalEpisodeCatalogRepository(database, feed, DownloadCacheRelinker { _, _ -> false }, {}, hash)

    private suspend fun follow(show: String, epNs: Long? = show.toLong()) {
        database.podcastDao().upsert(PodcastEntity(podcastId = show, title = "Show $show", author = "", imageUrl = "", description = null, isSubscribed = true, epNs = epNs))
    }

    private suspend fun seedAlias(id: String, show: String, key: String) {
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(
                EpisodeAliasEntity(
                    aliasId = id,
                    podcastId = show,
                    catalogKey = key,
                    recipeId = EpisodeRecipeId.forNamespace(show.toLong(), key),
                    state = EpisodeAliasState.TRUSTED,
                    owner = true,
                    provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
                    createdAt = 1L,
                ),
            ),
        )
    }

    private suspend fun history(id: String, show: String, audio: String = "https://cdn.example/$id.mp3", title: String = "Ep $id") {
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = id,
                podcastId = show,
                episodeTitle = title,
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = audio,
                podcastName = "Show",
                progressMs = 5_000L,
                durationMs = 60_000L,
                isCompleted = false,
                isLiked = true,
                lastPlayedAt = 1_000L,
            ),
        )
    }

    private suspend fun alias(id: String): EpisodeAliasEntity? = database.episodeIdentityDao().getAlias(id)

    private suspend fun ids(show: String): List<String> = database.localEpisodeCatalogDao().listIdentities(show).map { it.episodeId }

    /** Catalog key → stored ID for one show. */
    private suspend fun rows(show: String): Map<String, String> = database.localEpisodeCatalogDao().listIdentities(show).associate { it.guid to it.episodeId }

    private fun recipe(show: String, key: String): String = EpisodeRecipeId.forNamespace(show.toLong(), key).toString()

    /** The reserved ID P4a minted under the feed address (development installs only). */
    private fun p4aReservedId(show: String, key: String): String = RssIdGenerator.episodeIdForPodcast(
        podcastId = RssIdGenerator.podcastId(feedUrl(show)),
        guid = "$key\u0000reserved:$show:1",
        enclosureUrl = null,
        publishedDate = 0L,
        title = "",
    )

    private fun row(id: String, show: String, key: String, audio: String = "https://cdn.example/$key.mp3", title: String = "Ep $key") = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = title,
        description = "",
        audioUrl = audio,
        imageUrl = null,
        duration = 60,
        publishedDate = 10L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private data class Item(val guid: String?, val title: String = "Ep $guid", val audio: String = "https://cdn.example/$guid.mp3")

    /** Serves [items] as the feed at every address, parsed like a feed with the item order given. */
    private class ScriptedFeedClient : RssFeedClient() {
        var items: List<Item> = emptyList()

        override suspend fun fetch(url: String) = RssFetchResult(finalUrl = url, etag = null, lastModified = null, body = ByteArray(1))

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = ParsedRssFeed(
            title = "Show",
            author = "",
            description = null,
            imageUrl = null,
            genre = null,
            podcastType = "episodic",
            podcastGuid = null,
            declaredUpdatedAt = null,
            episodes =
            items.map { item ->
                RssEpisodeEntity(
                    episodeId = RssIdGenerator.episodeIdForPodcast(podcastId, item.guid, item.audio, 10L, item.title),
                    podcastId = podcastId,
                    guid = item.guid,
                    title = item.title,
                    description = "",
                    audioUrl = item.audio,
                    imageUrl = null,
                    duration = 60,
                    publishedDate = 10L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = "audio/mpeg",
                )
            },
        )
    }

    private companion object {
        const val SHOW = "920666"
        const val OTHER_SHOW = "920667"
        const val THIRD_SHOW = "920668"
        const val FORCED = 4_600_000_000_000_000L
        const val SAME_AUDIO = "https://cdn.example/same.mp3"
        const val MOVED_URL = "https://moved.example/feed.xml"
        val FORCED_HASH = RecipeIdHash { _, _ -> FORCED }

        fun feedUrl(show: String) = "https://feeds.example/$show.xml"
    }
}
