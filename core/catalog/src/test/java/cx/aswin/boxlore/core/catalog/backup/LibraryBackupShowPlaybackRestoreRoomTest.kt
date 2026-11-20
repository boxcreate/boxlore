package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A full backup carries each show's own playback speed and trim silence (backup version 11): they
 * restore with the show, alongside its skip overrides, while an older backup leaves the phone's alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupShowPlaybackRestoreRoomTest {
    private lateinit var source: BoxLoreDatabase
    private lateinit var target: BoxLoreDatabase

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
    fun aShowsSpeedAndTrimSilenceAreExportedAndRestored() = runTest {
        source.podcastDao().upsert(show(SHOW).copy(skipBeginningOverrideMs = 30_000L, playbackSpeedOverride = 1.5f, skipSilenceOverride = false))
        source.podcastDao().upsert(show(OTHER))
        val json = manager(source).exportLibraryAsJson()

        val exported = JsonParser.parseString(json).asJsonObject
        assertEquals(11, exported["version"].asInt)
        val row = exported["subscriptions"].asJsonArray.map { it.asJsonObject }.single { it["podcastId"].asString == SHOW }
        assertEquals(1.5f, row["playbackSpeedOverride"].asFloat)
        assertEquals(false, row["skipSilenceOverride"].asBoolean)

        manager(target).importLibraryFromJson(json)

        val restored = target.podcastDao().getPodcast(SHOW)!!
        assertEquals(1.5f, restored.playbackSpeedOverride)
        assertEquals(false, restored.skipSilenceOverride)
        assertEquals(30_000L, restored.skipBeginningOverrideMs)
        val other = target.podcastDao().getPodcast(OTHER)!!
        assertNull(other.playbackSpeedOverride)
        assertNull(other.skipSilenceOverride)
    }

    @Test
    fun aVersion11BackupWithoutTheShowsOwnSettingsClearsThePhonesToTheAppSettings() = runTest {
        source.podcastDao().upsert(show(SHOW))
        target.podcastDao().upsert(show(SHOW).copy(playbackSpeedOverride = 2f, skipSilenceOverride = true))

        manager(target).importLibraryFromJson(manager(source).exportLibraryAsJson())

        val restored = target.podcastDao().getPodcast(SHOW)!!
        assertNull(restored.playbackSpeedOverride)
        assertNull(restored.skipSilenceOverride)
    }

    @Test
    fun anOlderBackupLeavesThePhonesOwnSettingsAndRestoresNoneOnANewPhone() = runTest {
        target.podcastDao().upsert(show(SHOW).copy(playbackSpeedOverride = 2f, skipSilenceOverride = true))
        val v10 = """{"version":10,"subscriptions":[${showJson(SHOW)},${showJson(OTHER)}],"history":[]}"""

        manager(target).importLibraryFromJson(v10)

        val kept = target.podcastDao().getPodcast(SHOW)!!
        assertEquals(2f, kept.playbackSpeedOverride)
        assertEquals(true, kept.skipSilenceOverride)
        val added = target.podcastDao().getPodcast(OTHER)!!
        assertNull(added.playbackSpeedOverride)
        assertNull(added.skipSilenceOverride)
    }

    private fun show(id: String) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "Host",
        imageUrl = "https://example.com/$id.jpg",
        description = null,
        isSubscribed = true,
        subscribedAt = 1_000L,
        feedUrl = "https://example.com/$id.xml",
    )

    private fun showJson(id: String) =
        """{"podcastId":"$id","title":"Show $id","author":"Host","imageUrl":"https://example.com/$id.jpg",""" +
            """"isSubscribed":true,"subscribedAt":1000,"sourceType":"podcast_index","feedUrl":"https://example.com/$id.xml"}"""

    private fun manager(database: BoxLoreDatabase) = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = RoomHistory(database),
        podcastRepository = mock(PodcastRepository::class.java),
        context = ApplicationProvider.getApplicationContext(),
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = mock(RssPodcastRepository::class.java),
        database = database,
        deviceIdentity = DeviceIdentityPort { "phone-a" },
        legacyListeningOrigin = DeviceIdentityPort { "legacy-phone-a" },
    )

    private class RoomHistory(private val database: BoxLoreDatabase) : ListeningHistoryBackupPort {
        override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = database.listeningHistoryDao().getRecentHistory()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = database.listeningHistoryDao().getAllStoredHistory()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = database.listeningHistoryDao().upsert(entity)

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }

    private companion object {
        const val SHOW = "1001"
        const val OTHER = "1002"
    }
}
