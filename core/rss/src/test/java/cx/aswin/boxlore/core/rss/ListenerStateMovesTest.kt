package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The narrowed re-key paths (docs/EPISODE_IDS.md §5): the orphan rematch moves only unresolved
 * legacy IDs, never resurrects a cleared like, and records the moved ID as an alias; switching a
 * Podcast Index show to its feed moves nothing and links the feed's episodes by alias instead.
 */
class ListenerStateMovesTest {
    @Test
    fun theOrphanRematchLeavesTrustedAndAmbiguousIdsAlone() {
        val aliases = listOf(alias("-1", EpisodeAliasState.TRUSTED), alias("-2", EpisodeAliasState.AMBIGUOUS), alias("-3", EpisodeAliasState.UNRESOLVED, key = null))
        val settled = ListenerStateMoves.settledIds(aliases)
        assertEquals(setOf("-1", "-2"), settled)

        val target = catalogRow("59023522163")
        val references = listOf("-1", "-2", "-3", "-4").map { id ->
            LocalCatalogListenerReference(episodeId = id, enclosureUrl = target.audioUrl, title = target.title, publishedDate = null)
        }
        val mappings = LocalCatalogListenerRemapLogic.mappings(listOf(target), references.filter { it.episodeId !in settled })

        assertEquals(setOf("-3", "-4"), mappings.keys)
        assertTrue(LocalCatalogListenerRemapLogic.isLegacyLocalId("-3"))
        assertFalse(LocalCatalogListenerRemapLogic.isLegacyLocalId("59023522163"))
        assertFalse(LocalCatalogListenerRemapLogic.isLegacyLocalId("briefing_us_2026-10-25"))
    }

    @Test
    fun aMovedIdBecomesATrustedNonOwnerAliasOfTheCatalogRowsEpisode() {
        val target = alias("59023522163", EpisodeAliasState.TRUSTED, owner = true)

        val moved = ListenerStateMoves.movedAlias("-9", target, nowMs = 1_234L)!!

        assertEquals(
            EpisodeAliasEntity("-9", SHOW, KEY, RECIPE, EpisodeAliasState.TRUSTED, owner = false, provenance = EpisodeAliasProvenance.ORPHAN_REMATCH, createdAt = 1_234L),
            moved,
        )
        assertNull(ListenerStateMoves.movedAlias("-9", null, 1L))
        assertEquals(target, ListenerStateMoves.trustedTarget(target, SHOW))
        assertNull(ListenerStateMoves.trustedTarget(target, "other-show"))
        assertNull(ListenerStateMoves.trustedTarget(alias("1", EpisodeAliasState.AMBIGUOUS), SHOW))
        assertNull(ListenerStateMoves.trustedTarget(null, SHOW))
    }

    @Test
    fun aTrustedOrphanJoinsTheCatalogRowThatHoldsItsKeyUnderAnotherId() {
        val orphan = alias("-5", EpisodeAliasState.TRUSTED)
        val reread = catalogRow("777")
        val legacySynthetic = catalogRow("778").copy(guid = "id:778", legacy = true)

        assertEquals(reread, ListenerStateMoves.catalogRowOfKey(orphan, listOf(reread, legacySynthetic)))
        assertNull(ListenerStateMoves.catalogRowOfKey(orphan, listOf(legacySynthetic)))
        assertNull(ListenerStateMoves.catalogRowOfKey(orphan.copy(state = EpisodeAliasState.AMBIGUOUS), listOf(reread)))
        // Two rows with the key, or a row of another show, are not proof.
        assertNull(ListenerStateMoves.catalogRowOfKey(orphan, listOf(reread, catalogRow("779"))))
        assertNull(ListenerStateMoves.catalogRowOfKey(orphan, listOf(reread.copy(podcastId = "other"))))
        // A key stored with a trailing newline by an older build still matches once trimmed.
        assertEquals(reread.copy(guid = "$KEY\n"), ListenerStateMoves.catalogRowOfKey(orphan, listOf(reread.copy(guid = "$KEY\n"))))

        val joined = ListenerStateMoves.joinedCatalogAlias(orphan, reread, existing = null, nowMs = 9L)!!
        assertEquals(
            EpisodeAliasEntity("777", SHOW, KEY, RECIPE, EpisodeAliasState.TRUSTED, owner = false, provenance = EpisodeAliasProvenance.CATALOG_KEY_MATCH, createdAt = 9L),
            joined,
        )
        // An unresolved alias of the row's ID is resolved; any other alias is left as it is.
        assertEquals(joined, ListenerStateMoves.joinedCatalogAlias(orphan, reread, alias("777", EpisodeAliasState.UNRESOLVED, key = null), 9L))
        assertNull(ListenerStateMoves.joinedCatalogAlias(orphan, reread, alias("777", EpisodeAliasState.TRUSTED), 9L))
        assertNull(ListenerStateMoves.joinedCatalogAlias(orphan, reread, alias("777", EpisodeAliasState.AMBIGUOUS), 9L))
    }

