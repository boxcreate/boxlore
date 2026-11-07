package cx.aswin.boxlore.feature.info.components

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The show page's notification-settings shortcut: the app's notification page, else its details, else nothing; never a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationSettingsLinkTest {
    /** A phone whose apps open only what [opens] accepts; it records what it was asked to open. */
    private class Phone(private val opens: (Intent) -> Boolean) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val asked = mutableListOf<Intent>()
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            asked += intent
            if (!opens(intent)) throw ActivityNotFoundException("No app for $intent")
            started += intent
        }
    }

    @Test
    fun theAppsNotificationPageOpensWhenThePhoneHasIt() {
        val phone = Phone { true }

        openAppNotificationSettings(phone)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, phone.started.single().action)
        assertEquals(phone.packageName, phone.started.single().getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun withoutThatPageTheAppsDetailsOpenInstead() {
        val phone = Phone { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS }

        openAppNotificationSettings(phone)

        val details = phone.started.single()
        assertEquals("package:${phone.packageName}", details.data.toString())
        assertEquals(listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS), phone.asked.map { it.action })
    }

    @Test
    fun withNeitherPageNothingOpensAndNothingCrashes() {
        val phone = Phone { false }

        openAppNotificationSettings(phone)

        assertTrue(phone.started.isEmpty())
        assertEquals(2, phone.asked.size)
    }
}
