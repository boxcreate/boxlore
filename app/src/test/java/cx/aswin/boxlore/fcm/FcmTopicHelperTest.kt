package cx.aswin.boxlore.fcm

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FcmTopicHelperTest {
    @Test fun existingRestoreSentinelAndEmptyEnabledListDoNotSkipRssCleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        val sentinel = File(context.noBackupFilesDir, "fcm_topics_synced")
        try {
            sentinel.createNewFile()
            var scheduled = 0
            val subscriptions = SubscriptionRepository(database.podcastDao(), requestRssNotificationSync = { scheduled++ })
            FcmTopicHelper.reconcileAfterRestoreIfNeeded(context, subscriptions)
            assertEquals(1, scheduled)
            assertEquals(emptyList<Any>(), database.podcastDao().getNotificationEnabledPodcasts())
        } finally {
            sentinel.delete()
            database.close()
        }
    }
}