    @Test
    fun aRematchIsExactOnlyWhenItsAddressNamesOneCatalogRow() {
        val target = catalogRow("1")
        val byAddress = LocalCatalogListenerReference("-1", enclosureUrl = " ${target.audioUrl} ", title = null, publishedDate = null)
        val byTitle = LocalCatalogListenerReference("-2", enclosureUrl = null, title = target.title, publishedDate = target.publishedDate)

        val rematches = LocalCatalogListenerRemapLogic.rematches(listOf(target), listOf(byAddress, byTitle))

        assertTrue(rematches.getValue("-1").exact)
        assertFalse(rematches.getValue("-2").exact)
        assertEquals(target, rematches.getValue("-2").target)
    }

    @Test
    fun anOrphanMergeTakesTheLatestLikeDecisionSoAnOlderLikeCannotUndoAnUnlike() {
        val olderLike = history("-9", liked = true, likedAt = 10L)
        val laterUnlike = history("1", liked = false, likedAt = 20L)

        val merged = mergeHistory(olderLike, laterUnlike, catalogRow("1"))

        assertFalse(merged.isLiked)
        assertEquals(20L, merged.likedAt)
        // A like decided later still wins, and legacy rows without a like time keep either like.
        assertTrue(mergeHistory(olderLike.copy(likedAt = 30L), laterUnlike, catalogRow("1")).isLiked)
        assertTrue(mergeHistory(history("-9", liked = true), history("1"), catalogRow("1")).isLiked)
    }

    @Test
    fun anOrphanMergeKeepsCompletionUnlessTheGroupSettingsClearedItLater() {
        val completed = history("-9", completed = true, playedAt = 50L, manual = true)
        val unfinished = history("1", playedAt = 40L)

        val kept = mergeHistory(completed, unfinished, catalogRow("1"))
        assertTrue(kept.isCompleted)
        assertTrue(kept.isManualCompletion)

        val clearedLater = EpisodeGroupStateEntity(SHOW, KEY, completeClearedAt = 60L)
        val cleared = mergeHistory(completed, unfinished, catalogRow("1"), clearedLater)
        assertFalse(cleared.isCompleted)
        assertFalse(cleared.isManualCompletion)

        val clearedEarlier = EpisodeGroupStateEntity(SHOW, KEY, completeClearedAt = 45L)
        assertTrue(mergeHistory(completed, unfinished, catalogRow("1"), clearedEarlier).isCompleted)
        // A later completion recorded for the episode carries over even when neither row has it.
        val completedLater = EpisodeGroupStateEntity(SHOW, KEY, completedAt = 70L)
        assertTrue(mergeHistory(unfinished.copy(episodeId = "-9"), unfinished, catalogRow("1"), completedLater).isCompleted)
    }

