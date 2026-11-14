package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoveDownloadCopyTest {
    @Test
    fun `a download on this phone is asked about as before`() {
        assertEquals("Remove download?", RemoveDownloadCopy.title(fromWatch = false))
        assertEquals(
            "Remove downloaded episode \"Blackshades\"? You'll need an internet connection to listen to it again.",
            RemoveDownloadCopy.body("Blackshades", fromWatch = false),
        )
        assertEquals("Remove this downloaded episode? You'll need an internet connection to listen to it again.", RemoveDownloadCopy.body(null, fromWatch = false))
    }

    @Test
    fun `a download on the watch names the watch and says it can be sent again`() {
        assertEquals("Remove from watch?", RemoveDownloadCopy.title(fromWatch = true))
        assertEquals("Remove \"Blackshades\" from your watch? You can send it again anytime.", RemoveDownloadCopy.body("Blackshades", fromWatch = true))
        assertTrue(RemoveDownloadCopy.body(" ", fromWatch = true).startsWith("Remove this episode from your watch?"))
    }
}
