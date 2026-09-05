package cx.aswin.boxlore.core.playback.service

import android.app.Service
import android.content.Intent
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Scheduler
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncher
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaDownloadServiceTest {

    private class TestMediaDownloadService : MediaDownloadService() {
        init {
            attachBaseContext(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        }

        fun exposedScheduler(): Scheduler? = getScheduler()

        override fun getDownloadManager(): DownloadManager =
            throw UnsupportedOperationException("Simulated background service exception")
    }

    @Before
    fun setUp() {
        DownloadServiceLauncherHolder.instance =
            DownloadServiceLauncher {
                MediaDownloadService::class.java
            }
    }

    @After
    fun tearDown() {
        DownloadServiceLauncherHolder.instance = null
    }

    @Test
    fun `getScheduler returns null on API 31 and above`() {
        val service = TestMediaDownloadService()
        assertNull(service.exposedScheduler())
    }

    @Test
    fun `onStartCommand catches exception and returns START_NOT_STICKY`() {
        val service = TestMediaDownloadService()
        val result = service.onStartCommand(Intent(), 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
    }

    @Test
    @Config(sdk = [28])
    fun `getScheduler returns PlatformScheduler on API 30 and below`() {
        val service = TestMediaDownloadService()
        org.junit.Assert.assertNotNull(service.exposedScheduler())
    }
}