    @Test
    fun switchingAShowToItsFeedLinksAnRssEpisodeAsTrustedOnlyByAnExactUniqueKey() {
        val rss = listOf(rssEpisode("-71", guid = KEY), rssEpisode("-72", guid = "guid-b", audio = AUDIO_B, title = "Two"))
        val aliases = mapOf("555" to alias("555", EpisodeAliasState.TRUSTED, owner = true))

        val plan = switched(listOf(reference("555", audio = "https://elsewhere.example/1.mp3")), aliases, rss)

        // The old ID's trusted key names exactly one RSS episode: it joins that episode as a non-owner.
        assertEquals(
            listOf(EpisodeAliasEntity("-71", SHOW, KEY, RECIPE, EpisodeAliasState.TRUSTED, owner = false, provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK, createdAt = NOW)),
            plan.links,
        )
        assertTrue(plan.settled.isEmpty())
    }

    @Test
    fun anyWeakerSwitchMatchIsRecordedAsAmbiguous() {
        // Two RSS episodes share the old ID's key: neither is proven.
        val shared = listOf(rssEpisode("-71", guid = KEY), rssEpisode("-72", guid = KEY, audio = AUDIO_B))
        val trusted = mapOf("555" to alias("555", EpisodeAliasState.TRUSTED))
        assertEquals(
            listOf("-71" to EpisodeAliasState.AMBIGUOUS, "-72" to EpisodeAliasState.AMBIGUOUS),
            switched(listOf(reference("555")), trusted, shared).links.map { it.aliasId to it.state },
        )

        // A match only by title, or a trusted key of another show: recorded under the RSS episode's own key, ambiguous.
        val rss = listOf(rssEpisode("-71", guid = "guid-x", audio = AUDIO_A, title = "One"))
        val otherShow = mapOf("557" to alias("557", EpisodeAliasState.TRUSTED).copy(podcastId = "another-show"))
        for ((references, aliases) in listOf(listOf(reference("555", title = "One")) to emptyMap(), listOf(reference("557", audio = AUDIO_A)) to otherShow)) {
            val plan = switched(references, aliases, rss)
            val link = plan.links.single()
            assertEquals(EpisodeAliasState.AMBIGUOUS, link.state)
            assertEquals("guid-x", link.catalogKey)
            assertEquals(SHOW, link.podcastId)
            assertFalse(link.owner)
            assertTrue(plan.settled.isEmpty())
        }
    }

    @Test
    fun anOldIdWithoutAUsableKeyIsSettledByAnExactUniqueAudioAddress() {
        val rss = listOf(rssEpisode("-71", guid = "guid-x", audio = AUDIO_A, title = "One"), rssEpisode("-72", guid = "guid-y", audio = AUDIO_B))
        // No alias (played before the show was followed), or an unresolved alias of the show (v39 listener-only or address-keyed).
        val unresolved = mapOf("556" to alias("556", EpisodeAliasState.UNRESOLVED, key = null).copy(createdAt = 7L))
        for ((id, aliases) in listOf("555" to emptyMap(), "556" to unresolved)) {
            val plan = switched(listOf(reference(id, audio = " $AUDIO_A ")), aliases, rss)

            val recipe = EpisodeRecipeId.forNamespace(SHOW.toLong(), "guid-x")
            assertEquals(listOf(EpisodeAliasEntity("-71", SHOW, "guid-x", recipe, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.RSS_SOURCE_LINK, NOW)), plan.links)
            val createdAt = aliases[id]?.createdAt ?: NOW
            assertEquals(listOf(EpisodeAliasEntity(id, SHOW, "guid-x", recipe, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.RSS_SOURCE_AUDIO_MATCH, createdAt)), plan.settled)
        }
    }

