package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistorySyncResolverTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var listeningHistoryDao: ListeningHistoryDao
    private lateinit var fakePlaybackPort: FakeActivePlaybackSyncPort
    private lateinit var resolver: HistorySyncResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        listeningHistoryDao = database.listeningHistoryDao()
        fakePlaybackPort = FakeActivePlaybackSyncPort()
        resolver = HistorySyncResolver(
            listeningHistoryDao = listeningHistoryDao,
            activePlaybackSyncPort = fakePlaybackPort,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveHistoryItem_activePlayingEpisodeShield_neverOverwritesPlaybackProgress() = runTest {
        listeningHistoryDao.upsert(
            createHistory(
                episodeId = "ep-active",
                podcastId = "pod-1",
                episodeTitle = "Currently Playing Episode",
                podcastName = "Show Name",
                progressMs = 45000L,
                durationMs = 120000L,
                isCompleted = false,
                isLiked = false,
                likedAt = 0L,
                lastPlayedAt = 1000L,
                isDirty = false,
                syncedAt = 1000L,
            ),
        )

        fakePlaybackPort.activeEpisodeId = "ep-active"

        val remoteDto = ListeningHistorySyncDto(
            episodeId = "ep-active",
            podcastId = "pod-1",
            progressMs = 10000L, // Remote stale/lower progress
            durationMs = 120000L,
            isCompleted = false,
            isLiked = true, // Remote liked this episode
            likedAt = 2500L,
            lastPlayedAt = 2000L,
            updatedAt = 2500L,
        )

        resolver.resolveHistoryItem(remoteDto, syncedAt = 3000L)

        val updated = listeningHistoryDao.getHistoryItem("ep-active")
        assertNotNull(updated)
        // Progress protected by shield:
        assertEquals(45000L, updated!!.progressMs)
        assertEquals(1000L, updated.lastPlayedAt)
        // Independent like status merged:
        assertTrue(updated.isLiked)
        assertEquals(2500L, updated.likedAt)
        assertEquals(3000L, updated.syncedAt)
    }

    @Test
    fun resolveHistoryItem_remoteProgressLWW_overwritesWhenRemoteLastPlayedIsNewer() = runTest {
        listeningHistoryDao.upsert(
            createHistory(
                episodeId = "ep-inactive",
                podcastId = "pod-1",
                episodeTitle = "Test Episode",
                progressMs = 15000L,
                durationMs = 60000L,
                isCompleted = false,
                lastPlayedAt = 1000L,
                isDirty = true,
            ),
        )

        fakePlaybackPort.activeEpisodeId = "other-ep"

        val remoteDto = ListeningHistorySyncDto(
            episodeId = "ep-inactive",
            podcastId = "pod-1",
            progressMs = 55000L,
            durationMs = 60000L,
            isCompleted = true,
            isLiked = false,
            likedAt = 0L,
            lastPlayedAt = 2000L, // Newer
            updatedAt = 2000L,
        )

        resolver.resolveHistoryItem(remoteDto, syncedAt = 3000L)

        val updated = listeningHistoryDao.getHistoryItem("ep-inactive")
        assertNotNull(updated)
        assertEquals(55000L, updated!!.progressMs)
        assertTrue(updated.isCompleted)
        assertEquals(2000L, updated.lastPlayedAt)
        assertFalse(updated.isDirty)
        assertEquals(3000L, updated.syncedAt)
    }

    @Test
    fun resolveHistoryItem_localProgressLWW_preservesLocalWhenLocalLastPlayedIsNewer() = runTest {
        listeningHistoryDao.upsert(
            createHistory(
                episodeId = "ep-inactive",
                podcastId = "pod-1",
                episodeTitle = "Test Episode",
                progressMs = 50000L,
                durationMs = 60000L,
                isCompleted = false,
                lastPlayedAt = 2500L, // Newer local
                isDirty = true,
            ),
        )

        fakePlaybackPort.activeEpisodeId = null

        val remoteDto = ListeningHistorySyncDto(
            episodeId = "ep-inactive",
            podcastId = "pod-1",
            progressMs = 10000L,
            durationMs = 60000L,
            isCompleted = false,
            isLiked = false,
            likedAt = 0L,
            lastPlayedAt = 1000L, // Older remote
            updatedAt = 1000L,
        )

        resolver.resolveHistoryItem(remoteDto, syncedAt = 3000L)

        val updated = listeningHistoryDao.getHistoryItem("ep-inactive")
        assertNotNull(updated)
        assertEquals(50000L, updated!!.progressMs)
        assertEquals(2500L, updated.lastPlayedAt)
    }

    @Test
    fun resolveHistoryItem_independentLikeLWW_resolvesCorrectly() = runTest {
        listeningHistoryDao.upsert(
            createHistory(
                episodeId = "ep-like-test",
                podcastId = "pod-1",
                progressMs = 1000L,
                lastPlayedAt = 1000L,
                isLiked = true,
                likedAt = 5000L, // Local un-liked or liked later
            ),
        )

        // Case A: Remote has older like status -> local wins
        val olderRemote = ListeningHistorySyncDto(
            episodeId = "ep-like-test",
            podcastId = "pod-1",
            progressMs = 1000L,
            durationMs = 60000L,
            isCompleted = false,
            isLiked = false,
            likedAt = 2000L,
            lastPlayedAt = 1000L,
            updatedAt = 2000L,
        )

        resolver.resolveHistoryItem(olderRemote, syncedAt = 6000L)
        val stateA = listeningHistoryDao.getHistoryItem("ep-like-test")
        assertTrue(stateA!!.isLiked)
        assertEquals(5000L, stateA.likedAt)

        // Case B: Remote has newer like status -> remote wins
        val newerRemote = ListeningHistorySyncDto(
            episodeId = "ep-like-test",
            podcastId = "pod-1",
            progressMs = 1000L,
            durationMs = 60000L,
            isCompleted = false,
            isLiked = false, // Remote explicitly unliked at 8000L
            likedAt = 8000L,
            lastPlayedAt = 1000L,
            updatedAt = 8000L,
        )

        resolver.resolveHistoryItem(newerRemote, syncedAt = 9000L)
        val stateB = listeningHistoryDao.getHistoryItem("ep-like-test")
        assertFalse(stateB!!.isLiked)
        assertEquals(8000L, stateB.likedAt)
    }

    @Test
    fun resolveHistoryItem_metadataPreserved_whenUpdatingExistingItem() = runTest {
        listeningHistoryDao.upsert(
            createHistory(
                episodeId = "ep-rich",
                podcastId = "pod-1",
                episodeTitle = "Rich Local Title",
                podcastName = "Rich Podcast Name",
                episodeImageUrl = "https://example.com/ep.png",
                podcastImageUrl = "https://example.com/pod.png",
                episodeAudioUrl = "https://example.com/ep.mp3",
                episodeDescription = "Rich description",
                progressMs = 5000L,
                durationMs = 30000L,
                lastPlayedAt = 1000L,
            ),
        )

        val remoteDto = ListeningHistorySyncDto(
            episodeId = "ep-rich",
            podcastId = "pod-1",
            progressMs = 20000L,
            durationMs = 30000L,
            isCompleted = false,
            isLiked = false,
            likedAt = 0L,
            lastPlayedAt = 2000L,
            updatedAt = 2000L,
        )

        resolver.resolveHistoryItem(remoteDto, syncedAt = 3000L)

        val updated = listeningHistoryDao.getHistoryItem("ep-rich")
        assertNotNull(updated)
        assertEquals("Rich Local Title", updated!!.episodeTitle)
        assertEquals("Rich Podcast Name", updated.podcastName)
        assertEquals("https://example.com/ep.png", updated.episodeImageUrl)
        assertEquals("https://example.com/pod.png", updated.podcastImageUrl)
        assertEquals("https://example.com/ep.mp3", updated.episodeAudioUrl)
        assertEquals("Rich description", updated.episodeDescription)
        assertEquals(20000L, updated.progressMs)
    }

    @Test
    fun resolveHistoryItem_newItemFromRemote_createsEntityWhenNotFoundLocally() = runTest {
        val remoteDto = ListeningHistorySyncDto(
            episodeId = "ep-new-cloud",
            podcastId = "pod-cloud",
            progressMs = 33000L,
            durationMs = 180000L,
            isCompleted = false,
            isLiked = true,
            likedAt = 1500L,
            lastPlayedAt = 1500L,
            updatedAt = 1500L,
        )

        resolver.resolveHistoryItem(remoteDto, syncedAt = 3000L)

        val created = listeningHistoryDao.getHistoryItem("ep-new-cloud")
        assertNotNull(created)
        assertEquals("ep-new-cloud", created!!.episodeId)
        assertEquals("pod-cloud", created.podcastId)
        assertEquals(33000L, created.progressMs)
        assertEquals(180000L, created.durationMs)
        assertTrue(created.isLiked)
        assertEquals(1500L, created.likedAt)
        assertEquals(1500L, created.lastPlayedAt)
        assertFalse(created.isDirty)
        assertEquals(3000L, created.syncedAt)
    }

    @Suppress("LongParameterList")
    private fun createHistory(
        episodeId: String,
        podcastId: String = "pod-1",
        progressMs: Long = 0L,
        durationMs: Long = 60000L,
        isCompleted: Boolean = false,
        isLiked: Boolean = false,
        likedAt: Long = 0L,
        lastPlayedAt: Long = 0L,
        isDirty: Boolean = false,
        syncedAt: Long = 0L,
        episodeTitle: String = "Test Episode",
        podcastName: String = "Show Name",
        episodeImageUrl: String? = null,
        podcastImageUrl: String? = null,
        episodeAudioUrl: String? = "https://example.com/audio.mp3",
        episodeDescription: String? = null,
    ) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = episodeTitle,
        episodeImageUrl = episodeImageUrl,
        podcastImageUrl = podcastImageUrl,
        episodeAudioUrl = episodeAudioUrl,
        podcastName = podcastName,
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        likedAt = likedAt,
        lastPlayedAt = lastPlayedAt,
        isDirty = isDirty,
        syncedAt = syncedAt,
        episodeDescription = episodeDescription,
    )

    private class FakeActivePlaybackSyncPort(
        var activeEpisodeId: String? = null,
    ) : ActivePlaybackSyncPort {
        override fun getActivePlayingEpisodeId(): String? = activeEpisodeId
    }
}
