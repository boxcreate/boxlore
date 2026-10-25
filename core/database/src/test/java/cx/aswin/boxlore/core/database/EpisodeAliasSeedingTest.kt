package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.EpisodeRecipeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeAliasSeedingTest {
    private fun catalog(id: String, key: String, podcastId: String = SHOW, audioUrl: String? = AUDIO, savedGuid: String? = null,) =
        EpisodeAliasSeeding.forCatalogRow(id, podcastId, key, audioUrl, savedGuid)

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
    fun audioAddressKeysOfItemsThatHadAGuidAreUnresolved() {
        val seed = catalog("-14", AUDIO, savedGuid = "repeated-guid")
        assertEquals(EpisodeAliasState.UNRESOLVED, seed?.state)
        assertEquals(EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY, seed?.provenance)
        assertNull(seed?.catalogKey)
        assertNull(seed?.recipeId)
    }

    @Test
    fun audioAddressKeysAreTrustedWhenTheItemHadNoOtherGuid() {
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", AUDIO, savedGuid = null)?.state)
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", AUDIO, savedGuid = " \n")?.state)
        // The GUID itself was the audio address.
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", AUDIO, savedGuid = "$AUDIO\n")?.state)
        // A real GUID key that differs from the saved item's GUID is still a GUID key, not an address.
        assertEquals(EpisodeAliasState.TRUSTED, catalog("-15", "new-guid", savedGuid = "old-guid")?.state)
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
