package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The orphan rematch after a real feed read, through Room (docs/EPISODE_IDS.md §5): it moves only
 * unresolved legacy negative IDs, after consulting catalog rows and aliases, and what it and the
 * read record agrees with the projected listener state (one owner, progress written to it, one
 * state per episode).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrphanRematchRoomTest : RoomCatalogTestBase() {
    private val history get() = database.listeningHistoryDao()
    private val store get() = database.episodeListenerStore()

    @Test
    fun anIdAnotherShowsCatalogRowHoldsIsNeverRematched() = runBlocking {
        // A build before the collision guard let another show reading the same feed take the row.
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-77", OTHER_SHOW, "g1")))
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("5001", SHOW, "g1")))
        val before = listening("-77", progress = 300L, playedAt = 30L, audio = "https://cdn.example/g1.mp3")
        history.upsert(before)
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to "5001"), rows(SHOW))
        assertEquals(before, history.getHistoryItem("-77"))
        assertNull(history.getHistoryItem("5001"))
        assertNull(alias("-77"))
    }

    @Test
    fun aStoredRowsNewAliasJoinsATrustedOrphansEpisodeAndNothingMoves() = runBlocking {
        // The episode's listening is under a trusted phone-minted ID whose catalog row was evicted;
        // a row stored later under another ID holds the same key and has no alias yet.
        follow(SHOW)
        seedAlias("-66", SHOW, "g2")
        val before = listening("-66", progress = 700L, playedAt = 40L, audio = "https://cdn.example/g2.mp3")
        history.upsert(before)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("5002", SHOW, "g2")))
        feed.items = listOf(Item("g2"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g2" to "5002"), rows(SHOW))
        assertEquals(before, history.getHistoryItem("-66"))
        assertNull(history.getHistoryItem("5002"))
        val joined = alias("5002")!!
        assertEquals(EpisodeAliasState.TRUSTED, joined.state)
        assertEquals("g2", joined.catalogKey)
        assertFalse(joined.owner)
        // One episode: the catalog row reads the listening, and progress goes to its owner.
        assertEquals(700L, store.projectedHistory("5002")!!.progressMs)
        assertEquals("-66", store.historyId("5002"))
        assertEquals(1, store.projectRows(history.getRecentHistoryList(10)).size)
    }

    @Test
    fun anUnresolvedOrphanMovesIntoItsEpisodesOwnerAndJoinsItsEpisode() = runBlocking {
        follow(SHOW)
        feed.items = listOf(Item("g1"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        val owner = recipe(SHOW, "g1")
        history.upsert(listening(owner, progress = 100L, playedAt = 10L, audio = "https://cdn.example/g1.mp3"))
        // A restored row under a phone-minted ID whose catalog row never came back.
        history.upsert(listening("-55", progress = 500L, playedAt = 60L, liked = true, audio = "https://cdn.example/g1.mp3"))
        seedUnresolved("-55", SHOW, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to owner), rows(SHOW))
        assertNull(history.getHistoryItem("-55"))
        val merged = history.getHistoryItem(owner)!!
        assertEquals(500L, merged.progressMs)
        assertEquals(60L, merged.lastPlayedAt)
        assertTrue(merged.isLiked)
        // An exact audio match: the moved ID joins the episode, so a later copy of it reads as one.
        val moved = alias("-55")!!
        assertEquals(EpisodeAliasState.TRUSTED, moved.state)
        assertEquals(EpisodeAliasProvenance.ORPHAN_REMATCH, moved.provenance)
        assertFalse(moved.owner)
        assertEquals(listOf(owner, "-55"), store.memberIds("-55"))
        assertEquals(owner, store.historyId("-55"))
        assertEquals(500L, store.projectedHistory("-55")!!.progressMs)
    }

    @Test
    fun anIdAdoptedByAListenerMatchOwnsItsEpisodeAndTakesALaterOrphan() = runBlocking {
        // Played from search before the follow: the first read keeps the Podcast Index ID.
        history.upsert(listening("5001", progress = 200L, playedAt = 20L, audio = "https://cdn.example/g1.mp3"))
        seedUnresolved("5001", SHOW, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        follow(SHOW)
        feed.items = listOf(Item("g1"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        assertEquals(EpisodeAliasProvenance.FEED_LISTENER_MATCH, alias("5001")!!.provenance)
        assertEquals("5001", store.historyId("5001"))

        // A restored copy under a phone-minted ID is rematched into it on the next read.
        history.upsert(listening("-55", progress = 800L, playedAt = 80L, audio = "https://cdn.example/g1.mp3"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to "5001"), rows(SHOW))
        assertNull(history.getHistoryItem("-55"))
        assertEquals(800L, history.getHistoryItem("5001")!!.progressMs)
        assertEquals(listOf("5001", "-55"), store.memberIds("-55"))
        assertEquals("5001", store.historyId("-55"))
    }

    @Suppress("LongParameterList")
    private fun listening(id: String, progress: Long, playedAt: Long, audio: String, liked: Boolean = false,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Ep $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = audio,
        podcastName = "Show",
        progressMs = progress,
        durationMs = 60_000L,
        isCompleted = false,
        isLiked = liked,
        likedAt = if (liked) playedAt else 0L,
        lastPlayedAt = playedAt,
    )
}
