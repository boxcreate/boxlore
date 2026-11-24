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

    @Test @Config(sdk = [34])
    fun `copying the privacy ID on Android 13 and later leaves the confirmation to the system`() {
        copyDeletionId(context, "privacy-id-1")

        assertEquals("privacy-id-1", clipboardText())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test @Config(sdk = [32])
    fun `copying the privacy ID before Android 13 still confirms it`() {
        copyDeletionId(context, "privacy-id-1")

        assertEquals("privacy-id-1", clipboardText())
        assertNotNull(ShadowToast.getLatestToast())
    }

    @Test fun `boxlore confirms copies itself only before Android 13`() {
        assertTrue(boxloreShowsOwnCopyConfirmation(sdkInt = 32))
        assertFalse(boxloreShowsOwnCopyConfirmation(sdkInt = 33))
    }

    private fun clipboardText(): String? =
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
}
