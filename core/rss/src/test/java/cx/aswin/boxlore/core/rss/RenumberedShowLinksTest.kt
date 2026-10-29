package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.LocalEpisodeLinkRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matching rules of [RenumberedShowLinks] that the Room test doesn't reach: queue and download
 * rows matched by title and date, group settings merged into an episode that has its own, and a
 * key two new episodes share.
 */
class RenumberedShowLinksTest {
    @Test
    fun aQueueOrDownloadRowJoinsByTitleAndADateWithinTwoDays() {
        val rows = listOf(catalogRow("new-1", "guid-1", title = "Episode 1", publishedDate = PUBLISHED))
        val inside = reference("-11", title = " Episode 1 ", publishedDate = PUBLISHED + LocalCatalogOrphanRematch.DATE_WINDOW_SECONDS)
        val outside = reference("-12", title = "Episode 1", publishedDate = PUBLISHED - LocalCatalogOrphanRematch.DATE_WINDOW_SECONDS - 1)

        val links = plan(references = listOf(inside, outside), rows = rows)

        assertEquals(listOf("-11"), links.map { it.aliasId })
        assertEquals("guid-1", links.single().catalogKey)
        assertEquals(EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH, links.single().provenance)
    }

    @Test
    fun aTitleTwoNewEpisodesShareWithinTheWindowJoinsNeither() {
        val rows =
            listOf(
                catalogRow("new-1", "guid-1", title = "Trailer", publishedDate = PUBLISHED),
                catalogRow("new-2", "guid-2", title = "Trailer", publishedDate = PUBLISHED + 60),
            )

        assertTrue(plan(references = listOf(reference("-11", title = "Trailer", publishedDate = PUBLISHED)), rows = rows).isEmpty())
    }

    @Test
    fun groupSettingsMergeIntoAnEpisodeThatHasItsOwnKeepingEachLatestDecision() {
        val former = EpisodeGroupStateEntity(OLD, "guid-1", likedAt = 9_000L, completedAt = 2_000L, completeClearedAt = 1_000L, version = 3L)
        val current = EpisodeGroupStateEntity(NEW, "guid-1", likedAt = 4_000L, likeClearedAt = 5_000L, completedAt = 1_500L, version = 7L)

        val merged = RenumberedShowLinks.mergedGroupState(former, current, NEW, "guid-1")

        assertEquals(EpisodeGroupStateEntity(NEW, "guid-1", likedAt = 9_000L, likeClearedAt = 5_000L, completedAt = 2_000L, completeClearedAt = 1_000L, version = 8L), merged)
    }

    @Test
    fun aKeyTwoNewEpisodesShareJoinsNoGroup() {
        val rows = listOf(catalogRow("new-1", "guid-1"), catalogRow("new-2", " guid-1"))
        val group = listOf(alias("-11", OLD, "guid-1"))

        val moved =
            RenumberedShowLinks.joinedGroup(
                formerGroup = group,
                key = "guid-1",
                currentId = NEW,
                catalogRows = rows,
                rowAliasesById = rows.associate { it.episodeId to alias(it.episodeId, NEW, "guid-1") },
                catalogHeldIds = emptySet(),
                epNs = NEW.toLong(),
            )

        assertTrue(moved.isEmpty())
    }

    private fun plan(references: List<LocalCatalogListenerReference>, rows: List<LocalEpisodeLinkRow>) = RenumberedShowLinks.plan(
        formerId = OLD,
        currentId = NEW,
        references = references,
        aliasesById = emptyMap(),
        catalogRows = rows,
        rowAliasesById = rows.associate { it.episodeId to alias(it.episodeId, NEW, it.guid.trim()) },
        catalogHeldIds = emptySet(),
        epNs = NEW.toLong(),
        nowMs = 1L,
    )

    private fun catalogRow(id: String, guid: String, title: String = "Episode $id", publishedDate: Long = PUBLISHED) =
        LocalEpisodeLinkRow(episodeId = id, guid = guid, audioUrl = "https://audio.example/$id.mp3", title = title, publishedDate = publishedDate, legacy = false)

    /** A queue or download row: no audio address, a title and a date. */
    private fun reference(id: String, title: String, publishedDate: Long) =
        LocalCatalogListenerReference(episodeId = id, enclosureUrl = null, title = title, publishedDate = publishedDate)

    private fun alias(id: String, show: String, key: String) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = show,
        catalogKey = key,
        recipeId = null,
        state = EpisodeAliasState.TRUSTED,
        provenance = EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH,
    )

    private companion object {
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val PUBLISHED = 1_700_000_000L
    }
}
