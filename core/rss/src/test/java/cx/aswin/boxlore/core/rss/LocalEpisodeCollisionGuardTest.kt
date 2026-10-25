package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.LocalEpisodeCollisionGuard.KeyRepair
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalEpisodeCollisionGuardTest {
    private val namespace = RssIdGenerator.podcastId("https://feeds.example/shared.xml")

    /** A `local_episodes` stand-in, in insertion order. */
    private val table = mutableListOf<LocalEpisodeEntity>()
    private val owners: suspend (List<String>) -> List<LocalEpisodeOwner> = { ids ->
        table.filter { it.episodeId in ids }.map { LocalEpisodeOwner(it.episodeId, it.podcastId, it.guid) }
    }
    private val showRows: suspend (String) -> List<LocalEpisodeIdentity> = { podcastId ->
        table.filter { it.podcastId == podcastId }.map { LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) }
    }

    /** An `episode_aliases` stand-in. */
    private val aliasTable = mutableListOf<EpisodeAliasEntity>()
    private val aliases: suspend (List<String>) -> List<EpisodeAliasEntity> = { ids -> aliasTable.filter { it.aliasId in ids } }

    /** Room's REPLACE: deletes every stored row sharing the primary key or the unique (podcastId, guid) index. */
    private fun store(rows: List<LocalEpisodeEntity>) {
        for (r in rows) {
            table.removeAll { it.episodeId == r.episodeId || (it.podcastId == r.podcastId && it.guid == r.guid) }
            table += r
        }
    }

    private suspend fun resolve(rows: List<LocalEpisodeEntity>) = LocalEpisodeCollisionGuard.resolve(rows, namespace, owners, showRows, aliases)

    private fun ownerOf(id: String) = table.single { it.episodeId == id }

    @Test
    fun anotherShowsIdGetsAReservedIdAndBothEpisodesSurvive() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val written = resolve(listOf(row("-11", show = "200", key = "guid-a"))).rows

        val reserved = written.single().episodeId
        assertNotEquals("-11", reserved)
        assertTrue(reserved.toLong() < 0L, "reserved IDs stay negative")
        assertEquals("200", written.single().podcastId)
        store(written)
        assertEquals("100", ownerOf("-11").podcastId)
        assertEquals("200", ownerOf(reserved).podcastId)
    }

    @Test
    fun theSameShowAndKeyKeepsUpdatingItsRow() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val written = resolve(listOf(row("-11", show = "100", key = "guid-a", title = "Renamed"))).rows
        assertEquals("-11", written.single().episodeId)
        assertEquals("Renamed", written.single().title)
    }

    @Test
    fun theSameShowWithAnotherKeyIsAClashToo() = runTest {
        store(listOf(row("77", show = "100", key = "old-guid")))
        val written = resolve(listOf(row("77", show = "100", key = "new-guid"))).rows
        assertNotEquals("77", written.single().episodeId)
    }

    @Test
    fun twoRowsOfOneBatchNeverShareAnId() = runTest {
        val written = resolve(listOf(row("-11", show = "100", key = "guid-a"), row("-11", show = "100", key = "guid-b"))).rows
        assertEquals(2, written.map { it.episodeId }.toSet().size)
        assertEquals("-11", written.first().episodeId)
    }

    @Test
    fun aReservedIdIsStableAcrossRefreshes() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val first = resolve(listOf(row("-11", show = "200", key = "guid-a"))).rows
        store(first)
        val again = resolve(listOf(row("-11", show = "200", key = "guid-a"))).rows
        assertEquals(first.single().episodeId, again.single().episodeId)
    }

    @Test
    fun aLegacyKeyWithTrailingWhitespaceKeepsItsIdAndGetsNoSecondRow() = runTest {
        // Room migration 31→32 trimmed keys with SQLite TRIM (spaces only). The refresh re-trims the
        // stored key and reuses its ID; the guard used to compare it untrimmed and reserve a new ID.
        for (suffix in LEGACY_KEY_SUFFIXES) {
            table.clear()
            store(listOf(row("-11", show = "100", key = "guid-a$suffix")))
            val resolution = resolve(listOf(row("-11", show = "100", key = "guid-a")))

            store(resolution.rows)
            assertEquals(listOf("-11"), table.map { it.episodeId }, "one row for suffix ${codePoints(suffix)}")
            assertEquals("guid-a", table.single().guid)
            assertEquals(KeyRepair.RETRIMMED, resolution.repaired.single().repair)
            assertEquals("guid-a$suffix", resolution.repaired.single().storedKey)
        }
    }

    @Test
    fun anIncomingIdUnderAStoredKeyTakesTheStoredIdInsteadOfDeletingIt() = runTest {
        // REPLACE also deletes a row sharing (show, key). An incoming row with another ID for a key
        // the show stores must update that row, not delete it through the unique index.
        store(listOf(row("-11", show = "100", key = "guid-a", title = "Stored")))
        val written = resolve(listOf(row("-22", show = "100", key = "guid-a", title = "Fresh"))).rows

        assertEquals("-11", written.single().episodeId)
        store(written)
        assertEquals(listOf("-11"), table.map { it.episodeId })
        assertEquals("Fresh", table.single().title)
    }

    @Test
    fun anExactStoredKeyWinsOverARetrimmedOneSoNeitherRowIsDeleted() = runTest {
        // Two stored rows of one show whose keys trim alike: writing the re-trimmed one's ID under the
        // exact key would delete the exact one. Both rows survive and the exact row is updated.
        store(listOf(row("-11", show = "100", key = "guid-a\n"), row("-12", show = "100", key = "guid-a")))
        val written = resolve(listOf(row("-11", show = "100", key = "guid-a", title = "Fresh"))).rows

        assertEquals("-12", written.single().episodeId)
        store(written)
        assertEquals(setOf("-11", "-12"), table.map { it.episodeId }.toSet())
        assertEquals("Fresh", ownerOf("-12").title)
    }

    @Test
    fun aStoredKeyKeepsItsIdEvenWhenAnotherRowOfTheBatchMintedIt() = runTest {
        // A new item whose minted ID equals a stored row's ID must not take that row from its own key.
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val written = resolve(listOf(row("-11", show = "100", key = "guid-new"), row("-11", show = "100", key = "guid-a"))).rows

        assertEquals("-11", written.single { it.guid == "guid-a" }.episodeId)
        assertNotEquals("-11", written.single { it.guid == "guid-new" }.episodeId)
        assertEquals(listOf("guid-new", "guid-a"), written.map { it.guid }, "input order is kept")
    }

    @Test
    fun aSyntheticLegacyKeyHoldingTheItemsOwnIdIsHealed() = runTest {
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        store(listOf(row(minted, show = "100", key = "id:$minted")))
        val resolution = resolve(listOf(row(minted, show = "100", key = "guid-a")))

        store(resolution.rows)
        assertEquals(listOf(minted), table.map { it.episodeId })
        assertEquals("guid-a", table.single().guid)
        assertEquals(KeyRepair.SYNTHETIC_KEY, resolution.repaired.single().repair)
        assertEquals("id:$minted", resolution.repaired.single().storedKey)
    }

    @Test
    fun anAudioAddressLegacyKeyHoldingTheItemsOwnIdIsHealedWhenTheItemHasAGuid() = runTest {
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        val audio = "https://cdn.example/guid-a.mp3"
        store(listOf(row(minted, show = "100", key = audio, audioUrl = audio)))
        val resolution = resolve(listOf(row(minted, show = "100", key = "guid-a")))

        store(resolution.rows)
        assertEquals(listOf(minted), table.map { it.episodeId })
        assertEquals("guid-a", table.single().guid)
        assertEquals(KeyRepair.AUDIO_URL_KEY, resolution.repaired.single().repair)
    }

    @Test
    fun aLegacyKeyIsKeptApartWhenTheIdIsNotTheItemsOwnMint() = runTest {
        // Same show and a synthetic key, but the stored ID came from elsewhere (a Podcast Index ID).
        store(listOf(row("-11", show = "100", key = "id:-11")))
        val resolution = resolve(listOf(row("-11", show = "100", key = "guid-a")))

        assertNotEquals("-11", resolution.rows.single().episodeId)
        assertTrue(resolution.repaired.isEmpty())
    }

    @Test
    fun anotherShowsLegacyRowIsNeverHealed() = runTest {
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        store(listOf(row(minted, show = "100", key = "id:$minted")))
        val resolution = resolve(listOf(row(minted, show = "200", key = "guid-a")))

        assertNotEquals(minted, resolution.rows.single().episodeId)
        store(resolution.rows)
        assertEquals("id:$minted", ownerOf(minted).guid)
    }

    @Test
    fun aLegacyRowHoldingTheItemsOwnMintIsHealedWhenTheRowArrivesWithAnotherId() = runTest {
        // On a catalog's first read the item can arrive with a Podcast Index match instead of its
        // own mint. The legacy row holding the own mint is still the same episode: it keeps its ID
        // and takes the feed's key, and the other ID is not written as a second row.
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        val audio = "https://cdn.example/guid-a.mp3"
        for ((legacyKey, repair) in listOf("id:$minted" to KeyRepair.SYNTHETIC_KEY, audio to KeyRepair.AUDIO_URL_KEY)) {
            table.clear()
            store(listOf(row(minted, show = "100", key = legacyKey, audioUrl = audio)))
            val resolution = resolve(listOf(row("777", show = "100", key = "guid-a", audioUrl = audio)))

            store(resolution.rows)
            assertEquals(listOf(minted), table.map { it.episodeId }, "one row for $legacyKey")
            assertEquals("guid-a", table.single().guid)
            assertEquals(repair, resolution.repaired.single().repair)
            assertEquals(legacyKey, resolution.repaired.single().storedKey)
        }
    }

    @Test
    fun anotherEpisodeHoldingTheItemsOwnMintStaysApartWhenTheRowArrivesWithAnotherId() = runTest {
        // A same-show row under the own mint with a real, different GUID is another episode.
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        store(listOf(row(minted, show = "100", key = "guid-other")))
        val resolution = resolve(listOf(row("777", show = "100", key = "guid-a")))

        assertEquals("777", resolution.rows.single().episodeId)
        assertTrue(resolution.repaired.isEmpty())
        store(resolution.rows)
        assertEquals("guid-other", ownerOf(minted).guid)
    }

    @Test
    fun aLegacyRowWhoseKeyAnotherItemStillUsesIsNotHealed() = runTest {
        // The audio-keyed legacy row is exactly the key of a GUID-less item in the batch, so that
        // item keeps the row; the GUID item whose own mint it holds gets a row of its own.
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "guid-a")
        val audio = "https://cdn.example/shared.mp3"
        store(listOf(row(minted, show = "100", key = audio, audioUrl = audio)))
        val resolution =
            resolve(listOf(row(minted, show = "100", key = "guid-a", audioUrl = audio), row("-12", show = "100", key = audio, audioUrl = audio)))

        assertEquals(minted, resolution.rows.single { it.guid == audio }.episodeId)
        assertNotEquals(minted, resolution.rows.single { it.guid == "guid-a" }.episodeId)
        assertTrue(resolution.repaired.isEmpty())
    }

    @Test
    fun anAudioAddressKeyIsKeptApartWhenTheItemHasNoGuid() = runTest {
        // Both keys are audio addresses: a different address is a different episode.
        val newAudio = "https://cdn.example/new.mp3"
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, newAudio)
        store(listOf(row(minted, show = "100", key = "https://cdn.example/old.mp3", audioUrl = "https://cdn.example/old.mp3")))
        val resolution = resolve(listOf(row(minted, show = "100", key = newAudio, audioUrl = newAudio)))

        assertNotEquals(minted, resolution.rows.single().episodeId)
        assertTrue(resolution.repaired.isEmpty())
    }

    @Test
    fun exhaustingReservedIdsKeepsAStoredRowOnItsId() = runTest {
        // Every reserved ID is taken, but the show already stores the key (with a legacy newline):
        // the row keeps its stored ID instead of being left out.
        store(listOf(row("-11", show = "200", key = "guid-a\n")))
        store(listOf(row("-99", show = "300", key = "other")))
        takeEveryReservedId(show = "200", key = "guid-a")
        val resolution = resolve(listOf(row("-99", show = "200", key = "guid-a")))

        assertEquals("-11", resolution.rows.single().episodeId)
        assertTrue(resolution.unplaced.isEmpty())
        store(resolution.rows)
        assertEquals("other", ownerOf("-99").guid, "the other show's row is untouched")
    }

    @Test
    fun aNewRowWithNoFreeIdIsReportedInsteadOfOverwriting() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        takeEveryReservedId(show = "200", key = "guid-a")
        val incoming = row("-11", show = "200", key = "guid-a")
        val resolution = resolve(listOf(incoming))

        assertTrue(resolution.rows.isEmpty())
        assertEquals(listOf(incoming), resolution.unplaced)
    }

    @Test
    fun aRepeatedKeyInOneBatchIsWrittenOnce() = runTest {
        val written = resolve(listOf(row("-11", show = "100", key = "guid-a", title = "First"), row("-12", show = "100", key = "guid-a"))).rows
        assertEquals("First", written.single().title)
    }

    @Test
    fun theLegacyUpgradeCheckIgnoresItsOwnShowsRows() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-11", show = "100", key = "guid-x")), "100", owners))
        assertTrue(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-11", show = "200", key = "guid-a")), "200", owners))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-12", show = "200", key = "guid-b")), "200", owners))
    }

    @Test
    fun theLegacyUpgradeCheckAlsoStopsAtAnotherShowsAlias() = runTest {
        // Show 100's catalog was evicted; its alias still holds -11 and the listener state on it.
        aliasTable += alias("-11", show = "100", key = "guid-a")
        val rows = listOf(row("-11", show = "200", key = "guid-a"))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(rows, "200", owners), "without aliases the check can't see it")
        assertTrue(LocalEpisodeCollisionGuard.takesAnotherShowsId(rows, "200", owners, aliases))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-11", show = "100", key = "guid-a")), "100", owners, aliases))
    }

    @Test
    fun aTakenReusedIdTriesItsFallbacksInOrderBeforeAReservedOne() = runTest {
        // The evicted row's ID now belongs to another show's catalog row.
        store(listOf(row("-11", show = "300", key = "other")))
        val incoming = row("-11", show = "100", key = "guid-a")
        val withRecipe =
            LocalEpisodeCollisionGuard.resolve(listOf(incoming), namespace, owners, showRows, aliases, fallbackIds = listOf(listOf("-12", RECIPE)))
        assertEquals("-12", withRecipe.rows.single().episodeId, "the key's other alias ID first")
        assertTrue(withRecipe.reservedIds.isEmpty())

        store(listOf(row("-12", show = "300", key = "other-2")))
        val recipe = LocalEpisodeCollisionGuard.resolve(listOf(incoming), namespace, owners, showRows, aliases, fallbackIds = listOf(listOf("-12", RECIPE)))
        assertEquals(RECIPE, recipe.rows.single().episodeId, "then the recipe ID, never a reserved one while it is free")
        assertTrue(recipe.reservedIds.isEmpty())

        aliasTable += alias(RECIPE, show = "300", key = "other-3")
        val reserved = LocalEpisodeCollisionGuard.resolve(listOf(incoming), namespace, owners, showRows, aliases, fallbackIds = listOf(listOf("-12", RECIPE)))
        assertEquals(StickyEpisodeIdentity.reservedEpisodeId("100", "guid-a", 1), reserved.rows.single().episodeId)
        assertEquals(setOf(StickyEpisodeIdentity.reservedEpisodeId("100", "guid-a", 1)), reserved.reservedIds)
    }

    @Test
    fun aFreeIdNeverLooksAtItsFallbacks() = runTest {
        val resolution = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "100", key = "guid-a")), namespace, owners, showRows, aliases, fallbackIds = listOf(listOf(RECIPE)))
        assertEquals("-11", resolution.rows.single().episodeId)
    }

    @Test
    fun aReservedIdIsReportedAndMintedInTheShowsNamespace() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val resolution = resolve(listOf(row("-11", show = "200", key = "guid-a")))

        val reserved = resolution.rows.single().episodeId
        assertEquals(StickyEpisodeIdentity.reservedEpisodeId("200", "guid-a", 1), reserved, "the show ID, not the feed address")
        assertEquals(setOf(reserved), resolution.reservedIds)
    }

    @Test
    fun anotherShowsAliasBlocksAnIdNoCatalogRowHolds() = runTest {
        // Show 100's catalog was evicted; its alias keeps the ID and the listener state on it.
        aliasTable += alias("-11", show = "100", key = "guid-a")
        val written = resolve(listOf(row("-11", show = "200", key = "guid-a"))).rows
        assertNotEquals("-11", written.single().episodeId)
    }

    @Test
    fun aTrustedAliasOfAnotherKeyOfTheSameShowBlocks() = runTest {
        aliasTable += alias(RECIPE, show = "100", key = "guid-a")
        val written = resolve(listOf(row(RECIPE, show = "100", key = "guid-b"))).rows
        assertNotEquals(RECIPE, written.single().episodeId, "both episodes survive")
    }

    @Test
    fun anAliasOfTheSameEpisodeOrAnUnsettledOneOfTheShowDoesNotBlock() = runTest {
        aliasTable += alias(RECIPE, show = "100", key = "guid-a")
        assertEquals(RECIPE, resolve(listOf(row(RECIPE, show = "100", key = "guid-a"))).rows.single().episodeId)

        aliasTable.clear()
        aliasTable += alias(RECIPE, show = "100", key = null, state = EpisodeAliasState.UNRESOLVED)
        assertEquals(RECIPE, resolve(listOf(row(RECIPE, show = "100", key = "guid-a"))).rows.single().episodeId)

        aliasTable.clear()
        aliasTable += alias(RECIPE, show = "100", key = "older-key", state = EpisodeAliasState.AMBIGUOUS)
        assertEquals(RECIPE, resolve(listOf(row(RECIPE, show = "100", key = "guid-a"))).rows.single().episodeId)
    }

    @Test
    fun aReservedIdAnAliasHoldsIsSkipped() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        aliasTable += alias(StickyEpisodeIdentity.reservedEpisodeId("200", "guid-a", 1), show = "300", key = "other")
        val written = resolve(listOf(row("-11", show = "200", key = "guid-a"))).rows
        assertEquals(StickyEpisodeIdentity.reservedEpisodeId("200", "guid-a", 2), written.single().episodeId)
    }

    private fun alias(id: String, show: String, key: String?, state: String = EpisodeAliasState.TRUSTED) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = show,
        catalogKey = key,
        recipeId = null,
        state = state,
        owner = state == EpisodeAliasState.TRUSTED,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
    )

    private fun takeEveryReservedId(show: String, key: String) {
        for (attempt in 1..LocalEpisodeCollisionGuard.MAX_RESERVE_ATTEMPTS) {
            val id = StickyEpisodeIdentity.reservedEpisodeId(show, key, attempt)
            store(listOf(row(id, show = "300", key = "taken-$attempt")))
        }
    }

    private fun row(
        id: String,
        show: String,
        key: String,
        title: String = "Ep",
        audioUrl: String = "https://cdn.example/${EpisodeRecipeId.trimKey(key) ?: key}.mp3",
    ) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
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
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private fun codePoints(value: String): String = value.map { "U+%04X".format(it.code) }.joinToString(" ")

    private companion object {
        /**
         * Key endings SQLite `TRIM()` (spaces only) leaves on a legacy key and the recipe trims:
         * control characters, a space before a newline, a no-break space, a Unicode line separator,
         * and U+180E, which the desktop JVM's `String.trim()` keeps but Android's and the recipe's trim.
         */
        val LEGACY_KEY_SUFFIXES = listOf("\n", "\t", "\r\n", " \n", "\u00A0", "\u2028", "\u180E")

        const val RECIPE = "4503599627370500"
    }
}
