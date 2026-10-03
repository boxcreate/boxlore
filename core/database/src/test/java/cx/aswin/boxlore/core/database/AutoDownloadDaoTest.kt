package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadDaoTest {
    @Test fun settingsPersistActivationDirtyStateAndCancelPendingOnDisable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        try {
            database.podcastDao().upsert(PodcastEntity("show", "Show", "Author", "", null, isSubscribed = true))
            val dao = database.autoDownloadDao()
            dao.changeSetting("show", true, 100)
            assertEquals(100L, dao.getShow("show")?.enabledAt)
            assertTrue(database.podcastDao().getPodcast("show")!!.autoDownloadEnabled)
            assertTrue(database.podcastDao().getPodcast("show")!!.isDirty)
            assertFalse(database.podcastDao().getPodcast("show")!!.notificationsEnabled)
            dao.insertRelease(AutoDownloadReleaseEntity("-1", "show"))
            dao.changeSetting("show", false, 200)
            assertNull(dao.getShow("show"))
            assertEquals(AutoDownloadReleaseEntity.HANDLED, dao.getRelease("-1")?.state)
            dao.changeSetting("show", true, 300)
            assertEquals(300L, dao.getShow("show")?.enabledAt)
            assertEquals(AutoDownloadReleaseEntity.HANDLED, dao.getRelease("-1")?.state)
        } finally {
            database.close()
        }
    }

    @Test fun lateEnrichmentCannotRecreateRemovedDownload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        try {
            val downloads = database.downloadedEpisodeDao()
            downloads.insert(
                DownloadedEpisodeEntity(
                    "-1", "show", "Episode", null, null, "Show", null,
                1000, 100, "CACHED", 0, 100, 100, downloadOrigin = DownloadedEpisodeEntity.ORIGIN_AUTO
                )
            )
            database.autoDownloadDao().markRemoved("-1")
            downloads.delete("-1")
            downloads.updateArtwork("-1", "/late-art.png", "/late-show.png")
            downloads.updateOfflineText("-1", "/late-chapters.json", "/late-transcript.txt")
            assertNull(downloads.getDownload("-1"))
            assertEquals(AutoDownloadReleaseEntity.REMOVED, database.autoDownloadDao().getRelease("-1")?.state)
        } finally {
            database.close()
        }
    }
}
