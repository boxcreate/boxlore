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
import cx.aswin.boxlore.core.model.Podcast
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
 * ID's download, and following the Podcast Index show again keeps everything. Links are written
 * again after a restore, a re-follow and a feed read, and an ambiguous link turns trusted once
 * exact evidence exists.
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
        // Another show's rows that look like a feed episode by audio address and title are never linked.
        history.upsert(listening("8880001", OTHER_SHOW, playedAt = 3L, audio = "https://cdn.example/g4.mp3", title = "Ep g4"))
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
        // Played before the show was followed (no alias), its audio address names g3 alone: the old
        // ID is settled under g3's key first, then the feed episode joins it.
        assertEquals(link(RSS_G3, "g3", EpisodeAliasState.TRUSTED), alias(RSS_G3)?.copy(createdAt = 0L))
        assertEquals(settled(PLAYED_FROM_SEARCH, "g3", owner = false), alias(PLAYED_FROM_SEARCH)?.copy(createdAt = 0L))
        // g4 matches only an ID whose alias is ambiguous, and another show's rows: no link.
        assertNull(alias(RSS_G4))
        assertNull(alias("8880001"))

        // The feed's episodes read the old IDs' projected state.
        val g1 = store.projectedHistory(RSS_G1)!!
        assertEquals(PROGRESS, g1.progressMs)
        assertTrue(g1.isLiked)
        assertTrue(store.projectedHistory(RSS_G2)!!.isCompleted)
        assertEquals(10L, store.projectedHistory(RSS_G3)!!.lastPlayedAt)
        assertEquals(PROGRESS, store.projectRowsByMember(history.getRecentHistoryList(10)).single { it.episodeId == RSS_G1 }.progressMs)
        assertTrue(RSS_G1 in store.projectedLikedIds(flowOf(history.getLikedEpisodesList())).first())
        assertTrue(RSS_G2 in store.projectCompletedIds(history.getCompletedEpisodeIds()))
        // Progress goes to the episode's owner, and offline playback uses the old ID's download.
        assertEquals(seeded.first, store.historyId(RSS_G1))
        assertEquals(PLAYED_FROM_SEARCH, store.historyId(RSS_G3))
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
        database.podcastDao().upsert(rssShow())
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "g1")))
        history.upsert(listening(RSS_G1, RSS_SHOW, progress = 900L, playedAt = 90L))
        switchToFeed()
        // The linked feed ID joined as a non-owner; ownership then follows the owner rule: it moves
        // to the feed ID, which holds the listening, because the old owner holds none.
        assertEquals(RSS_G1, store.historyId(RSS_G1))
        assertTrue(alias(RSS_G1)!!.owner)

        follow(SHOW)
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to g1), rows(SHOW))
        assertEquals(900L, store.projectedHistory(g1)!!.progressMs)
    }

    @Test
    fun anUpgradedIdKeyedByItsAudioAddressJoinsTheFeedEpisodeItNamesExactly() = runBlocking {
        seedShowWithListening()
        // A phone-minted ID the v39 upgrade left unresolved because its key was its own audio address.
        seedUnresolved(ADDRESS_KEYED, SHOW, EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY)
        history.upsert(listening(ADDRESS_KEYED, SHOW, progress = 700L, playedAt = 40L, audio = "https://cdn.example/g5.mp3", title = "Old title"))
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G5, "g5")))

        switchToFeed()

        // No catalog row holds g5, so the settled old ID owns the episode.
        assertEquals(settled(ADDRESS_KEYED, "g5", owner = true), alias(ADDRESS_KEYED)?.copy(createdAt = 0L))
        assertEquals(link(RSS_G5, "g5", EpisodeAliasState.TRUSTED), alias(RSS_G5)?.copy(createdAt = 0L))
        assertEquals(700L, store.projectedHistory(RSS_G5)!!.progressMs)
        assertEquals(ADDRESS_KEYED, store.historyId(RSS_G5))
        // The old row is still where it was, under its own ID and show.
        assertEquals(SHOW, history.getHistoryItem(ADDRESS_KEYED)!!.podcastId)
    }

    @Test
    fun aLaterLinkTurnsAnAmbiguousLinkTrustedOnceTheOldIdHasExactEvidence() = runBlocking {
        seedShowWithListening()
        // Matched only by its title: the link is ambiguous and reads nothing.
        history.upsert(listening(TITLE_ONLY, SHOW, progress = 600L, playedAt = 35L, audio = "https://elsewhere.example/5.mp3", title = "Ep g5"))
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G5, "g5")))
        switchToFeed()
        assertEquals(link(RSS_G5, "g5", EpisodeAliasState.AMBIGUOUS), alias(RSS_G5)?.copy(createdAt = 0L))
        assertNull(store.projectedHistory(RSS_G5))

        // A read of the Podcast Index show's feed later proves the old ID's key.
        seedAlias(TITLE_ONLY, SHOW, "g5")
        val rss = repository()
        rss.relinkSwitchedShow(RSS_SHOW)

        assertEquals(link(RSS_G5, "g5", EpisodeAliasState.TRUSTED), alias(RSS_G5)?.copy(createdAt = 0L))
        assertEquals(600L, store.projectedHistory(RSS_G5)!!.progressMs)
        // Linking again never changes a trusted link.
        rss.relinkSwitchedShow(RSS_SHOW)
        assertEquals(EpisodeAliasState.TRUSTED, alias(RSS_G5)!!.state)
    }

    @Test
    fun aConfirmedLookalikeShowTrustsAKeyOnlyWithASecondSignal() = runBlocking {
        follow(SHOW)
        feed.items = listOf(Item("1"), Item("2"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        // Two rows of the Podcast Index show, with nothing but their key in common with the feed.
        history.upsert(listening(recipe(SHOW, "1"), SHOW, playedAt = 20L, audio = "https://pi.example/1.mp3", title = "Ep one"))
        history.upsert(listening(recipe(SHOW, "2"), SHOW, playedAt = 21L, audio = "https://pi.example/2.mp3", title = "The second one"))
        // The listener confirmed a feed that only looked the same: another address, GUIDs "1" and "2".
        database.podcastDao().upsert(rssShow(feedUrl = "https://elsewhere.example/feed.xml"))
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "1").copy(title = "Ep one"), rssEpisode(RSS_G2, "2")))

        switchToFeed()

        // The same title backs key "1"; key "2" alone proves nothing.
        assertEquals(EpisodeAliasState.TRUSTED, alias(RSS_G1)!!.state)
        assertEquals(EpisodeAliasState.AMBIGUOUS, alias(RSS_G2)!!.state)
        assertNull(store.projectedHistory(RSS_G2))
    }

    @Test
    fun addingAFollowedShowsFeedByItsAddressSwitchesItWithoutMovingAnything() = runBlocking {
        val oldIds = seedShowFollowedByAddress()
        val before = oldIds.map { history.getHistoryItem(it) }
        val rss = repository()

        val added = rss.addSubscription(feedUrl(SHOW))

        val rssShow = added.podcast.id
        assertEquals(SHOW, added.linkedPodcastIndexId)
        assertFalse(database.podcastDao().getPodcast(SHOW)!!.isSubscribed)
        assertEquals(before, oldIds.map { history.getHistoryItem(it) })
        val rssIds = database.rssEpisodeDao().getAllNewest(rssShow).associate { it.guid to it.episodeId }
        for ((key, oldId) in listOf("g1", "g2").zip(oldIds)) {
            val link = alias(rssIds.getValue(key))!!
            assertEquals(EpisodeAliasState.TRUSTED, link.state)
            assertEquals(EpisodeAliasProvenance.RSS_SOURCE_LINK, link.provenance)
            assertEquals(oldId, store.historyId(rssIds.getValue(key)))
        }
        assertEquals(PROGRESS, store.projectedHistory(rssIds.getValue("g1"))!!.progressMs)
    }

    @Test
    fun unfollowingTheFeedShowAndAddingItAgainKeepsItsLinks() = runBlocking {
        seedShowFollowedByAddress()
        val rss = repository()
        val rssShow = rss.addSubscription(feedUrl(SHOW)).podcast.id
        val rssIds = database.rssEpisodeDao().getAllNewest(rssShow).map { it.episodeId }
        val links = rssIds.associateWith { alias(it) }

        // Unfollowing an RSS show deletes its episodes and keeps its row (as SubscriptionRepository does).
        val followed = database.podcastDao().getPodcast(rssShow)!!
        database.podcastDao().upsert(followed.copy(isSubscribed = false, subscribedAt = 0L, unsubscribedAt = 5L))
        database.podcastDao().deleteRssEpisodes(rssShow)
        rss.addSubscription(feedUrl(SHOW))

        assertEquals(rssIds, database.rssEpisodeDao().getAllNewest(rssShow).map { it.episodeId })
        assertEquals(links, rssIds.associateWith { alias(it) })
        assertEquals(SHOW, database.podcastDao().getPodcast(rssShow)!!.linkedPodcastIndexId)
        assertEquals(PROGRESS, store.projectedHistory(rssIds.first { alias(it)?.catalogKey == "g1" })!!.progressMs)
    }

    @Test
    fun aRestoredFeedShowLinksItsEpisodesAgain() = runBlocking {
        // A restore brings back the feed show with its link and the old rows, but no aliases: the
        // Podcast Index show's catalog was never read on this device.
        val oldG1 = "7770011"
        val oldG2 = "7770012"
        history.upsert(listening(oldG1, SHOW, progress = PROGRESS, playedAt = 30L, audio = "https://cdn.example/g1.mp3"))
        history.upsert(listening(oldG2, SHOW, playedAt = 20L, completed = true, audio = "https://old-cdn.example/g2.mp3", title = "Ep g2"))
        database.podcastDao().upsert(rssShow().copy(linkedPodcastIndexId = SHOW))
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "g1"), rssEpisode(RSS_G2, "g2")))

        repository().relinkSwitchedShow(RSS_SHOW)

        // The exact audio address proves g1; g2 matches only by title, so its link waits as ambiguous.
        assertEquals(settled(oldG1, "g1", owner = true), alias(oldG1)?.copy(createdAt = 0L))
        assertEquals(link(RSS_G1, "g1", EpisodeAliasState.TRUSTED), alias(RSS_G1)?.copy(createdAt = 0L))
        assertEquals(PROGRESS, store.projectedHistory(RSS_G1)!!.progressMs)
        assertEquals(EpisodeAliasState.AMBIGUOUS, alias(RSS_G2)!!.state)
        assertNull(alias(oldG2))
    }

    @Test
    fun restoringAFeedShowThatKeepsItsLinkLinksItsEpisodes() = runBlocking {
        val oldG1 = "7770021"
        history.upsert(listening(oldG1, SHOW, progress = PROGRESS, playedAt = 30L, audio = "https://cdn.example/g1.mp3"))
        // The feed show's row survived (unfollowed before, or restored first) with its link.
        database.podcastDao().upsert(rssShow().copy(isSubscribed = false, linkedPodcastIndexId = SHOW))
        feed.items = listOf(Item("g1"))

        repository().restoreSubscription(RSS_FEED_URL, RSS_SHOW)

        val rssG1 = database.rssEpisodeDao().getAllNewest(RSS_SHOW).single().episodeId
        assertEquals(EpisodeAliasState.TRUSTED, alias(rssG1)!!.state)
        assertEquals(PROGRESS, store.projectedHistory(rssG1)!!.progressMs)
    }

    @Test
    fun theRssFollowUpgradeNeverRunsOverAShowTheFeedReplaced() = runBlocking {
        seedShowWithListening()
        val rss = repository()
        rss.confirmPodcastIndexLink(RSS_SHOW, SHOW)
        val links = RSS_IDS.associateWith { alias(it) }
        val owners = listOf("g1", "g2", "g3").associateWith { key -> database.episodeIdentityDao().aliasesForKey(SHOW, key).filter { it.owner }.map { it.aliasId } }
        feed.items = listOf(Item("g1"), Item("g2"), Item("g3"), Item("g4"))
        val snapshot = rss.legacySubscriptionRepair.inspect(RSS_SHOW).getOrThrow()

        val outcome = rss.legacySubscriptionRepair.upgrade(RSS_SHOW, Podcast(id = SHOW, title = "Show", artist = "", imageUrl = "", feedUrl = feedUrl(SHOW)), snapshot)

        // The Podcast Index show still holds the listening, so the upgrade stops before writing.
        assertEquals(LegacyRssUpgradeOutcome.TARGET_ALREADY_IN_USE, outcome)
        assertEquals(links, RSS_IDS.associateWith { alias(it) })
        assertEquals(owners, listOf("g1", "g2", "g3").associateWith { key -> database.episodeIdentityDao().aliasesForKey(SHOW, key).filter { it.owner }.map { it.aliasId } })
        assertEquals(true, database.podcastDao().getPodcast(RSS_SHOW)!!.isSubscribed)
    }

    /** A followed Podcast Index show read from its feed, with listening under its old IDs. Returns the g1 and g2 IDs. */
    private suspend fun seedShowWithListening(): Pair<String, String> {
        followWithFeed()
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

        database.podcastDao().upsert(rssShow())
        database.rssEpisodeDao().upsertAll(listOf(rssEpisode(RSS_G1, "g1"), rssEpisode(RSS_G2, "g2"), rssEpisode(RSS_G3, "g3"), rssEpisode(RSS_G4, "g4")))
        return g1 to g2
    }

    /** A followed Podcast Index show with its feed address, read from it, with listening on g1 and g2. Returns their IDs. */
    private suspend fun seedShowFollowedByAddress(): List<String> {
        followWithFeed()
        feed.items = listOf(Item("g1"), Item("g2"))
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        val ids = listOf(recipe(SHOW, "g1"), recipe(SHOW, "g2"))
        history.upsert(listening(ids[0], SHOW, progress = PROGRESS, playedAt = 30L, liked = true))
        history.upsert(listening(ids[1], SHOW, completed = true, playedAt = 20L))
        return ids
    }

    private suspend fun followWithFeed() {
        follow(SHOW)
        database.podcastDao().upsert(database.podcastDao().getPodcast(SHOW)!!.copy(feedUrl = feedUrl(SHOW)))
    }

    private fun repository() = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)

    private suspend fun switchToFeed() {
        repository().confirmPodcastIndexLink(RSS_SHOW, SHOW)
    }

    /** Every listener row of the Podcast Index show's IDs, and the aliases of the IDs a switch never settles. */
    private suspend fun snapshot(seeded: Pair<String, String>): List<Any?> {
        val ids = listOf(seeded.first, seeded.second, recipe(SHOW, "g3"), PLAYED_FROM_SEARCH, AMBIGUOUS_ID)
        return ids.flatMap { id ->
            listOf(
                history.getHistoryItem(id),
                database.downloadedEpisodeDao().getDownload(id),
                database.listeningSessionDao().getSessionsForEpisode(id),
                database.listeningRollupDao().getRollupsForEpisode(id),
                if (id == PLAYED_FROM_SEARCH) null else alias(id),
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

    private fun settled(id: String, key: String, owner: Boolean) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = recipe(SHOW, key).toLong(),
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.RSS_SOURCE_AUDIO_MATCH,
        createdAt = 0L,
    )

    private fun rssShow(feedUrl: String = feedUrl(SHOW)) = PodcastEntity(
        podcastId = RSS_SHOW,
        title = "Feed show",
        author = "",
        imageUrl = "",
        description = "",
        isSubscribed = true,
        sourceType = PodcastEntity.SOURCE_RSS,
        feedUrl = feedUrl,
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
        const val RSS_G5 = "-105"
        val RSS_IDS = listOf(RSS_G1, RSS_G2, RSS_G3, RSS_G4)
        const val PLAYED_FROM_SEARCH = "7770001"
        const val AMBIGUOUS_ID = "7770002"
        const val TITLE_ONLY = "7770003"
        const val ADDRESS_KEYED = "-5550001"
        const val PROGRESS = 400L
    }
}
