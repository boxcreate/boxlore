package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalEpisodeCollisionGuardTest {
    private val namespace = "rss:shared-feed"

    /** A `local_episodes` stand-in: episode ID → owner, replaced like Room's REPLACE. */
    private val table = linkedMapOf<String, LocalEpisodeOwner>()
    private val owners: suspend (List<String>) -> List<LocalEpisodeOwner> = { ids -> ids.mapNotNull(table::get) }

    private fun store(rows: List<LocalEpisodeEntity>) {
        for (r in rows) table[r.episodeId] = LocalEpisodeOwner(r.episodeId, r.podcastId, r.guid)
    }

    @Test
    fun anotherShowsIdGetsAReservedIdAndBothEpisodesSurvive() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val written = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "200", key = "guid-a")), namespace, owners)

        val reserved = written.single().episodeId
        assertNotEquals("-11", reserved)
        assertTrue(reserved.toLong() < 0L, "reserved IDs stay negative")
        assertEquals("200", written.single().podcastId)
        store(written)
        assertEquals("100", table.getValue("-11").podcastId)
        assertEquals("200", table.getValue(reserved).podcastId)
    }

    @Test
    fun theSameShowAndKeyKeepsUpdatingItsRow() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val written = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "100", key = "guid-a", title = "Renamed")), namespace, owners)
        assertEquals("-11", written.single().episodeId)
        assertEquals("Renamed", written.single().title)
    }

    @Test
    fun theSameShowWithAnotherKeyIsAClashToo() = runTest {
        store(listOf(row("77", show = "100", key = "old-guid")))
        val written = LocalEpisodeCollisionGuard.resolve(listOf(row("77", show = "100", key = "new-guid")), namespace, owners)
        assertNotEquals("77", written.single().episodeId)
    }

    @Test
    fun twoRowsOfOneBatchNeverShareAnId() = runTest {
        val written =
            LocalEpisodeCollisionGuard.resolve(
                listOf(row("-11", show = "100", key = "guid-a"), row("-11", show = "100", key = "guid-b")),
                namespace,
                owners,
            )
        assertEquals(2, written.map { it.episodeId }.toSet().size)
        assertEquals("-11", written.first().episodeId)
    }

    @Test
    fun aReservedIdIsStableAcrossRefreshes() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        val first = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "200", key = "guid-a")), namespace, owners)
        store(first)
        val again = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "200", key = "guid-a")), namespace, owners)
        assertEquals(first.single().episodeId, again.single().episodeId)
    }

    @Test
    fun aRowWithNoFreeIdIsLeftOutInsteadOfOverwriting() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        for (attempt in 1..LocalEpisodeCollisionGuard.MAX_RESERVE_ATTEMPTS) {
            val id = StickyEpisodeIdentity.reservedEpisodeId(namespace, "200", "guid-a", attempt)
            table[id] = LocalEpisodeOwner(id, "300", "other")
        }
        val written = LocalEpisodeCollisionGuard.resolve(listOf(row("-11", show = "200", key = "guid-a")), namespace, owners)
        assertTrue(written.isEmpty())
    }

    @Test
    fun theLegacyUpgradeCheckIgnoresItsOwnShowsRows() = runTest {
        store(listOf(row("-11", show = "100", key = "guid-a")))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-11", show = "100", key = "guid-x")), "100", owners))
        assertTrue(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-11", show = "200", key = "guid-a")), "200", owners))
        assertFalse(LocalEpisodeCollisionGuard.takesAnotherShowsId(listOf(row("-12", show = "200", key = "guid-b")), "200", owners))
    }

    private fun row(id: String, show: String, key: String, title: String = "Ep",) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = title,
        description = "",
        audioUrl = "https://cdn.example/$key.mp3",
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
}