    @Test
    fun anAudioAddressSettlesNothingWhenItIsNotExactlyOneEpisodesAlone() {
        // Two old IDs point at the same RSS episode: neither is proven to be it.
        val one = listOf(rssEpisode("-71", guid = "guid-x", audio = AUDIO_A))
        val twoClaimants = switched(listOf(reference("555", audio = AUDIO_A), reference("556", audio = AUDIO_A)), emptyMap(), one)
        assertEquals(listOf(EpisodeAliasState.AMBIGUOUS), twoClaimants.links.map { it.state })
        assertTrue(twoClaimants.settled.isEmpty())
        // Two RSS episodes share the audio address.
        val sharedAudio = listOf(rssEpisode("-71", guid = "guid-x", audio = AUDIO_A), rssEpisode("-72", guid = "guid-y", audio = AUDIO_A))
        assertTrue(switched(listOf(reference("555", audio = AUDIO_A)), emptyMap(), sharedAudio).settled.isEmpty())
        // The RSS episode has no GUID: its key is only its address.
        val noGuid = listOf(rssEpisode("-71", guid = " ", audio = AUDIO_A))
        val unkeyed = switched(listOf(reference("555", audio = AUDIO_A)), emptyMap(), noGuid)
        assertTrue(unkeyed.settled.isEmpty())
        assertEquals(EpisodeAliasState.AMBIGUOUS, unkeyed.links.single().state)
        // An unresolved alias of another show is not this show's to settle.
        val otherShow = mapOf("556" to alias("556", EpisodeAliasState.UNRESOLVED, key = null).copy(podcastId = "another-show"))
        assertTrue(switched(listOf(reference("556", audio = AUDIO_A)), otherShow, one).settled.isEmpty())
    }

    @Test
    fun aLookalikeFeedTrustsAKeyOnlyWithTheSameAudioAddressOrTitle() {
        // The listener confirmed a show that only looked the same; both feeds number their GUIDs "1", "2".
        val rss = listOf(rssEpisode("-71", guid = "1", title = "Feed one"), rssEpisode("-72", guid = "2", audio = AUDIO_B, title = "Feed two"))
        val aliases = mapOf("555" to alias("555", EpisodeAliasState.TRUSTED, key = "1"), "556" to alias("556", EpisodeAliasState.TRUSTED, key = "2"))
        val references = listOf(reference("555", audio = AUDIO_A, title = "Another show's one"), reference("556", audio = AUDIO_B, title = "Another show's two"))

        val lookalike = switched(references, aliases, rss, feedProven = false)
        assertEquals(listOf("-71" to EpisodeAliasState.AMBIGUOUS, "-72" to EpisodeAliasState.TRUSTED), lookalike.links.map { it.aliasId to it.state })
        // The same title is a second signal too.
        val sameTitle = switched(listOf(reference("555", audio = AUDIO_A, title = "FEED one!")), aliases, rss, feedProven = false)
        assertEquals(EpisodeAliasState.TRUSTED, sameTitle.links.single().state)
        // The show's own feed needs no second signal.
        assertTrue(switched(references, aliases, rss, feedProven = true).links.all { it.state == EpisodeAliasState.TRUSTED })
    }

    @Test
    fun aLinkUpgradesOnlyAnAmbiguousLinkOfTheSameShowAndNeverDowngrades() {
        val trusted = EpisodeAliasEntity("-71", SHOW, KEY, RECIPE, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.RSS_SOURCE_LINK, NOW)
        val ambiguous = trusted.copy(state = EpisodeAliasState.AMBIGUOUS, catalogKey = "guid-x", recipeId = null, createdAt = 1L)

        assertEquals(trusted, ListenerStateMoves.linkWrite(null, trusted))
        assertEquals(ambiguous.copy(state = EpisodeAliasState.TRUSTED, catalogKey = KEY, recipeId = RECIPE), ListenerStateMoves.linkWrite(ambiguous, trusted))
        assertNull(ListenerStateMoves.linkWrite(trusted, ambiguous))
        assertNull(ListenerStateMoves.linkWrite(ambiguous, ambiguous))
        assertNull(ListenerStateMoves.linkWrite(ambiguous.copy(podcastId = "another-show"), trusted))
        assertNull(ListenerStateMoves.linkWrite(ambiguous.copy(provenance = EpisodeAliasProvenance.UPGRADE_CATALOG), trusted))
    }

    @Test
    fun aWeakerMatchFromAnotherOldIdDoesNotUndoAnExactKeyMatch() {
        // 558 is another episode (guid-c) whose audio address happens to match the RSS episode of KEY.
        val two = mapOf("555" to alias("555", EpisodeAliasState.TRUSTED), "558" to alias("558", EpisodeAliasState.TRUSTED, key = "guid-c"))
        val links = switched(listOf(reference("558", audio = AUDIO_A), reference("555")), two, listOf(rssEpisode("-71", guid = KEY, audio = AUDIO_A))).links
        assertEquals(listOf(Triple("-71", EpisodeAliasState.TRUSTED, KEY)), links.map { Triple(it.aliasId, it.state, it.catalogKey) })
        assertEquals(RECIPE, links.single().recipeId)
    }

