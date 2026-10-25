package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.EpisodeRecipeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeAliasSeedingTest {
    private fun catalog(
        id: String,
        key: String,
        podcastId: String = SHOW,
        audioUrl: String? = AUDIO,
        heldUnderAnotherShow: Boolean = false,
    ) = EpisodeAliasSeeding.forCatalogRow(id, podcastId, key, audioUrl, heldUnderAnotherShow)

    @Test
    fun podcastIndexAndNegativeIdsAreTrustedUnderTheirKeysRecipe() {
        val recipe = EpisodeRecipeId.forNamespace(SHOW.toLong(), "guid-a")
        assertEquals(
            AliasSeed("5001", SHOW, "guid-a", recipe, EpisodeAliasState.TRUSTED, EpisodeAliasProvenance.UPGRADE_CATALOG),
            catalog("5001", "guid-a"),
        )
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-9223372036854775000", "guid-a")?.state)
        assertEquals(recipe, catalog("-9223372036854775000", "guid-a")?.recipeId)
    }

    @Test
    fun keysSqliteTrimLeftPaddedAreReTrimmedBeforeHashing() {
        val clean = EpisodeRecipeId.forNamespace(SHOW.toLong(), "guid-a")
        for (padded in listOf("guid-a\n", "\tguid-a", "guid-a ", "᠎guid-a", "　guid-a\r\n", " guid-a ")) {
            val seed = catalog("-11", padded)
            assertEquals("guid-a", seed?.catalogKey, padded)
            assertEquals(clean, seed?.recipeId, padded)
            assertEquals(EpisodeAliasState.TRUSTED, seed?.state, padded)
        }
    }

    @Test
    fun charactersOutsideTheTrimSetStayInTheKey() {
        for (kept in listOf("guid-a﻿", "guid-a​", "\u0001guid-a")) {
            assertEquals(kept, catalog("-11", kept)?.catalogKey)
        }
    }

    @Test
    fun syntheticKeysOfTheRowsOwnIdAreUnresolvedAndNeverHashed() {
        assertEquals(
            AliasSeed("-12", SHOW, null, null, EpisodeAliasState.UNRESOLVED, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY),
            catalog("-12", "id:-12"),
        )
        // Another row's synthetic-looking key is just a key.
        assertEquals("id:-12", catalog("-13", "id:-12")?.catalogKey)
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-13", "id:-12")?.state)
    }

    @Test
    fun phoneMintedIdsKeyedByTheirOwnAudioAddressAreUnresolved() {
        // Migration 31→32 keyed saved items by address when their GUID repeated; once Podcast Index
        // lists the item its saved row is gone, so nothing on the phone shows whether it had a GUID.
        for (key in listOf(AUDIO, "$AUDIO\n", " $AUDIO")) {
            val seed = catalog("-14", key)
            assertEquals(EpisodeAliasState.UNRESOLVED, seed?.state, key)
            assertEquals(EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY, seed?.provenance, key)
            assertNull(seed?.catalogKey, key)
            assertNull(seed?.recipeId, key)
        }
        assertEquals(EpisodeAliasState.UNRESOLVED, catalog("-14", AUDIO, audioUrl = "$AUDIO ")?.state)
    }

    @Test
    fun audioAddressKeysOfOtherIdsAndOtherKeysAreTrusted() {
        // A Podcast Index ID was matched by the feed read that wrote its key.
        assertEquals(EpisodeAliasState.TRUSTED, catalog("5001", AUDIO)?.state)
        assertEquals(AUDIO, catalog("5001", AUDIO)?.catalogKey)
        // The key is another address than the row's own.
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", "https://cdn.example/old.mp3")?.state)
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", "guid-a", audioUrl = null)?.state)
    }

    @Test
    fun anIdHeldUnderAnotherShowKeepsItsKeyButIsAmbiguous() {
        val seed = catalog("-17", "guid-a", heldUnderAnotherShow = true)
        assertEquals(EpisodeAliasState.AMBIGUOUS, seed?.state)
        assertEquals("guid-a", seed?.catalogKey)
        assertEquals(EpisodeRecipeId.forNamespace(SHOW.toLong(), "guid-a"), seed?.recipeId)
        assertEquals(EpisodeAliasProvenance.UPGRADE_CATALOG, seed?.provenance)
        // A served ID that matches its key is still in doubt when another show holds it.
        val served = catalog("8825297254050678", "https://api.spreaker.com/episode/75827097", podcastId = "3391039", heldUnderAnotherShow = true)
        assertEquals(EpisodeAliasState.AMBIGUOUS, served?.state)
        // Rows with no usable key stay unresolved.
        assertEquals(EpisodeAliasState.UNRESOLVED, catalog("-12", "id:-12", heldUnderAnotherShow = true)?.state)
    }

    @Test
    fun aServedRecipeIdThatMatchesTheKeyIsTheEpisodesOwnAlias() {
        val seed = catalog("8825297254050678", "https://api.spreaker.com/episode/75827097", podcastId = "3391039")
        assertEquals(8825297254050678L, seed?.recipeId)
        assertEquals(EpisodeAliasState.TRUSTED, seed?.state)
    }

    @Test
    fun aRecipeRangeIdThatDoesNotMatchTheKeyStaysButIsAmbiguous() {
        val seed = catalog(EpisodeRecipeId.MIN.toString(), "guid-c")
        assertEquals(EpisodeAliasState.AMBIGUOUS, seed?.state)
        assertEquals(EpisodeRecipeId.forNamespace(SHOW.toLong(), "guid-c"), seed?.recipeId)
        assertEquals(EpisodeRecipeId.MIN.toString(), seed?.aliasId)
        assertEquals(EpisodeAliasState.TRUSTED, catalog((EpisodeRecipeId.MIN - 1).toString(), "guid-c")?.state)
    }

    @Test
    fun onlyPodcastIndexShowsAndRealEpisodeIdsGetAliases() {
        for (show in listOf("rss:abc", "0123", "+5", " 5", "9007199254740992", "", "briefing_us")) {
            assertNull(catalog("-1", "guid", podcastId = show), show)
            assertNull(EpisodeAliasSeeding.forListenerRow("-1", show), show)
        }
        assertNull(catalog("briefing_us_2026-10-25", "guid"))
        assertNull(catalog(" ", "guid"))
        assertNull(EpisodeAliasSeeding.forListenerRow("briefing_in_1", SHOW))
    }

    @Test
    fun aBlankStoredKeyLeavesTheIdUnresolved() {
        assertEquals(EpisodeAliasState.UNRESOLVED, catalog("-16", " \t")?.state)
        assertEquals(EpisodeAliasProvenance.UPGRADE_CATALOG, catalog("-16", " \t")?.provenance)
    }

    @Test
    fun idsOnlyInHistoryQueueOrDownloadsAreUnresolved() {
        assertEquals(
            AliasSeed("4503599627370999", SHOW, null, null, EpisodeAliasState.UNRESOLVED, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW),
            EpisodeAliasSeeding.forListenerRow("4503599627370999", SHOW),
        )
    }

    private companion object {
        const val SHOW = "100"
        const val AUDIO = "https://cdn.example/a.mp3"
    }
}
