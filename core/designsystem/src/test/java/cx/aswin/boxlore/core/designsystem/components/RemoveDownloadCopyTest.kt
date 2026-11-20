package cx.aswin.boxlore.core.designsystem.components

import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoveDownloadCopyTest {
    private fun UiText.english() = resolve(ApplicationProvider.getApplicationContext<android.content.Context>())

    @Test
    fun `a download on this phone is asked about as before`() {
        assertEquals("Remove download?", RemoveDownloadCopy.title(fromWatch = false).english())
        assertEquals(
            "Remove downloaded episode \"Blackshades\"? You'll need an internet connection to listen to it again.",
            RemoveDownloadCopy.body("Blackshades", fromWatch = false).english(),
        )
        assertEquals("Remove this downloaded episode? You'll need an internet connection to listen to it again.", RemoveDownloadCopy.body(null, fromWatch = false).english())
    }

    @Test
    fun `a download on the watch names the watch and says it can be sent again`() {
        assertEquals("Remove from watch?", RemoveDownloadCopy.title(fromWatch = true).english())
        assertEquals("Remove \"Blackshades\" from your watch? You can send it again anytime.", RemoveDownloadCopy.body("Blackshades", fromWatch = true).english())
        assertTrue(RemoveDownloadCopy.body(" ", fromWatch = true).english().startsWith("Remove this episode from your watch?"))
    }
}