    @Test
    fun aSwitchLinksNothingForAmbiguousIdsUnmatchedRowsOrAnIdTheShowAlreadyHolds() {
        val rss = listOf(rssEpisode("-71", guid = KEY, audio = AUDIO_A, title = "One"))
        // An ambiguous old ID never resolves, so nothing is linked to it.
        val ambiguous = mapOf("555" to alias("555", EpisodeAliasState.AMBIGUOUS))
        assertTrue(switched(listOf(reference("555", audio = AUDIO_A)), ambiguous, rss).links.isEmpty())
        // A row that matches no RSS episode links nothing.
        assertTrue(switched(listOf(reference("559", audio = "https://elsewhere.example/9.mp3", title = "Nine")), emptyMap(), rss).links.isEmpty())
        // The show's own rows already hold the RSS episode's ID: it is that episode and gets no alias.
        val held = mapOf("555" to alias("555", EpisodeAliasState.TRUSTED))
        assertTrue(switched(listOf(reference("555"), reference("-71", audio = AUDIO_A)), held, rss).links.isEmpty())
    }

    @Test
    fun sameTitleIgnoresCaseSpacesAndPunctuationButNeverMatchesBlanks() {
        assertTrue(ListenerStateMoves.sameTitle("Episode 1: The Start", "episode 1 - the start"))
        assertFalse(ListenerStateMoves.sameTitle("Episode 1", "Episode 2"))
        assertFalse(ListenerStateMoves.sameTitle("", ""))
        assertFalse(ListenerStateMoves.sameTitle(null, "Episode 1"))
    }

    private fun switched(
        references: List<LocalCatalogListenerReference>,
        aliases: Map<String, EpisodeAliasEntity>,
        rss: List<RssEpisodeEntity>,
        feedProven: Boolean = true,
    ): SwitchedShowLinkPlan = ListenerStateMoves.switchedShowLinks(SHOW, references, aliases, rss, epNs = SHOW.toLong(), feedProven = feedProven, nowMs = NOW)

    private fun reference(id: String, audio: String? = null, title: String? = "Episode $id",) =
        LocalCatalogListenerReference(episodeId = id, enclosureUrl = audio, title = title, publishedDate = null)

    private fun alias(id: String, state: String, key: String? = KEY, owner: Boolean = false,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = if (key == null) null else RECIPE,
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = 1L,
    )

    private fun catalogRow(id: String) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        guid = KEY,
        title = "The same episode",
        description = "Description",
        audioUrl = "https://cdn.example.com/$id.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 1_000L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = "audio/mpeg",
    )

    private fun rssEpisode(id: String, guid: String, audio: String = "https://cdn.example.com/$id.mp3", title: String = "Feed $id",) = RssEpisodeEntity(
        episodeId = id,
        podcastId = "rss:abc",
        guid = guid,
        title = title,
        description = "",
        audioUrl = audio,
        imageUrl = null,
        duration = 60,
        publishedDate = 1_000L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = "audio/mpeg",
    )

    @Suppress("LongParameterList")
    private fun history(
        id: String,
        liked: Boolean = false,
        likedAt: Long = 0L,
        completed: Boolean = false,
        playedAt: Long = 0L,
        manual: Boolean = false,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Old title",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example.com/1.mp3",
        podcastName = "Show",
        progressMs = 0L,
        durationMs = 100L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
        isManualCompletion = manual,
    )

    private companion object {
        const val SHOW = "9926"
        const val KEY = "guid-a"
        const val RECIPE = 4_503_599_627_370_500L
        const val NOW = 1_234L
        const val AUDIO_A = "https://cdn.example.com/a.mp3"
        const val AUDIO_B = "https://cdn.example.com/b.mp3"
    }
}
