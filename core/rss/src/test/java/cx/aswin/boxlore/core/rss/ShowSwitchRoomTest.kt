package cx.aswin.boxlore.core.rss

import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching a Podcast Index show to its RSS feed through Room (docs/EPISODE_IDS.md §5): no listener
 * state moves and no ID changes; the feed's episodes are linked to the show's episodes by alias, so
 * they read the old IDs' projected state, write progress to the episode's owner and play the old
 * ID's download, and following the Podcast Index show again keeps everything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowSwitchRoomTest : RoomCatalogTestBase() {
    private val history get() = database.listeningHistoryDao()
    private val store get() = database.episodeListenerStore()

    @After
    fun clearRepository() {
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun switchingAShowToItsFeedMovesNothingAndTheFeedsEpisodesReadTheOldState() = runBlocking {
        val seeded = seedShowWithListening()
        val before = snapshot(seeded)

        switchToFeed()

        // Nothing moved: every row stays under its stored ID and show.
        assertEquals(before, snapshot(seeded))
        for (rssId in RSS_IDS) {
            assertNull(history.getHistoryItem(rssId))
            assertNull(database.downloadedEpisodeDao().getDownload(rssId))
            assertTrue(database.listeningSessionDao().getSessionsForEpisode(rssId).isEmpty())
            assertTrue(database.listeningRollupDao().getRollupsForEpisode(rssId).isEmpty())
        }
        assertEquals(listOf(seeded.first), database.queueDao().getAllQueueItemsSync().map { it.episodeId })

        // The feed's episodes are linked by alias under the Podcast Index show.
        assertEquals(link(RSS_G1, "g1", EpisodeAliasState.TRUSTED), alias(RSS_G1)?.copy(createdAt = 0L))
        assertEquals(link(RSS_G2, "g2", EpisodeAliasState.TRUSTED), alias(RSS_G2)?.copy(createdAt = 0L))
        // Matched only by its audio address from an ID with no key: ambiguous, so it reads nothing.
        assertEquals(link(RSS_G3, "g3", EpisodeAliasState.AMBIGUOUS), alias(RSS_G3)?.copy(createdAt = 0L))
        assertNull(alias(RSS_G4))

        // The feed's episodes read the old IDs' projected state.
        val g1 = store.projectedHistory(RSS_G1)!!
        assertEquals(PROGRESS, g1.progressMs)
        assertTrue(g1.isLiked)
        assertTrue(store.projectedHistory(RSS_G2)!!.isCompleted)
        assertNull(store.projectedHistory(RSS_G3))
        assertEquals(PROGRESS, store.projectRowsByMember(history.getRecentHistoryList(10)).single { it.episodeId == RSS_G1 }.progressMs)
        assertTrue(RSS_G1 in store.projectedLikedIds(flowOf(history.getLikedEpisodesList())).first())
        assertTrue(RSS_G2 in store.projectCompletedIds(history.getCompletedEpisodeIds()))
        // Progress goes to the episode's owner, and offline playback uses the old ID's download.
        assertEquals(seeded.first, store.historyId(RSS_G1))
        assertEquals(seeded.first, store.downloadHolder(RSS_G1))
        assertNull(store.downloadHolder(RSS_G3))

        // The Podcast Index show is retired in favour of the feed.
        assertFalse(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertEquals(SHOW, database.podcastDao().getPodcast(RSS_SHOW)!!.linkedPodcastIndexId)
    }

    @Test
    fun followingTheIndexShowAgainAfterASwitchKeepsEverythingUnderItsIds() = runBlocking {
        val seeded = seedShowWithListening()
        val before = snapshot(seeded)
        val projectedBefore = store.projectedHistory(seeded.first)
        switchToFeed()
        val links = RSS_IDS.associateWith { alias(it) }

        // Switching again changes nothing.
        switchToFeed()
        assertEquals(links, RSS_IDS.associateWith { alias(it) })

        // The listener follows the Podcast Index show again; its catalog was evicted and is read again.
        database.podcastDao().upsert(database.podcastDao().getPodcast(RSS_SHOW)!!.copy(isSubscribed = false))
        follow(SHOW)
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        // The catalog takes back its own IDs, never a linked feed ID, and every row is where it was.
        assertEquals(mapOf("g1" to seeded.first, "g2" to seeded.second, "g3" to recipe(SHOW, "g3")), rows(SHOW))
        assertEquals(before, snapshot(seeded))
        assertEquals(projectedBefore, store.projectedHistory(seeded.first))
        assertEquals(seeded.first, store.historyId(seeded.first))
        assertEquals(seeded.first, store.downloadHolder(seeded.first))
        assertEquals(listOf(seeded.first), database.queueDao().getAllQueueItemsSync().map { it.episodeId })
        assertEquals(links, RSS_IDS.associateWith { alias(it) })
    }

    @Test
    fun listeningTheFeedShowHeldBeforeTheSwitchStaysAndReadsAsOneEpisode() = runBlocking {
        val seeded = seedShowWithListening()
        // The feed show was followed and played before the switch: later than the old ID.
        history.upsert(listening(RSS_G1, RSS_SHOW, progress = 900L, playedAt = 90L))

        switchToFeed()

        assertEquals(900L, history.getHistoryItem(RSS_G1)!!.progressMs)
        assertEquals(PROGRESS, history.getHistoryItem(seeded.first)!!.progressMs)
        // One episode: progress from the later play, the like from the old ID's decision.
        val projected = store.projectedHistory(seeded.first)!!
        assertEquals(900L, projected.progressMs)
        assertTrue(projected.isLiked)
        assertEquals(1, store.projectRows(history.getRecentHistoryList(10)).count { it.episodeId in setOf(seeded.first, RSS_G1) })
    }

    @Test
    fun aCatalogReadNeverTakesALinkedFeedIdEvenWhenItOwnsTheEpisode() = runBlocking {
        follow(SHOW)
        feed.items = listOf(Item("g1"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        val g1 = recipe(SHOW, "g1")
        // The old ID is only queued; the feed show's copy of the episode holds the listening.
        queue(g1, SHOW, audio = "https://cdn.example/g1.mp3", title = "Ep g1")
        database.podcastDao().upsert(
            PodcastEntity(podcastId = RSS_SHOW, title = "Feed show", author = "", imageUrl = "", description = "", isSubscribed = true, sourceType = PodcastEntity.SOURCE_RSS),
        )
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "g1")))
        history.upsert(listening(RSS_G1, RSS_SHOW, progress = 900L, playedAt = 90L))
        switchToFeed()
        // Ownership moves to the linked feed ID: it holds the listening and the old owner holds none.
        assertEquals(RSS_G1, store.historyId(RSS_G1))
        assertTrue(alias(RSS_G1)!!.owner)

        follow(SHOW)
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to g1), rows(SHOW))
        assertEquals(900L, store.projectedHistory(g1)!!.progressMs)
    }

    /** A followed Podcast Index show read from its feed, with listening under its old IDs. Returns the g1 and g2 IDs. */
    private suspend fun seedShowWithListening(): Pair<String, String> {
        follow(SHOW)
        feed.items = listOf(Item("g1"), Item("g2"), Item("g3"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        val g1 = recipe(SHOW, "g1")
        val g2 = recipe(SHOW, "g2")
        history.upsert(listening(g1, SHOW, progress = PROGRESS, playedAt = 30L, liked = true))
        history.upsert(listening(g2, SHOW, completed = true, playedAt = 20L))
        // Played from search before the show was followed: an ID with no alias.
        history.upsert(listening(PLAYED_FROM_SEARCH, SHOW, playedAt = 10L, audio = "https://cdn.example/g3.mp3"))
        completedDownload(g1, SHOW, "Ep g1")
        queue(g1, SHOW, audio = "https://cdn.example/g1.mp3", title = "Ep g1")
        database.listeningSessionDao().upsertSession(ListeningSessionEntity("s1", g1, SHOW, 1L, 2L, 1_000L, false, 20_000L, 0))
        database.listeningRollupDao().upsertRollup(ListeningRollupEntity(20_000L, g1, SHOW, 1_000L, 1, 0, 2L))
        // An ID whose alias is ambiguous: nothing may be linked to it.
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(EpisodeAliasEntity(AMBIGUOUS_ID, SHOW, "g4", null, EpisodeAliasState.AMBIGUOUS, false, EpisodeAliasProvenance.UPGRADE_CATALOG, 1L)),
        )
        history.upsert(listening(AMBIGUOUS_ID, SHOW, playedAt = 5L, audio = "https://cdn.example/g4.mp3", title = "Ep g4"))

        database.podcastDao().upsert(
            PodcastEntity(podcastId = RSS_SHOW, title = "Feed show", author = "", imageUrl = "", description = "", isSubscribed = true, sourceType = PodcastEntity.SOURCE_RSS),
        )
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "g1"), rssEpisode(RSS_G2, "g2"), rssEpisode(RSS_G3, "g3"), rssEpisode(RSS_G4, "g4")))
        return g1 to g2
    }

    private suspend fun switchToFeed() {
        RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database).confirmPodcastIndexLink(RSS_SHOW, SHOW)
    }

    /** Every listener row and alias of the Podcast Index show's IDs. */
    private suspend fun snapshot(seeded: Pair<String, String>): List<Any?> {
        val ids = listOf(seeded.first, seeded.second, recipe(SHOW, "g3"), PLAYED_FROM_SEARCH, AMBIGUOUS_ID)
        return ids.flatMap { id ->
            listOf(
                history.getHistoryItem(id),
                database.downloadedEpisodeDao().getDownload(id),
                database.listeningSessionDao().getSessionsForEpisode(id),
                database.listeningRollupDao().getRollupsForEpisode(id),
                alias(id),
            )
        } + listOf(database.queueDao().getAllQueueItemsSync())
    }

    private fun link(id: String, key: String, state: String) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = recipe(SHOW, key).toLong(),
        state = state,
        owner = false,
        provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK,
        createdAt = 0L,
    )

    @Suppress("LongParameterList")
    private fun listening(
        id: String,
        show: String,
        progress: Long = 0L,
        playedAt: Long,
        liked: Boolean = false,
        completed: Boolean = false,
        audio: String = "https://cdn.example/$id.mp3",
        title: String = "Ep $id",
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = title,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = audio,
        podcastName = "Show",
        progressMs = progress,
        durationMs = 60_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = if (liked) playedAt else 0L,
        lastPlayedAt = playedAt,
    )

    private fun rssEpisode(id: String, guid: String) = RssEpisodeEntity(
        episodeId = id,
        podcastId = RSS_SHOW,
        guid = guid,
        title = "Ep $guid",
        description = "",
        audioUrl = "https://cdn.example/$guid.mp3",
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
        enclosureType = "audio/mpeg",
    )

    private companion object {
        const val RSS_SHOW = "rss:feedshow"
        const val RSS_G1 = "-101"
        const val RSS_G2 = "-102"
        const val RSS_G3 = "-103"
        const val RSS_G4 = "-104"
        val RSS_IDS = listOf(RSS_G1, RSS_G2, RSS_G3, RSS_G4)
        const val PLAYED_FROM_SEARCH = "7770001"
        const val AMBIGUOUS_ID = "7770002"
        const val PROGRESS = 400L
    }
}
