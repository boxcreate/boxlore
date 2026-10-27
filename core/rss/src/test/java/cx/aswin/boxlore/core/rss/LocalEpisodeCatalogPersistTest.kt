package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalEpisodeCatalogPersistTest {
    private val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")
    private val show100 = NewEpisodeIds(epNs = 100L)

    @Test
    fun refreshReusesExistingEpisodeId() {
        val existing = listOf(LocalEpisodeIdentity(episodeId = "-9", guid = "g1", audioUrl = "https://cdn.example/a.mp3"))
        val rows = plan(listOf(rssEpisode(guid = "g1", title = "Renamed")), existing = existing)
        assertEquals(listOf("-9"), rows.map { it.entity.episodeId })
        assertEquals(PlannedIdSource.STORED, rows.single().source)
    }

    @Test
    fun aNewEpisodeTakesItsRecipeIdInTheShowsNamespace() {
        val rows = plan(listOf(rssEpisode(guid = "  g1\n")))

        assertEquals(EpisodeRecipeId.forNamespace(100L, "g1").toString(), rows.single().entity.episodeId)
        assertEquals("g1", rows.single().entity.guid)
        assertEquals(PlannedIdSource.RECIPE, rows.single().source)
        assertTrue(rows.single().keyFollowsContract)
    }

    @Test
    fun theStoredNamespaceDecidesNotTheShowId() {
        val rows = plan(listOf(rssEpisode(guid = "g1")), newIds = NewEpisodeIds(epNs = 555L))
        assertEquals(EpisodeRecipeId.forNamespace(555L, "g1").toString(), rows.single().entity.episodeId)
    }

    @Test
    fun aGuidlessItemWithAPlayableEnclosureTakesTheRecipeIdOfItsEnclosure() {
        val audio = "https://cdn.example/enc.mp3"
        val item = ParsedFeedItem(rssEpisode(guid = null, audioUrl = audio), documentIndex = 0, enclosureUrls = listOf(audio))
        val rows = plan(listOf(item), parsedItems = true)

        assertEquals(EpisodeRecipeId.forNamespace(100L, audio).toString(), rows.single().entity.episodeId)
        assertEquals(audio, rows.single().entity.guid)
    }

    @Test
    fun anItemOutsideTheIdentityContractKeepsAPhoneMint() {
        // No GUID and no playable enclosure: keyed on the parser's audio pick, a phone-only key.
        val item = rssEpisode(guid = null, audioUrl = "https://cdn.example/enc.mp3")
        val rows = plan(listOf(item))

        assertEquals(item.episodeId, rows.single().entity.episodeId)
        assertTrue(rows.single().entity.episodeId.toLong() < 0)
        assertEquals(PlannedIdSource.LOCAL, rows.single().source)
        assertFalse(rows.single().keyFollowsContract)
    }

    @Test
    fun aShowWithoutANamespaceKeepsPhoneMints() {
        val item = rssEpisode(guid = "g1")
        val rows = plan(listOf(item), newIds = NewEpisodeIds.NONE)

        assertEquals(item.episodeId, rows.single().entity.episodeId)
        assertEquals(PlannedIdSource.LOCAL, rows.single().source)
    }

    @Test
    fun anEvictedEpisodeKeepsItsIdThroughATrustedAlias() {
        val ids = NewEpisodeIds(epNs = 100L, aliases = listOf(alias("-9", key = "g1")))
        val rows = plan(listOf(rssEpisode(guid = "g1")), newIds = ids)

        assertEquals("-9", rows.single().entity.episodeId)
        assertEquals(PlannedIdSource.ALIAS, rows.single().source)
    }

    @Test
    fun theOwnerAliasWinsOverAnOlderTrustedAlias() {
        val ids =
            NewEpisodeIds(
                epNs = 100L,
                aliases = listOf(alias("-1", key = "g1", createdAt = 1L), alias("77", key = "g1", owner = true, createdAt = 5L)),
            )
        assertEquals("77", plan(listOf(rssEpisode(guid = "g1")), newIds = ids).single().entity.episodeId)
    }

    @Test
    fun ambiguousAndUnresolvedAliasesNeverDecideAnId() {
        val ids =
            NewEpisodeIds(
                epNs = 100L,
                aliases =
                listOf(
                    alias("-9", key = "g1", state = EpisodeAliasState.AMBIGUOUS),
                    alias("-8", key = null, state = EpisodeAliasState.UNRESOLVED),
                ),
            )
        val rows = plan(listOf(rssEpisode(guid = "g1")), newIds = ids)
        assertEquals(EpisodeRecipeId.forNamespace(100L, "g1").toString(), rows.single().entity.episodeId)
    }

    @Test
    fun anAliasIdAStoredRowHoldsIsNotTakenTwice() {
        val existing = listOf(LocalEpisodeIdentity(episodeId = "-9", guid = "other", audioUrl = "https://cdn.example/o.mp3"))
        val ids = NewEpisodeIds(epNs = 100L, aliases = listOf(alias("-9", key = "g1")))
        val rows = plan(listOf(rssEpisode(guid = "g1")), existing = existing, newIds = ids)
        assertEquals(EpisodeRecipeId.forNamespace(100L, "g1").toString(), rows.single().entity.episodeId)
    }

    @Test
    fun aGuidlessItemFindsAnEvictedRowByItsOlderAudioKey() {
        // Older builds keyed this GUID-less item on the audio the parser picked; its alias kept that key.
        val pick = "https://cdn.example/picked.mp3"
        val enclosure = "https://cdn.example/declared.mp3"
        val item = ParsedFeedItem(rssEpisode(guid = null, audioUrl = pick), documentIndex = 0, enclosureUrls = listOf(enclosure))
        val ids = NewEpisodeIds(epNs = 100L, aliases = listOf(alias("4321", key = pick)))
        val row = plan(listOf(item), newIds = ids, parsedItems = true).single()

        assertEquals("4321", row.entity.episodeId)
        assertEquals(pick, row.entity.guid, "written under the key its alias holds, like a stored row found by it")
        assertFalse(row.keyFollowsContract)
    }

    @Test
    fun anAliasRowFallsBackToTheKeysOtherAliasIdsThenItsRecipeId() {
        val ids = NewEpisodeIds(epNs = 100L, aliases = listOf(alias("-9", key = "g1", owner = true), alias("-8", key = "g1", createdAt = 5L)))
        val row = plan(listOf(rssEpisode(guid = "g1")), newIds = ids).single()

        assertEquals("-9", row.entity.episodeId)
        assertEquals(listOf("-8", EpisodeRecipeId.forNamespace(100L, "g1").toString()), row.fallbackIds)
        assertTrue(plan(listOf(rssEpisode(guid = "g2")), newIds = ids).single().fallbackIds.isEmpty(), "a recipe ID has none")
    }

    @Test
    fun aNewItemKeepsTheListenerHeldIdThatEqualsItsOwnMint() {
        // A played feed-only extra, or restored history: the item's own phone mint, held by history only.
        val item = rssEpisode(guid = "g1")
        val ids = withListeners(heldIds = setOf(item.episodeId))
        val row = plan(listOf(item), newIds = ids).single()

        assertEquals(item.episodeId, row.entity.episodeId)
        assertEquals(PlannedIdSource.OWN_MINT, row.source)
        assertEquals(listOf(EpisodeRecipeId.forNamespace(100L, "g1").toString()), row.fallbackIds, "its recipe ID if the mint is taken by then")
    }

    @Test
    fun aGuidlessItemKeepsTheMintOfTheAudioAnOlderBuildKeyedItOn() {
        val pick = "https://cdn.example/picked.mp3"
        val enclosure = "https://cdn.example/declared.mp3"
        val item = ParsedFeedItem(rssEpisode(guid = null, audioUrl = pick), documentIndex = 0, enclosureUrls = listOf(enclosure), audioUrls = listOf(pick))
        val olderMint = StickyEpisodeIdentity.ownMintedId(namespace, pick)
        val row = plan(listOf(item), newIds = withListeners(heldIds = setOf(olderMint)), parsedItems = true).single()

        assertEquals(olderMint, row.entity.episodeId)
        assertEquals(enclosure, row.entity.guid, "written under the contract key")
        assertEquals(PlannedIdSource.OWN_MINT, row.source)
    }

    @Test
    fun aHistoryOnlyIdMintedUnderTheShowsOlderAddressIsKeptAfterTheAddressChanged() {
        // No stored row holds it: only listening history does, minted when the show had another address.
        val older = RssIdGenerator.podcastId("https://old.example/feed.xml")
        val item = rssEpisode(guid = "g1")
        val olderMint = StickyEpisodeIdentity.ownMintedId(older, "g1")
        val ids = withListeners(heldIds = setOf(olderMint))

        assertEquals(PlannedIdSource.RECIPE, plan(listOf(item), newIds = ids).single().source, "without the older address nothing proves it")
        val row = plan(listOf(item), newIds = ids, olderNamespaces = listOf(older)).single()
        assertEquals(olderMint, row.entity.episodeId)
        assertEquals(PlannedIdSource.OWN_MINT, row.source)
    }

    @Test
    fun aPodcastIndexIdOnlyHistoryHoldsIsKeptWhenItsAudioMatchesOneItem() {
        val audio = "https://cdn.example/played.mp3"
        val ids = withListeners(heldIds = setOf("5001"), references = listOf(reference("5001", audio = audio)))
        val rows = plan(listOf(rssEpisode(guid = "g1", audioUrl = audio), rssEpisode(guid = "g2", audioUrl = "https://cdn.example/other.mp3")), newIds = ids)

        assertEquals("5001", rows[0].entity.episodeId)
        assertEquals(PlannedIdSource.LISTENER_MATCH, rows[0].source)
        assertEquals(EpisodeRecipeId.forNamespace(100L, "g2").toString(), rows[1].entity.episodeId)
    }

    @Test
    fun aDownloadIsMatchedByTitleAndDate() {
        val ids = withListeners(heldIds = setOf("5001"), references = listOf(reference("5001", audio = null, title = "Ep", date = 10L + 3_600L)))
        assertEquals("5001", plan(listOf(rssEpisode(guid = "g1")), newIds = ids).single().entity.episodeId)
    }

    @Test
    fun aListenerMatchThatIsNotUniqueKeepsNothing() {
        val audio = "https://cdn.example/played.mp3"
        // Two held IDs match the same item: nothing tells them apart.
        val twoIds = withListeners(heldIds = setOf("5001", "5002"), references = listOf(reference("5001", audio = audio), reference("5002", audio = audio)))
        assertEquals(PlannedIdSource.RECIPE, plan(listOf(rssEpisode(guid = "g1", audioUrl = audio)), newIds = twoIds).single().source)

        // One held ID whose audio two items share.
        val oneId = withListeners(heldIds = setOf("5001"), references = listOf(reference("5001", audio = audio)))
        val rows = plan(listOf(rssEpisode(guid = "g1", audioUrl = audio), rssEpisode(guid = "g2", audioUrl = audio)), newIds = oneId)
        assertTrue(rows.all { it.source == PlannedIdSource.RECIPE })
    }

    @Test
    fun aListenerIdThatMatchesAStoredRowIsLeftToTheReconciler() {
        val audio = "https://cdn.example/played.mp3"
        val existing = listOf(LocalEpisodeIdentity(episodeId = "-9", guid = "g1", audioUrl = audio))
        val ids = withListeners(heldIds = setOf("5001"), references = listOf(reference("5001", audio = audio)))
        val rows = plan(listOf(rssEpisode(guid = "g1", audioUrl = audio), rssEpisode(guid = "g2", audioUrl = "https://cdn.example/b.mp3")), existing = existing, newIds = ids)

        assertEquals(listOf("-9", EpisodeRecipeId.forNamespace(100L, "g2").toString()), rows.map { it.entity.episodeId })
    }

    @Test
    fun aTrustedAliasWinsOverAListenerHeldId() {
        val item = rssEpisode(guid = "g1")
        val ids =
            NewEpisodeIds(
                epNs = 100L,
                aliases = listOf(alias("-9", key = "g1")),
                hash = RecipeIdHash.RECIPE,
                listeners = ListenerEvidence(setOf(item.episodeId), emptyList()),
            )
        assertEquals("-9", plan(listOf(item), newIds = ids).single().entity.episodeId)
    }

    @Test
    fun listenerHeldIdsNeedARecipeNamespaceAndAContractKey() {
        val item = rssEpisode(guid = null, audioUrl = "https://cdn.example/enc.mp3")
        val ids = withListeners(heldIds = setOf("5001"), references = listOf(reference("5001", audio = "https://cdn.example/enc.mp3")))
        assertEquals(item.episodeId, plan(listOf(item), newIds = ids).single().entity.episodeId, "outside the contract: its phone mint")
        val none = NewEpisodeIds(epNs = null, aliases = emptyList(), hash = RecipeIdHash.RECIPE, listeners = ids.listeners)
        assertEquals(rssEpisode(guid = "g1").episodeId, plan(listOf(rssEpisode(guid = "g1")), newIds = none).single().entity.episodeId)
    }

    @Test
    fun guidChangeMintsANewRecipeIdAndDoesNotReuseTheOldRow() {
        val existing = listOf(LocalEpisodeIdentity(episodeId = "-9", guid = "g1", audioUrl = "https://cdn.example/a.mp3"))
        val rows = plan(listOf(rssEpisode(guid = "g2", audioUrl = "https://cdn.example/a.mp3")), existing = existing)

        assertEquals(EpisodeRecipeId.forNamespace(100L, "g2").toString(), rows.single().entity.episodeId)
        assertEquals("g2", rows.single().entity.guid)
    }

    @Test
    fun aForcedHashGivesTwoKeysTheSameProposedIdForTheGuardToSettle() {
        val forced = NewEpisodeIds(epNs = 100L, hash = { _, _ -> FORCED })
        val rows = plan(listOf(rssEpisode(guid = "a"), rssEpisode(guid = "b")), newIds = forced)
        assertEquals(listOf(FORCED.toString(), FORCED.toString()), rows.map { it.entity.episodeId })
    }

    @Test
    fun duplicateGuidKeepsFirstRow() {
        val rows =
            plan(
                listOf(
                    rssEpisode(guid = "dup", title = "First"),
                    rssEpisode(guid = "dup", title = "Second", audioUrl = "https://cdn.example/b.mp3"),
                ),
            )
        assertEquals(1, rows.size)
        assertEquals("First", rows.single().entity.title)
    }

    @Test
    fun skipsItemWithNoGuidOrEnclosure() {
        assertTrue(plan(listOf(rssEpisode(guid = null, audioUrl = "  ", title = "Nope"))).isEmpty())
    }

    @Test
    fun enclosureOnlyStoresEnclosureAsGuid() {
        val rows = plan(listOf(rssEpisode(guid = null, audioUrl = "https://cdn.example/enc.mp3")))
        assertEquals("https://cdn.example/enc.mp3", rows.single().entity.guid)
    }

    @Test
    fun toLocalEpisodesReturnsThePlannedRows() {
        val parsed = listOf(rssEpisode(guid = "g1")).asParsedFeedItems()
        val rows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = "100",
                rssNamespaceId = namespace,
                parsed = parsed,
                existing = emptyList(),
                newIds = show100,
                channelImageUrl = null,
                showImageUrl = null,
            )
        assertEquals(plan(listOf(rssEpisode(guid = "g1"))).map { it.entity }, rows)
    }

    private fun plan(
        items: List<Any>,
        existing: List<LocalEpisodeIdentity> = emptyList(),
        newIds: NewEpisodeIds = show100,
        parsedItems: Boolean = false,
        olderNamespaces: List<String> = emptyList(),
    ): List<PlannedEpisodeRow> = LocalEpisodeCatalogPersist.plan(
        podcastIndexId = "100",
        rssNamespaceId = namespace,
        parsed = if (parsedItems) items.map { it as ParsedFeedItem } else items.map { it as RssEpisodeEntity }.asParsedFeedItems(),
        existing = existing,
        newIds = newIds,
        channelImageUrl = null,
        showImageUrl = null,
        olderNamespaces = olderNamespaces,
    )

    private fun withListeners(heldIds: Set<String>, references: List<LocalCatalogListenerReference> = emptyList()) =
        NewEpisodeIds(epNs = 100L, aliases = emptyList(), hash = RecipeIdHash.RECIPE, listeners = ListenerEvidence(heldIds, references))

    private fun reference(id: String, audio: String?, title: String? = "Ep", date: Long? = null) = LocalCatalogListenerReference(id, audio, title, date)

    private fun alias(
        id: String,
        key: String?,
        state: String = EpisodeAliasState.TRUSTED,
        owner: Boolean = false,
        createdAt: Long = 0L,
    ) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = "100",
        catalogKey = key,
        recipeId = key?.let { EpisodeRecipeId.forNamespace(100L, it) },
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    private fun rssEpisode(guid: String? = "g1", title: String = "Ep", audioUrl: String = "https://cdn.example/a.mp3",): RssEpisodeEntity {
        val id =
            if (StickyEpisodeIdentity.catalogKey(guid, audioUrl) == null) {
                "-1"
            } else {
                RssIdGenerator.episodeIdForPodcast(
                    podcastId = namespace,
                    guid = guid,
                    enclosureUrl = audioUrl,
                    publishedDate = 10L,
                    title = title,
                )
            }
        return RssEpisodeEntity(
            episodeId = id,
            podcastId = namespace,
            guid = guid,
            title = title,
            description = "",
            audioUrl = audioUrl,
            imageUrl = null,
            duration = 60,
            publishedDate = 10L,
            chaptersUrl = null,
            transcriptUrl = null,
            transcripts = null,
            persons = null,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeType = "full",
            enclosureType = "audio/mpeg",
        )
    }

    private companion object {
        const val FORCED = 4_600_000_000_000_000L
    }
}
