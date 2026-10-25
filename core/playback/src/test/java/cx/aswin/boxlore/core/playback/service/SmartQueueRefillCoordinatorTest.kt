package cx.aswin.boxlore.core.playback.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.QueueEntry
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueEngine
import cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartQueueRefillCoordinatorTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: QueueRepository
    private lateinit var catalog: PodcastRepository
    private val podcast = Podcast("show", "Show", "Host", "https://example.com/art.jpg")
    private var engineCalls = 0
    private var excluded = emptySet<String>()
    private var source: String? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        catalog = mock(PodcastRepository::class.java)
        repository = QueueRepository(database, catalog)
    }

    @After fun tearDown() {
        database.close()
    }

    private fun episode(id: Int) = Episode("$id", "Episode $id", "", "https://example.com/$id.mp3", podcastId = "show")
    private fun candidate() = QueueEntry(EpisodeItem(id = 100L, title = "Next", enclosureUrl = "https://example.com/100.mp3"), podcast, SmartQueueEngine.SOURCE_RESUME)

    private suspend fun TestScope.coordinator(
        getCandidates: suspend () -> List<QueueEntry> = { listOf(candidate()) },
    ): SmartQueueRefillCoordinator {
        `when`(catalog.getPodcastDetails("show")).thenReturn(podcast)
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.regionStream).thenReturn(MutableStateFlow("US"))
        val engine = object : SmartQueueEngine {
            override suspend fun getNextEpisodes(currentEpisode: EpisodeItem, podcast: Podcast?, preferredSort: String?, excludeEpisodeIds: Set<String>, currentContextSourceId: String?): List<QueueEntry> {
                engineCalls++
                excluded = excludeEpisodeIds
                source = currentContextSourceId
                return getCandidates()
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SmartQueueRefillCoordinator(database, catalog, repository, engine, prefs, dispatcher, dispatcher, { "show" }, 50, SmartQueueRefillPolicy::stripQueuePrefixes)
    }

    private class Playlist(episodes: List<Episode>, initialIndex: Int) {
        val items = episodes.map { episode ->
            MediaItem.Builder().setMediaId(episode.id).setUri(episode.audioUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).setExtras(PlaybackQueueContext.extras(episode, null)).build()).build()
        }.toMutableList()
        var index = initialIndex
        val player: Player = mock(Player::class.java).also { player ->
            `when`(player.currentMediaItem).thenAnswer { items[index] }
            `when`(player.currentMediaItemIndex).thenAnswer { index }
            `when`(player.mediaItemCount).thenAnswer { items.size }
            `when`(player.getMediaItemAt(anyInt())).thenAnswer { items[it.getArgument<Int>(0)] }
            doAnswer {
                items.addAll(it.getArgument<List<MediaItem>>(0))
                null
            }.`when`(player).addMediaItems(anyList())
        }
    }

    @Test fun `playing context does not query or append fallback even at final row`() = runTest {
        val episodes = PlaybackQueueContext.episodes(listOf(episode(1)))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 0)
        assertFalse(coordinator().refillQueue(playlist.player))
        assertEquals(0, engineCalls)
        assertEquals(listOf("1"), playlist.items.map { it.mediaId })
    }

    @Test fun `long exhausted list counts only upcoming items against cap and appends one batch`() = runTest {
        val episodes = PlaybackQueueContext.episodes((1..50).map(::episode))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 49)
        assertTrue(coordinator().refillQueue(playlist.player, contextExhausted = true))
        assertEquals((1..50).map { "$it" } + "100", playlist.items.map { it.mediaId })
        assertEquals(2, playlist.items.size - playlist.index)
        assertEquals((1..50).map { "$it" }.toSet(), excluded)
        assertEquals(PlaybackQueueContext.NEW_EPISODES, source)
        assertFalse(PlaybackQueueContext.isContextItem(playlist.items.last()))
    }

    @Test fun `replacement in Room while candidates load rejects stale refill`() = runTest {
        val episodes = PlaybackQueueContext.episodes(listOf(episode(1)))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 0)
        val coordinator = coordinator {
            repository.replaceQueue(listOf(episode(9)))
            listOf(candidate())
        }
        assertFalse(coordinator.refillQueue(playlist.player, contextExhausted = true))
        assertEquals(listOf("1"), playlist.items.map { it.mediaId })
        assertEquals(listOf("9"), repository.getQueueEpisodeSnapshot().map { it.id })
    }

    @Test fun `transition trimming consumed Room prefix while candidates load still appends refill`() = runTest {
        val episodes = listOf(episode(1), episode(2), episode(3))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 1)
        val coordinator = coordinator {
            // The UI transition drops consumed rows while the service still retains its playlist.
            repository.replaceQueue(episodes.drop(1))
            listOf(candidate())
        }
        assertTrue(coordinator.refillQueue(playlist.player))
        assertEquals(listOf("1", "2", "3", "100"), playlist.items.map { it.mediaId })
        assertEquals(listOf("2", "3", "100"), repository.getQueueEpisodeSnapshot().map { it.id })
    }

    @Test fun `playlist change while candidates load rejects stale refill`() = runTest {
        val episodes = PlaybackQueueContext.episodes(listOf(episode(1)))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 0)
        val coordinator = coordinator {
            playlist.items[0] = MediaItem.Builder().setMediaId("9").build()
            listOf(candidate())
        }
        assertFalse(coordinator.refillQueue(playlist.player, contextExhausted = true))
        assertEquals(listOf("1"), repository.getQueueEpisodeSnapshot().map { it.id })
        assertEquals(listOf("9"), playlist.items.map { it.mediaId })
    }

    @Test fun `refill never queues the same episode twice across its IDs`() = runTest {
        // 1 and 100 are one episode; 101 and 102 are another; 103 is its own.
        aliases("guid-a" to listOf("1", "100"), "guid-b" to listOf("101", "102"))
        val episodes = listOf(episode(1))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 0)
        val picks = listOf(100L, 101L, 102L, 103L).map { id ->
            QueueEntry(EpisodeItem(id = id, title = "Pick $id", enclosureUrl = "https://example.com/$id.mp3"), podcast, SmartQueueEngine.SOURCE_RESUME)
        }

        assertTrue(coordinator { picks }.refillQueue(playlist.player))

        assertEquals(setOf("1", "100"), excluded)
        assertEquals(listOf("1", "101", "103"), playlist.items.map { it.mediaId })
        assertEquals(listOf("1", "101", "103"), repository.getQueueEpisodeSnapshot().map { it.id })
    }

    @Test fun `a refill item's cache key is its download key so a downloaded pick plays from its file`() = runTest {
        aliases("guid-c" to listOf("100", "200"))
        database.downloadedEpisodeDao().insert(download("200"))
        val episodes = listOf(episode(1))
        repository.replaceQueue(episodes)
        val playlist = Playlist(episodes, 0)
        val own = QueueEntry(EpisodeItem(id = 300L, title = "Own", enclosureUrl = "https://example.com/300.mp3"), podcast, SmartQueueEngine.SOURCE_RESUME)

        assertTrue(coordinator { listOf(candidate(), own) }.refillQueue(playlist.player))

        val keys = playlist.items.drop(1).map { it.localConfiguration?.customCacheKey }
        // 100's download is held by its other ID, 200; 300 plays from its own key.
        assertEquals(listOf("200", EpisodeMediaCacheKey.of("300", "https://example.com/300.mp3")), keys)
        assertEquals(EpisodeMediaCacheKey.of("100", "https://example.com/100.mp3"), refillMediaItem(candidate()).localConfiguration?.customCacheKey)
        assertEquals("300", refillMediaItem(own).localConfiguration?.customCacheKey)
    }

    private suspend fun aliases(vararg episodes: Pair<String, List<String>>) {
        database.episodeIdentityDao().insertAliasesIfAbsent(
            episodes.flatMap { (key, ids) ->
                ids.mapIndexed { index, id ->
                    EpisodeAliasEntity(id, "show", key, null, EpisodeAliasState.TRUSTED, owner = index == 0, provenance = EpisodeAliasProvenance.UPGRADE_CATALOG, createdAt = index.toLong())
                }
            },
        )
    }

    private fun download(id: String) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = "show",
        episodeTitle = "Episode $id",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Show",
        podcastImageUrl = null,
        durationMs = 1_000L,
        publishedDate = 1L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
    )
}
