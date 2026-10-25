package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The narrowed re-key paths (docs/EPISODE_IDS.md §5): the orphan rematch moves only unresolved
 * legacy IDs, never resurrects a cleared like, and records the moved ID as an alias.
 */
class ListenerStateMovesTest {
    @Test
    fun theOrphanRematchLeavesTrustedAndAmbiguousIdsAlone() {
        val aliases = listOf(alias("-1", EpisodeAliasState.TRUSTED), alias("-2", EpisodeAliasState.AMBIGUOUS), alias("-3", EpisodeAliasState.UNRESOLVED, key = null))
        val settled = ListenerStateMoves.settledIds(aliases)
        assertEquals(setOf("-1", "-2"), settled)
        assertEquals(setOf("-2"), ListenerStateMoves.ambiguousIds(aliases))

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
    fun switchingAShowToItsFeedMergesTheLikeByLatestDecisionAndKeepsCompletion() {
        val rssEpisode = rssEpisode("-77")
        val rssShow = PodcastEntity(podcastId = "rss:abc", title = "Feed show", author = "Host", imageUrl = "https://img/rss.jpg", description = "")
        val piRow = history("555", liked = true, likedAt = 10L, completed = true, playedAt = 30L)
        val rssRow = history("-77", liked = false, likedAt = 20L, playedAt = 40L)

        val merged = ListenerStateMoves.linkedHistoryRow(piRow, rssRow, rssEpisode, rssShow)

        assertEquals("-77", merged.episodeId)
        assertEquals("rss:abc", merged.podcastId)
        assertFalse(merged.isLiked)
        assertEquals(20L, merged.likedAt)
        assertTrue(merged.isCompleted)
        assertEquals(40L, merged.lastPlayedAt)
        assertTrue(ListenerStateMoves.linkedHistoryRow(piRow, null, rssEpisode, rssShow).isLiked)
    }

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

    private fun rssEpisode(id: String) = RssEpisodeEntity(
        episodeId = id,
        podcastId = "rss:abc",
        guid = "guid-x",
        title = "Feed title",
        description = "",
        audioUrl = "https://cdn.example.com/feed.mp3",
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
    }
}
