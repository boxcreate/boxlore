package cx.aswin.boxlore.core.rss

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.episodeListenerStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one path that still moves listener state between stored IDs, the orphan rematch after a feed
 * read, through Room (docs/EPISODE_IDS.md §5). Trusted IDs never move; a catalog row read again
 * under a new ID joins the episode instead; ambiguous IDs keep their rows. Switching a Podcast Index
 * show to its feed moves nothing ([ShowSwitchRoomTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenerStateMovesRoomTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var reconciler: LocalCatalogListenerStateReconciler
    private val history get() = database.listeningHistoryDao()
    private val identity get() = database.episodeIdentityDao()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        reconciler = LocalCatalogListenerStateReconciler(database, nowMs = { NOW })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anEvictedEpisodeReadAgainUnderANewIdJoinsItsEpisodeAndNothingMoves() = runBlocking {
        // A trusted phone-minted ID holds the listening; its catalog row was evicted.
        identity.insertAliasesIfAbsent(listOf(alias("-5", key = KEY, owner = true)))
        history.upsert(row("-5", progress = 300L, playedAt = 50L, audio = AUDIO_1))
        // The feed is read again and the same key gets a Podcast Index ID.
        val reread = catalogRow("777", guid = KEY, audio = AUDIO_1)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(reread))

        reconciler.reconcile(SHOW, listOf(reread))

        val joined = identity.getAlias("777")!!
        assertEquals(EpisodeAliasState.TRUSTED, joined.state)
        assertEquals(KEY, joined.catalogKey)
        assertEquals(EpisodeAliasProvenance.CATALOG_KEY_MATCH, joined.provenance)
        assertFalse(joined.owner)
        // The listening stays on its stored ID and reads under the catalog ID, counted once.
        assertNotNull(history.getHistoryItem("-5"))
        assertNull(history.getHistoryItem("777"))
        val store = database.episodeListenerStore()
        assertEquals(300L, store.projectedHistory("777")!!.progressMs)
        assertEquals(1, store.projectRows(history.getRecentHistoryList(10)).size)
        assertEquals("-5", store.historyId("777"))

        // A second read changes nothing.
        reconciler.reconcile(SHOW, listOf(reread))
        assertEquals(joined, identity.getAlias("777"))
    }

    @Test
    fun onlyIdsWithoutAResolvedAliasMoveAndOnlyAnExactMatchRecordsAnAlias() = runBlocking {
        val one = catalogRow("701", guid = "guid-1", audio = AUDIO_1, title = "One")
        val two = catalogRow("702", guid = "guid-2", audio = AUDIO_2, title = "Two")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(one, two))
        identity.insertAliasesIfAbsent(
            listOf(
                alias("701", key = "guid-1", owner = true),
                alias("702", key = "guid-2", owner = true),
                alias("-2", key = null, state = EpisodeAliasState.UNRESOLVED),
                alias("-3", key = "guid-1", state = EpisodeAliasState.AMBIGUOUS),
                alias("-4", key = "guid-elsewhere"),
            ),
        )
        // -1 has no alias and the same audio address as 701: an exact match.
        history.upsert(row("-1", progress = 40L, playedAt = 30L, audio = AUDIO_1))
        // -2 is unresolved and matches 702 only by title and publish date (from its queue row).
        history.upsert(row("-2", progress = 20L, playedAt = 20L, audio = "https://cdn.example.com/old-2.mp3", title = "Two"))
        database.queueDao().insertQueueItem(queueRow("-2", title = "Two", audio = "https://cdn.example.com/old-2.mp3", pubDate = two.publishedDate))
        // -3 is ambiguous and -4 trusted under another key: neither moves.
        history.upsert(row("-3", progress = 10L, playedAt = 10L, audio = AUDIO_1))
        history.upsert(row("-4", progress = 15L, playedAt = 15L, audio = AUDIO_1))

        reconciler.reconcile(SHOW, listOf(one, two))

        assertNull(history.getHistoryItem("-1"))
        assertEquals(40L, history.getHistoryItem("701")!!.progressMs)
        val moved = identity.getAlias("-1")!!
        assertEquals(EpisodeAliasEntity("-1", SHOW, "guid-1", RECIPE, EpisodeAliasState.TRUSTED, false, EpisodeAliasProvenance.ORPHAN_REMATCH, NOW), moved)

        assertNull(history.getHistoryItem("-2"))
        assertEquals(20L, history.getHistoryItem("702")!!.progressMs)
        assertEquals("702", database.queueDao().getAllQueueItemsSync().single().episodeId)
        // A title and date match is not proof: the moved ID keeps its unresolved alias.
        assertEquals(EpisodeAliasState.UNRESOLVED, identity.getAlias("-2")!!.state)

        assertEquals(10L, history.getHistoryItem("-3")!!.progressMs)
        assertEquals(15L, history.getHistoryItem("-4")!!.progressMs)
        assertEquals(EpisodeAliasState.AMBIGUOUS, identity.getAlias("-3")!!.state)
    }

    @Test
    fun movedListeningMergesIntoTheRowTheCatalogRowsEpisodeReads() = runBlocking {
        val target = catalogRow("701", guid = KEY, audio = AUDIO_1)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(target))
        // The catalog row's episode is stored under two IDs and "owner" holds its listening.
        identity.insertAliasesIfAbsent(listOf(alias("owner", key = KEY, owner = true), alias("701", key = KEY)))
        history.upsert(row("owner", progress = 100L, playedAt = 40L, audio = AUDIO_1))
        history.upsert(row("-1", progress = 500L, playedAt = 60L, audio = AUDIO_1, liked = true, likedAt = 5L))
        // The listener cleared the like later, under the episode's other ID.
        identity.upsertGroupState(EpisodeGroupStateEntity(SHOW, KEY, likeClearedAt = 20L))

        reconciler.reconcile(SHOW, listOf(target))

        assertNull(history.getHistoryItem("701"))
        val merged = history.getHistoryItem("owner")!!
        assertEquals(500L, merged.progressMs)
        assertEquals(60L, merged.lastPlayedAt)
        assertFalse(merged.isLiked)
        assertEquals(listOf("owner", "-1", "701").toSet(), database.episodeListenerStore().memberIds("701").toSet())
    }

    private fun alias(id: String, key: String?, state: String = EpisodeAliasState.TRUSTED, owner: Boolean = false,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = if (key == null) null else RECIPE,
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = 1L,
    )

    @Suppress("LongParameterList")
    private fun row(
        id: String,
        progress: Long = 0L,
        playedAt: Long = 1L,
        audio: String,
        title: String = "Episode $id",
        liked: Boolean = false,
        likedAt: Long = 0L,
        completed: Boolean = false,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = title,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = audio,
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
    )

    private fun catalogRow(id: String, guid: String, audio: String, title: String = "Episode $id",) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        guid = guid,
        title = title,
        description = "Description",
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

    private fun queueRow(id: String, title: String, audio: String, pubDate: Long,) = QueueItem(
        episodeId = id,
        title = title,
        podcastId = SHOW,
        podcastTitle = "Show",
        imageUrl = null,
        audioUrl = audio,
        duration = 60,
        pubDate = pubDate,
        description = null,
        position = 0,
    )

    private companion object {
        const val SHOW = "9926"
        const val KEY = "guid-a"
        const val RECIPE = 4_503_599_627_370_500L
        const val NOW = 1_234L
        const val AUDIO_1 = "https://cdn.example.com/1.mp3"
        const val AUDIO_2 = "https://cdn.example.com/2.mp3"
    }
}
