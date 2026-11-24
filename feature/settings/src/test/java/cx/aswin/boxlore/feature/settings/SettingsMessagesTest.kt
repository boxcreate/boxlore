package cx.aswin.boxlore.feature.settings

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.components.boxloreShowsOwnCopyConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Copying shows one confirmation: from Android 13 the system's own preview of what was copied,
 * before that boxlore's "copied" message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsMessagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun `copying the privacy ID on Android 13 and later leaves the confirmation to the system`() {
        copyDeletionId(context, "privacy-id-1")

        assertEquals("privacy-id-1", clipboardText())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    @Config(sdk = [32])
    fun `copying the privacy ID before Android 13 still confirms it`() {
        copyDeletionId(context, "privacy-id-1")

        assertEquals("privacy-id-1", clipboardText())
        assertNotNull(ShadowToast.getLatestToast())
    }

    @Test
    @Config(sdk = [32])
    fun `the feedback report and the account link confirm their copy before Android 13`() {
        assertFeedbackAndAccountCopies(expectToast = true)
    }

    @Test
    @Config(sdk = [33])
    fun `from Android 13 the feedback report and the account link leave the confirmation to the system`() {
        assertFeedbackAndAccountCopies(expectToast = false)
    }

    private fun assertFeedbackAndAccountCopies(expectToast: Boolean) {
        cx.aswin.boxlore.feature.settings.feedback.copyFeedbackReport(context, "boxlore diagnostics", "report-1")
        assertEquals("report-1", clipboardText())
        assertEquals(expectToast, ShadowToast.getLatestToast() != null)
        ShadowToast.reset()

        // Neither the link handler nor any app can open the page, so its link is copied instead.
        val noBrowser = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: android.content.Intent?) = throw android.content.ActivityNotFoundException()
        }
        val failingLinks = object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) = error("no handler")
        }
        cx.aswin.boxlore.feature.settings.pages.openAccountDeletionInfo(noBrowser, failingLinks)
        assertEquals("https://boxlore.in/account-deletion/", clipboardText())
        assertEquals(expectToast, ShadowToast.getLatestToast() != null)
    }

    @Test fun `boxlore confirms copies itself only before Android 13`() {
        assertTrue(boxloreShowsOwnCopyConfirmation(sdkInt = 32))
        assertFalse(boxloreShowsOwnCopyConfirmation(sdkInt = 33))
    }

    private fun clipboardText(): String? =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
}
