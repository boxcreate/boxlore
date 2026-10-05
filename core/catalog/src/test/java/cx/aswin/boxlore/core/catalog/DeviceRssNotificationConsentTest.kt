package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceRssNotificationConsentTest {
    @Test fun failedAcceptAndRegistrationWritesRemainClosedWithoutThrowing() {
        val storage = FailingStorage(newFile())
        val consent = DeviceRssNotificationConsent(storage)
        try {
            consent.accept("rss:one", "https://publisher.example/feed")
            assertFalse(consent.isAccepted("rss:one", "https://publisher.example/feed"))
            assertNull(consent.registrationId)
        } finally {
            storage.delete()
        }
    }

    @Test fun failedRevokeDeniesOldStoredConsentUntilAConfirmedWriteSucceeds() {
        val storage = FailingStorage(newFile()).apply { fail = false }
        val consent = DeviceRssNotificationConsent(storage)
        try {
            consent.accept("rss:one", "https://publisher.example/feed")
            assertTrue(consent.isAccepted("rss:one", "https://publisher.example/feed"))
            storage.fail = true
            consent.revoke("rss:one")
            assertFalse(consent.isAccepted("rss:one", "https://publisher.example/feed"))
            consent.accept("rss:one", "https://publisher.example/feed")
            assertFalse(consent.isAccepted("rss:one", "https://publisher.example/feed"))
            storage.fail = false
            consent.accept("rss:one", "https://publisher.example/feed")
            assertTrue(consent.isAccepted("rss:one", "https://publisher.example/feed"))
        } finally {
            storage.delete()
        }
    }

    private fun newFile() = File(ApplicationProvider.getApplicationContext<Context>().noBackupFilesDir, "rss-failure-test-${System.nanoTime()}")

    private class FailingStorage(file: File) : AtomicFile(file) {
        var fail = true
        override fun startWrite(): FileOutputStream {
            if (fail) throw IOException("disk full")
            return super.startWrite()
        }
    }
}
