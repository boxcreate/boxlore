package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.MemoryRenumberedShows
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JSON backup v7 on real Room: the identity block round trip (alias states, owners, provenance,
 * group settings, renumbered shows and the switched show's link), unresolved records, older
 * backups, refused conflicts, one owner per episode, and the OPML address (docs/EPISODE_IDS.md §4–§5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupIdentityRoomTest {
    private lateinit var source: BoxLoreDatabase
    private lateinit var target: BoxLoreDatabase
    private val sourceShows = MemoryRenumberedShows()
    private val targetShows = MemoryRenumberedShows()
    private val rssPodcastRepository = mock(RssPodcastRepository::class.java)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun aV7RoundTripKeepsAliasStatesOwnersProvenanceAndGroupSettings() = runTest {
        seedSource()
        stubRssRestore()

        val json = manager(source, sourceShows).exportLibraryAsJson()
        val result = manager(target, targetShows).importLibraryFromJson(json)

        val exported = JsonParser.parseString(json).asJsonObject
        assertEquals(7, exported["version"].asInt)
        assertEquals(5, exported["episodeIdentity"].asJsonObject["episodeAliases"].asJsonArray.size())
        assertEquals(2, result.first)
        val dao = target.episodeIdentityDao()
        assertEquals(source.episodeIdentityDao().allAliases(), dao.allAliases())
        assertEquals(listOf(PI_ID), dao.allAliases().filter { it.owner }.map { it.aliasId })
        assertEquals(EpisodeAliasProvenance.RSS_SOURCE_LINK, dao.getAlias(RSS_EPISODE)?.provenance)
        assertEquals(EpisodeAliasState.AMBIGUOUS, dao.getAlias(AMBIGUOUS_ID)?.state)
        assertEquals(source.episodeIdentityDao().allGroupStates(), dao.allGroupStates())
        assertEquals(mapOf(OLD_SHOW to NEW_SHOW), targetShows.all())
        // The switched show keeps its link, and is linked again once the history is in.
        assertEquals(SHOW, target.podcastDao().getPodcast(RSS_SHOW)?.linkedPodcastIndexId)
        verify(rssPodcastRepository).relinkSwitchedShow(RSS_SHOW)
        val history = target.listeningHistoryDao().getHistoryItem(PI_ID)!!
        assertEquals(1_000L, history.progressMs)
        assertEquals(50L, history.likedAt)
    }

    @Test
    fun unresolvedRecordsSurviveARestore() = runTest {
        seedSource()
        stubRssRestore()

        manager(target, targetShows).importLibraryFromJson(manager(source, sourceShows).exportLibraryAsJson())

        val unresolved = target.episodeIdentityDao().getAlias(UNRESOLVED_ID)!!
        assertEquals(EpisodeAliasState.UNRESOLVED, unresolved.state)
        assertNull(unresolved.catalogKey)
        assertNull(unresolved.recipeId)
        assertFalse(unresolved.owner)
        assertEquals(listOf(UNRESOLVED_ID), target.episodeIdentityDao().unresolvedAliases(SHOW).map { it.aliasId })
        assertEquals(500L, target.listeningHistoryDao().getHistoryItem(UNRESOLVED_ID)?.progressMs)
    }

    @Test
    fun aV6BackupStillImportsAndLeavesTheIdentityTablesAlone() = runTest {
        target.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias(PI_ID, owner = true)))
        val v6 =
            """
            {
              "version": 6,
              "subscriptions": [
                { "podcastId": "$PI_SHOW", "title": "Tech Talk", "author": "Host", "imageUrl": "", "feedUrl": "https://feeds.example.com/tech.xml",
                  "sourceType": "podcast_index", "subscribedAt": 1000, "notificationsEnabled": false, "autoDownloadEnabled": false }
              ],
              "history": [
                { "episodeId": "$PI_ID", "podcastId": "$SHOW", "episodeTitle": "Pilot", "podcastName": "Show", "progressMs": 700,
                  "durationMs": 60000, "isCompleted": false, "isLiked": true, "likedAt": 9, "lastPlayedAt": 30 }
              ],
              "directFeedOptIns": []
            }
            """.trimIndent()

        val result = manager(target, targetShows).importLibraryFromJson(v6)

        assertEquals(1, result.first)
        assertTrue(target.podcastDao().getPodcast(PI_SHOW)!!.isSubscribed)
        assertEquals(700L, target.listeningHistoryDao().getHistoryItem(PI_ID)?.progressMs)
        assertEquals(listOf(alias(PI_ID, owner = true)), target.episodeIdentityDao().allAliases())
        assertTrue(target.episodeIdentityDao().allGroupStates().isEmpty())
        assertTrue(targetShows.all().isEmpty())
    }

    @Test
    fun aRestoredAliasNamingAnotherEpisodeIsRefusedAndBothEpisodesSurvive() = runTest {
        seedSource()
        stubRssRestore()
        // This phone stores the backup's owner ID for another episode of the show.
        target.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias(PI_ID, key = "guid-z", owner = true)))
        target.listeningHistoryDao().upsert(history(PI_ID, progressMs = 9_999L))

        manager(target, targetShows).importLibraryFromJson(manager(source, sourceShows).exportLibraryAsJson())

        val dao = target.episodeIdentityDao()
        val kept = dao.getAlias(PI_ID)!!
        assertEquals("guid-z", kept.catalogKey)
        assertTrue(kept.owner)
        assertEquals("the phone's episode keeps its listening", 9_999L, target.listeningHistoryDao().getHistoryItem(PI_ID)?.progressMs)
        val restored = dao.aliasesForKey(SHOW, "guid-a")
        assertEquals(setOf(RECIPE_ID, RSS_EPISODE), restored.map { it.aliasId }.toSet())
        assertTrue("the refused owner leaves the restored episode to the owner rule", restored.none { it.owner })
        assertEquals(60L, dao.getGroupState(SHOW, "guid-a")?.completedAt)
    }

    @Test
    fun anIdOnlyAnotherShowsHistoryHoldsIsRefused() = runTest {
        seedSource()
        stubRssRestore()
        // A version 6 import left this ID's history under another show, with no alias or catalog row.
        target.listeningHistoryDao().upsert(history(PI_ID, progressMs = 7_777L).copy(podcastId = "999"))

        manager(target, targetShows).importLibraryFromJson(manager(source, sourceShows).exportLibraryAsJson())

        assertEquals(null, target.episodeIdentityDao().getAlias(PI_ID))
        val kept = target.listeningHistoryDao().getHistoryItem(PI_ID)!!
        assertEquals("999" to 7_777L, kept.podcastId to kept.progressMs)
    }

    @Test
    fun nullRowsInTheIdentityBlockAreSkipped() = runTest {
        seedSource()
        stubRssRestore()
        val json = com.google.gson.JsonParser.parseString(manager(source, sourceShows).exportLibraryAsJson()).asJsonObject
        val identity = json.getAsJsonObject("episodeIdentity")
        identity.getAsJsonArray("episodeAliases").add(com.google.gson.JsonNull.INSTANCE)
        identity.getAsJsonArray("episodeGroupStates").add(com.google.gson.JsonNull.INSTANCE)

        val (imported, _) = manager(target, targetShows).importLibraryFromJson(json.toString())

        assertTrue("the import doesn't fail", imported >= 0)
        assertTrue(target.episodeIdentityDao().aliasesForKey(SHOW, "guid-a").isNotEmpty())
    }

    @Test
    fun aRestoredOwnerNeverBecomesASecondOwner() = runTest {
        seedSource()
        stubRssRestore()
        target.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("999", owner = true)))
        target.episodeIdentityDao().upsertGroupState(EpisodeGroupStateEntity(SHOW, "guid-a", likeClearedAt = 80L, version = 2L))

        manager(target, targetShows).importLibraryFromJson(manager(source, sourceShows).exportLibraryAsJson())

        val members = target.episodeIdentityDao().aliasesForKey(SHOW, "guid-a")
        assertEquals(setOf("999", PI_ID, RECIPE_ID, RSS_EPISODE), members.map { it.aliasId }.toSet())
        assertEquals(listOf("999"), members.filter { it.owner }.map { it.aliasId })
        val merged = target.episodeIdentityDao().getGroupState(SHOW, "guid-a")!!
        assertEquals(50L, merged.likedAt)
        assertEquals("the later clear on this phone stays", 80L, merged.likeClearedAt)
        assertEquals(60L, merged.completedAt)
    }

    @Test
    fun opmlExportsPublisherAddressesOnly() = runTest {
        seedSource()
        source.podcastDao().upsert(subscribed(PodcastEntity(podcastId = "302", title = "No Address", author = "", imageUrl = "", description = null)))

        val opml = manager(source, sourceShows).exportLibraryAsOpml()

        assertTrue(opml, opml.contains("xmlUrl=\"https://feeds.example.com/tech.xml\""))
        assertTrue(opml, opml.contains("xmlUrl=\"https://feeds.example.com/switched.xml\""))
        assertFalse(opml, opml.contains("/episodes?id="))
        assertFalse(opml, opml.contains("/v2/feed/"))
        assertFalse(opml, opml.contains("No Address"))
    }

    private suspend fun seedSource() {
        val dao = source.episodeIdentityDao()
        dao.insertAliasesIfAbsent(
            listOf(
                alias(PI_ID, owner = true),
                alias(RECIPE_ID, provenance = EpisodeAliasProvenance.FEED_RECIPE, createdAt = 20L),
                alias(RSS_EPISODE, provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK, createdAt = 30L),
                alias(AMBIGUOUS_ID, key = "guid-b", state = EpisodeAliasState.AMBIGUOUS, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW),
                alias(UNRESOLVED_ID, key = null, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW),
            ),
        )
        dao.upsertGroupStates(
            listOf(
                EpisodeGroupStateEntity(SHOW, "guid-a", likedAt = 50L, completedAt = 60L, version = 3L),
                EpisodeGroupStateEntity(SHOW, "guid-b", likeClearedAt = 70L, version = 1L),
            ),
        )
        sourceShows.record(OLD_SHOW, NEW_SHOW)
        source.listeningHistoryDao().upsert(history(PI_ID, progressMs = 1_000L).copy(isLiked = true, likedAt = 50L))
        source.listeningHistoryDao().upsert(history(UNRESOLVED_ID, progressMs = 500L))
        val podcasts = source.podcastDao()
        podcasts.upsert(
            subscribed(
                PodcastEntity(
                    podcastId = PI_SHOW,
                    title = "Tech Talk",
                    author = "Host",
                    imageUrl = "",
                    description = null,
                    feedUrl = "https://feeds.example.com/tech.xml",
                    transportUrl = "https://api.boxlore.test/v2/feed/$PI_SHOW",
                ),
            ),
        )
        podcasts.upsert(
            subscribed(
                PodcastEntity(
                    podcastId = RSS_SHOW,
                    title = "Switched",
                    author = "Host",
                    imageUrl = "",
                    description = null,
                    sourceType = PodcastEntity.SOURCE_RSS,
                    feedUrl = SWITCHED_FEED,
                    linkedPodcastIndexId = SHOW,
                ),
            ),
        )
    }

    private suspend fun stubRssRestore() {
        val podcast = TestFixtures.podcast(id = RSS_SHOW, title = "Switched", sourceType = Podcast.SOURCE_RSS).copy(feedUrl = SWITCHED_FEED)
        `when`(rssPodcastRepository.restoreSubscription(SWITCHED_FEED, RSS_SHOW))
            .thenReturn(RssSubscriptionResult(podcast = podcast, episodeCount = 1, automaticUpdateChecksSupported = true))
    }

    private fun manager(database: BoxLoreDatabase, shows: MemoryRenumberedShows) = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = RoomHistory(database),
        podcastRepository = mock(PodcastRepository::class.java),
        context = ApplicationProvider.getApplicationContext(),
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = rssPodcastRepository,
        database = database,
        renumberedShows = shows,
    )

    private fun subscribed(entity: PodcastEntity) = entity.copy(isSubscribed = true, subscribedAt = 1_000L)

    private fun alias(
        id: String,
        key: String? = "guid-a",
        state: String = if (key == null) EpisodeAliasState.UNRESOLVED else EpisodeAliasState.TRUSTED,
        owner: Boolean = false,
        provenance: String = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt: Long = 10L,
    ) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = if (key == null) null else RECIPE_ID.toLong(),
        state = state,
        owner = owner,
        provenance = provenance,
        createdAt = createdAt,
    )

    private fun history(id: String, progressMs: Long) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 60_000L,
        isCompleted = false,
        lastPlayedAt = 40L,
    )

    /** History through Room, as the app's playback repository stores it. */
    private class RoomHistory(private val database: BoxLoreDatabase) : ListeningHistoryBackupPort {
        override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = database.listeningHistoryDao().getAllHistory()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = database.listeningHistoryDao().upsert(entity)

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }

    private companion object {
        /** The Podcast Index show switched to its RSS feed; its episodes' aliases name it. */
        const val SHOW = "100"
        const val PI_SHOW = "300"
        const val RSS_SHOW = "rss:switched"
        const val SWITCHED_FEED = "https://feeds.example.com/switched.xml"
        const val OLD_SHOW = "500"
        const val NEW_SHOW = "501"
        const val PI_ID = "111"
        const val RECIPE_ID = "4503599627370500"
        const val RSS_EPISODE = "-21"
        const val AMBIGUOUS_ID = "112"
        const val UNRESOLVED_ID = "113"
    }
}
