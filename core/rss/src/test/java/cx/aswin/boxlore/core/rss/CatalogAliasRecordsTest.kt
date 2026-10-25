package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.LocalEpisodeCollisionGuard.KeyRepair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every rule of [CatalogAliasRecords]: which alias a written catalog row gets, and how an existing one changes. */
class CatalogAliasRecordsTest {
    @Test
    fun nothingIsRecordedWithoutANamespaceOrForAShowIdThatIsNotCanonical() {
        val planned = listOf(plan(recipe("g1"), "g1", PlannedIdSource.RECIPE))
        assertTrue(writes(planned, newIds = NewEpisodeIds.NONE).isEmpty)
        assertTrue(writes(planned, podcastId = "0100").isEmpty)
    }

    @Test
    fun aNewRecipeIdGetsATrustedOwnerAlias() {
        val alias = writes(listOf(plan(recipe("g1"), "g1", PlannedIdSource.RECIPE))).inserts.single()

        assertEquals(recipe("g1"), alias.aliasId)
        assertEquals("g1", alias.catalogKey)
        assertEquals(recipe("g1").toLong(), alias.recipeId)
        assertEquals(EpisodeAliasState.TRUSTED, alias.state)
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, alias.provenance)
        assertTrue(alias.owner)
        assertEquals(NOW, alias.createdAt)
    }

    @Test
    fun aReservedIdIsRecordedAndNeverTakesOwnershipFromATrustedOwner() {
        val planned = listOf(plan(recipe("g1"), "g1", PlannedIdSource.RECIPE))
        val owner = alias("-77", key = "g1", owner = true)
        val result = writes(planned, written = listOf(row("-5", "g1")), reserved = setOf("-5"), showAliases = listOf(owner))

        val alias = result.inserts.single()
        assertEquals(EpisodeAliasProvenance.FEED_RESERVED, alias.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, alias.state)
        assertFalse(alias.owner)
    }

    @Test
    fun aStoredRowWithoutAnAliasGetsOneAndAServedIdOfAnotherKeyIsAmbiguous() {
        val served = "4600000000000001"
        val planned = listOf(plan("5001", "g1", PlannedIdSource.STORED), plan(served, "g2", PlannedIdSource.STORED))
        val inserts = writes(planned).inserts.associateBy { it.aliasId }

        assertEquals(EpisodeAliasProvenance.FEED_STORED_ROW, inserts.getValue("5001").provenance)
        assertEquals(EpisodeAliasState.TRUSTED, inserts.getValue("5001").state)
        assertTrue(inserts.getValue("5001").owner)
        assertEquals(EpisodeAliasState.AMBIGUOUS, inserts.getValue(served).state)
        assertFalse(inserts.getValue(served).owner)
    }

    @Test
    fun theStoredRowProvenanceCanBeTheCallers() {
        val alias = writes(listOf(plan("-11", "g1", PlannedIdSource.STORED)), storedRowProvenance = EpisodeAliasProvenance.RSS_FOLLOW_UPGRADE).inserts.single()
        assertEquals(EpisodeAliasProvenance.RSS_FOLLOW_UPGRADE, alias.provenance)
    }

    @Test
    fun aStoredRowUnderItsContractKeySettlesItsUnresolvedAlias() {
        // A GUID-less row keyed on its own audio address, seeded unresolved by the v39 upgrade.
        val seeded = alias("-11", key = null, state = EpisodeAliasState.UNRESOLVED, provenance = EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY)
        val update = writes(listOf(plan("-11", AUDIO, PlannedIdSource.STORED)), byId = listOf(seeded)).updates.single()

        assertEquals(EpisodeAliasState.TRUSTED, update.state)
        assertEquals(AUDIO, update.catalogKey)
        assertEquals(EpisodeRecipeId.forNamespace(SHOW_ID, AUDIO), update.recipeId)
        assertEquals(EpisodeAliasProvenance.FEED_CONFIRMED_KEY, update.provenance)
        assertTrue(update.owner)
        assertEquals(1L, update.createdAt, "the creation time is kept")
    }

    @Test
    fun aKeptListenerHeldIdSettlesOrAddsItsAlias() {
        val seeded = alias("-11", key = null, state = EpisodeAliasState.UNRESOLVED, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        val ownMint = writes(listOf(plan("-11", "g1", PlannedIdSource.OWN_MINT)), byId = listOf(seeded)).updates.single()
        assertEquals(EpisodeAliasProvenance.FEED_OWN_MINT, ownMint.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, ownMint.state)
        assertEquals("g1", ownMint.catalogKey)

        val matched = writes(listOf(plan("5001", "g1", PlannedIdSource.LISTENER_MATCH))).inserts.single()
        assertEquals(EpisodeAliasProvenance.FEED_LISTENER_MATCH, matched.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, matched.state)
        assertTrue(matched.owner)
    }

    @Test
    fun aKeptServedIdOfAnotherKeyIsAmbiguousAndNeverOwns() {
        val served = "4600000000000001"
        val seeded = alias(served, key = null, state = EpisodeAliasState.UNRESOLVED, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        val settled = writes(listOf(plan(served, "g1", PlannedIdSource.LISTENER_MATCH)), byId = listOf(seeded)).updates.single()
        assertEquals(EpisodeAliasState.AMBIGUOUS, settled.state)
        assertEquals("g1", settled.catalogKey)
        assertFalse(settled.owner)

        val added = writes(listOf(plan(served, "g1", PlannedIdSource.LISTENER_MATCH))).inserts.single()
        assertEquals(EpisodeAliasState.AMBIGUOUS, added.state)
        assertFalse(added.owner)
    }

    @Test
    fun aRecipeIdAnUnresolvedAliasHeldIsSettledAsARecipeAlias() {
        // History held this served ID before the catalog had a row for it.
        val seeded = alias(recipe("g1"), key = null, state = EpisodeAliasState.UNRESOLVED, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        val update = writes(listOf(plan(recipe("g1"), "g1", PlannedIdSource.RECIPE)), byId = listOf(seeded)).updates.single()
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, update.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, update.state)
    }

    @Test
    fun aReusedIdTheGuardReplacedWithTheRecipeIdIsARecipeAlias() {
        val planned = listOf(plan("-77", "g1", PlannedIdSource.ALIAS))
        val alias = writes(planned, written = listOf(row(recipe("g1"), "g1")), showAliases = listOf(alias("-77", key = "g1", owner = true))).inserts.single()
        assertEquals(recipe("g1"), alias.aliasId)
        assertEquals(EpisodeAliasProvenance.FEED_RECIPE, alias.provenance)
        assertFalse(alias.owner, "the evicted ID keeps the episode")
    }

    @Test
    fun trustedAmbiguousAndOtherShowsAliasesStayAsTheyAre() {
        val planned = listOf(plan("-1", "g1", PlannedIdSource.STORED), plan("-2", "g2", PlannedIdSource.STORED), plan("-3", "g3", PlannedIdSource.STORED))
        val byId =
            listOf(
                alias("-1", key = "g1"),
                alias("-2", key = "g2", state = EpisodeAliasState.AMBIGUOUS),
                alias("-3", key = null, state = EpisodeAliasState.UNRESOLVED, show = "200"),
            )
        assertTrue(writes(planned, byId = byId).isEmpty)
    }

    @Test
    fun rowsOutsideTheContractGetNoAlias() {
        assertTrue(writes(listOf(plan("-1", "https://cdn.example/pick.mp3", PlannedIdSource.LOCAL, contract = false))).isEmpty)
    }

    @Test
    fun aRetrimmedHealIsRecordedOnTheTrustedAliasTheUpgradeSeeded() {
        val seeded = alias("-11", key = "g1", owner = true, provenance = EpisodeAliasProvenance.UPGRADE_CATALOG)
        val heal = LocalEpisodeCollisionGuard.RepairedKey("-11", SHOW, "g1\n", "g1", KeyRepair.RETRIMMED)
        val update = writes(listOf(plan("-11", "g1", PlannedIdSource.STORED)), byId = listOf(seeded), repaired = listOf(heal)).updates.single()

        assertEquals(seeded.copy(provenance = EpisodeAliasProvenance.HEAL_RETRIMMED_KEY), update, "state, owner, key and time are kept")
    }

    @Test
    fun aHealWithoutAnAliasAddsOneAndAnUnresolvedOneBecomesTrusted() {
        val retrimmed = LocalEpisodeCollisionGuard.RepairedKey("-11", SHOW, "g1\n", "g1", KeyRepair.RETRIMMED)
        val added = writes(listOf(plan("-11", "g1", PlannedIdSource.STORED)), repaired = listOf(retrimmed)).inserts.single()
        assertEquals(EpisodeAliasProvenance.HEAL_RETRIMMED_KEY, added.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, added.state)

        val seeded = alias("-12", key = null, state = EpisodeAliasState.UNRESOLVED, provenance = EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY)
        val synthetic = LocalEpisodeCollisionGuard.RepairedKey("-12", SHOW, "id:-12", "g2", KeyRepair.SYNTHETIC_KEY)
        val settled = writes(listOf(plan(recipe("g2"), "g2", PlannedIdSource.RECIPE)), written = listOf(row("-12", "g2")), byId = listOf(seeded), repaired = listOf(synthetic)).updates.single()
        assertEquals(EpisodeAliasProvenance.HEAL_SYNTHETIC_KEY, settled.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals("g2", settled.catalogKey)
    }

    @Test
    fun aHealLeavesAnAliasOfAnotherKeyOrShowAlone() {
        val heal = LocalEpisodeCollisionGuard.RepairedKey("-11", SHOW, "g1\n", "g1", KeyRepair.RETRIMMED)
        val planned = listOf(plan("-11", "g1", PlannedIdSource.STORED))
        assertTrue(writes(planned, byId = listOf(alias("-11", key = "other")), repaired = listOf(heal)).isEmpty)
        assertTrue(writes(planned, byId = listOf(alias("-11", key = "g1", show = "200")), repaired = listOf(heal)).isEmpty)
        val ambiguous = alias("-11", key = "g1", state = EpisodeAliasState.AMBIGUOUS)
        assertEquals(EpisodeAliasProvenance.HEAL_RETRIMMED_KEY, writes(planned, byId = listOf(ambiguous), repaired = listOf(heal)).updates.single().provenance)
    }

    @Test
    fun aNewAliasOwnsItsEpisodeOnlyWhenNoTrustedAliasDoesYet() {
        val owned = writes(listOf(plan("-11", "g1", PlannedIdSource.STORED)), showAliases = listOf(alias("-77", key = "g1", owner = true))).inserts.single()
        assertFalse(owned.owner)
        val ambiguousOwner = alias("-77", key = "g1", owner = true, state = EpisodeAliasState.AMBIGUOUS)
        assertTrue(writes(listOf(plan("-11", "g1", PlannedIdSource.STORED)), showAliases = listOf(ambiguousOwner)).inserts.single().owner)
    }

    @Suppress("LongParameterList")
    private fun writes(
        planned: List<PlannedEpisodeRow>,
        written: List<LocalEpisodeEntity> = planned.map { it.entity },
        reserved: Set<String> = emptySet(),
        repaired: List<LocalEpisodeCollisionGuard.RepairedKey> = emptyList(),
        byId: List<EpisodeAliasEntity> = emptyList(),
        showAliases: List<EpisodeAliasEntity> = byId.filter { it.podcastId == SHOW },
        newIds: NewEpisodeIds = NewEpisodeIds(epNs = SHOW_ID),
        podcastId: String = SHOW,
        storedRowProvenance: String = EpisodeAliasProvenance.FEED_STORED_ROW,
    ): CatalogAliasRecords.Writes = CatalogAliasRecords.of(
        podcastId = podcastId,
        planned = planned,
        resolution = LocalEpisodeCollisionGuard.Resolution(written, repaired, emptyList(), reserved),
        aliasesById = byId.associateBy { it.aliasId },
        showAliases = showAliases,
        newIds = newIds,
        nowMillis = NOW,
        storedRowProvenance = storedRowProvenance,
    )

    private fun plan(id: String, key: String, source: PlannedIdSource, contract: Boolean = true) = PlannedEpisodeRow(row(id, key), source, contract)

    private fun row(id: String, key: String) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        guid = key,
        title = "Ep",
        description = "",
        audioUrl = AUDIO,
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

    @Suppress("LongParameterList")
    private fun alias(
        id: String,
        key: String?,
        state: String = EpisodeAliasState.TRUSTED,
        owner: Boolean = false,
        show: String = SHOW,
        provenance: String = EpisodeAliasProvenance.UPGRADE_CATALOG,
    ) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = show,
        catalogKey = key,
        recipeId = key?.let { EpisodeRecipeId.forNamespace(SHOW_ID, it) },
        state = state,
        owner = owner,
        provenance = provenance,
        createdAt = 1L,
    )

    private fun recipe(key: String) = EpisodeRecipeId.forNamespace(SHOW_ID, key).toString()

    private companion object {
        const val SHOW = "100"
        const val SHOW_ID = 100L
        const val NOW = 42L
        const val AUDIO = "https://cdn.example/a.mp3"
    }
}
