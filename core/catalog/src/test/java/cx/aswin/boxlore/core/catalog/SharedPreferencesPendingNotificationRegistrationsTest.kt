package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        nextProcess.remove("920666", nextProcess.mark("920666")!!)
        assertEquals(setOf("75075"), store().all())
        assertEquals("boxlore_notification_reregistrations", SharedPreferencesPendingNotificationRegistrations.FILE_NAME)
    }

    @Test
    fun `a newer request gets a later mark and survives a settle of the older one`() {
        val now = 1_000L
        val store =
            SharedPreferencesPendingNotificationRegistrations(
                context.getSharedPreferences(SharedPreferencesPendingNotificationRegistrations.FILE_NAME, Context.MODE_PRIVATE),
                nowMillis = { now },
            )
        store.add("920666")
        val older = store.mark("920666")!!
        store.add("920666") // the same millisecond
        val newer = store.mark("920666")!!
        assertTrue(newer > older)

        store.remove("920666", older)
        assertEquals(setOf("920666"), store.all())
        store.remove("920666", newer)
        assertTrue(store.all().isEmpty())
    }
}
