package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Auto's like and mark-played buttons for an episode stored under two IDs, owner "A" and
 * "B" (docs/EPISODE_IDS.md §5), and for an episode stored under one ID, which must work as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoEpisodeAliasActionsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var callback: AutoBrowseLibraryCallback
    private val dao get() = database.listeningHistoryDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        val host = mock(AutoBrowseLibraryHost::class.java)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))
        `when`(host.database).thenReturn(database)
        callback = AutoBrowseLibraryCallback(
            host = host,
            mediaResolver = mock(AutoMediaResolver::class.java),
            resumptionHandler = mock(AutoPlaybackResumptionHandler::class.java),
            treeBuilder = mock(AutoBrowseTreeBuilder::class.java),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theLikeButtonSetsTheEpisodesLikeOnEveryId() = runBlocking {
        twoIds()
        dao.upsertAll(listOf(row("A", playedAt = 50L), row("B", liked = true, likedAt = 10L, playedAt = 40L)))

        // The episode reads liked (B's like), so the button clears it under both IDs.
        assertTrue(callback.toggleEpisodeLike("A"))

        assertFalse(dao.getHistoryItem("A")!!.isLiked)
        assertFalse(dao.getHistoryItem("B")!!.isLiked)
        assertTrue(database.episodeIdentityDao().getGroupState(SHOW, KEY)!!.likeClearedAt > 0L)

        assertTrue(callback.toggleEpisodeLike("B"))
        assertTrue(dao.getHistoryItem("A")!!.isLiked)
        assertTrue(dao.getHistoryItem("B")!!.isLiked)
    }

    @Test
    fun theLikeButtonOnAnEpisodeStoredUnderOneIdTogglesItsRowAsBefore() = runBlocking {
        val before = row("solo", liked = false, likedAt = 3L, playedAt = 50L).copy(isDirty = false)
        dao.upsert(before)

        assertTrue(callback.toggleEpisodeLike("solo"))

        assertEquals(before.copy(isLiked = true, isDirty = true), dao.getHistoryItem("solo"))
        assertNull(database.episodeIdentityDao().getGroupState(SHOW, KEY))
    }

    @Test
    fun markPlayedCompletesTheOwnerAndEveryOtherId() = runBlocking {
        twoIds()
        dao.upsertAll(listOf(row("A", progress = 30L, playedAt = 50L), row("B", playedAt = 40L)))

        assertTrue(callback.markEpisodeComplete("B"))

        val owner = dao.getHistoryItem("A")!!
        assertTrue(owner.isCompleted)
        assertTrue(owner.isManualCompletion)
        assertTrue(dao.getHistoryItem("B")!!.isCompleted)
        assertTrue(database.episodeListenerStore().projectedHistory("B")!!.isCompleted)
        assertTrue(database.episodeIdentityDao().getGroupState(SHOW, KEY)!!.completedAt > 0L)
    }

    @Test
    fun markPlayedOnAnEpisodeStoredUnderOneIdCompletesItsRowAsBefore() = runBlocking {
        dao.upsert(row("solo", progress = 30L, playedAt = 50L))

        assertTrue(callback.markEpisodeComplete("solo"))

        val row = dao.getHistoryItem("solo")!!
        assertTrue(row.isCompleted)
        assertTrue(row.isManualCompletion)
        assertNull(database.episodeIdentityDao().getGroupState(SHOW, KEY))
    }

    private suspend fun twoIds() {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L), alias("B", createdAt = 2L)))
    }

    private fun alias(id: String, owner: Boolean = false, createdAt: Long = 1L,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = KEY,
        recipeId = 4_503_599_627_370_500L,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    private fun row(id: String, progress: Long = 0L, liked: Boolean = false, likedAt: Long = 0L, playedAt: Long = 1L,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_800_000L,
        isCompleted = false,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
    }
}
