package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Auto items saved under an old ID of an episode the phone now stores under another trusted
 * ID (own-IDs plan section 9, Android Auto): the saved browse item and a resumption target resolve
 * through the episode's aliases instead of failing or asking the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoEpisodeAliasResolutionTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var resolver: AutoMediaResolver
    private val queue = mock(QueueRepository::class.java)
    private val podcasts = mock(PodcastRepository::class.java)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        val host = mock(AutoBrowseLibraryHost::class.java)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))
        `when`(host.database).thenReturn(database)
        `when`(host.queueRepository).thenReturn(queue)
        `when`(host.podcastRepository).thenReturn(podcasts)
        runBlocking { `when`(queue.getQueueEpisodeSnapshot()).thenReturn(emptyList()) }
        resolver = AutoMediaResolver(host)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a saved browse item under an old ID plays the episode's history under its owner`() = runBlocking {
        twoIds()
        database.listeningHistoryDao().upsert(row("NEW"))

        val resolved = resolver.resolveMediaItem(MediaItem.Builder().setMediaId("${AutoBrowseContract.EPISODE_PREFIX}OLD").build())

        assertEquals("${AutoBrowseContract.EPISODE_PREFIX}OLD", resolved.mediaId)
        assertEquals("https://cdn.example/NEW.mp3", resolved.localConfiguration?.uri.toString())
        assertEquals("Episode NEW", resolved.mediaMetadata.title.toString())
        assertEquals("OLD", resolved.localConfiguration?.customCacheKey)
    }

    @Test
    fun `a resumption target under an old ID lines up with the queued copy of its episode`() = runBlocking {
        twoIds()
        val queued = Episode(id = "NEW", title = "Queued", description = "", audioUrl = "https://cdn.example/NEW.mp3", podcastId = SHOW)
        `when`(queue.getQueueEpisodeSnapshot()).thenReturn(listOf(queued))

        assertEquals(queued, resolver.resolveDomainEpisode("OLD"))
    }

    @Test
    fun `a resumption target under an old ID keeps that ID with the episode's history`() = runBlocking {
        twoIds()
        database.listeningHistoryDao().upsert(row("NEW"))

        val episode = resolver.resolveDomainEpisode("OLD")!!

        assertEquals("OLD", episode.id)
        assertEquals("https://cdn.example/NEW.mp3", episode.audioUrl)
        assertEquals("Episode NEW", episode.title)
    }

    @Test
    fun `an ID with no alias resolves only its own rows, as before`() = runBlocking {
        database.listeningHistoryDao().upsert(row("NEW"))
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("NEW", owner = true), alias("OLD").copy(state = EpisodeAliasState.AMBIGUOUS)))

        assertNull(resolver.resolveDomainEpisode("OLD"))
        assertEquals("NEW", resolver.resolveDomainEpisode("NEW")!!.id)
    }

    private suspend fun twoIds() {
        database.episodeIdentityDao().insertAliasesIfAbsent(listOf(alias("NEW", owner = true), alias("OLD")))
    }

    private fun alias(id: String, owner: Boolean = false) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = "guid-a",
        recipeId = 4_503_599_627_370_500L,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = if (owner) 1L else 2L,
    )

    private fun row(id: String) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = "https://img.example/$id.jpg",
        podcastImageUrl = "https://img.example/show.jpg",
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = "Show",
        progressMs = 30_000L,
        durationMs = 1_800_000L,
        isCompleted = false,
        lastPlayedAt = 50L,
    )

    private companion object {
        const val SHOW = "100"
    }
}
