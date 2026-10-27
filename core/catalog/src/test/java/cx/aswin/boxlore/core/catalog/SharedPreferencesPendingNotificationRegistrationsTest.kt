package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Registrations not yet acknowledged survive a new process, so they are asked for again (P4 PR 14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesPendingNotificationRegistrationsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun store() = SharedPreferencesPendingNotificationRegistrations(
        context.getSharedPreferences(SharedPreferencesPendingNotificationRegistrations.FILE_NAME, Context.MODE_PRIVATE),
    )

    @Test
    fun `a pending show holds in the next process until it is acknowledged`() {
        store().add("920666")
        store().add("75075")

        val nextProcess = store()
        assertEquals(setOf("920666", "75075"), nextProcess.all())
        nextProcess.remove("920666")
        assertEquals(setOf("75075"), store().all())
        assertEquals("boxlore_notification_reregistrations", SharedPreferencesPendingNotificationRegistrations.FILE_NAME)
    }
}
