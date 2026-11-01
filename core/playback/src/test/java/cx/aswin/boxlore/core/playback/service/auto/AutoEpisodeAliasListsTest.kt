package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Auto lists for an episode stored under two IDs, owner "A" and "B" (docs/EPISODE_IDS.md
 * §5): the history list and "Play all liked" show the episode once, beside an episode stored under one ID.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoEpisodeAliasListsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var treeBuilder: AutoBrowseTreeBuilder
    private lateinit var voiceHandler: AutoVoiceSearchHandler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        // Group titles and other strings read as empty; the lists' contents are what's checked.
        val host = mock(AutoBrowseLibraryHost::class.java, Mockito.RETURNS_MOCKS)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.database).thenReturn(database)
        treeBuilder = AutoBrowseTreeBuilder(host, mock(AutoMediaResolver::class.java))
        voiceHandler = AutoVoiceSearchHandler(host, treeBuilder)
        runBlocking {
            database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("A", owner = true), alias("B")))
            database.listeningHistoryDao().upsertAll(
                listOf(
                    row("A", liked = true, likedAt = 20L, playedAt = 50L),
                    row("B", liked = true, likedAt = 10L, playedAt = 40L),
                    row("solo", liked = true, likedAt = 5L, playedAt = 30L),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theHistoryListShowsAnEpisodeStoredUnderTwoIdsOnce() = runBlocking {
        val ids = treeBuilder.getHistoryChildren().map { it.mediaId }

        assertEquals(2, ids.size)
        assertTrue(ids.none { it.endsWith("B") })
    }

    @Test
    fun playAllLikedQueuesAnEpisodeStoredUnderTwoIdsOnce() = runBlocking {
        val ids = voiceHandler.handlePlayAllLiked(fromCar = true).map { it.mediaId }

        assertEquals(2, ids.size)
        assertTrue(ids.none { it.endsWith("B") })
        assertEquals(2, treeBuilder.getLikedChildren().count { !it.mediaId.contains("play_all") })
    }

    private fun alias(id: String, owner: Boolean = false) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = "guid-a",
        recipeId = 4_503_599_627_370_500L,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
    )

    private fun row(id: String, liked: Boolean, likedAt: Long, playedAt: Long,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = 10L,
        durationMs = 1_800_000L,
        isCompleted = false,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val SHOW = "100"
    }
}
