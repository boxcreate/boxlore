package cx.aswin.boxlore.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDialogSystemBarsTest {
    @Test fun `light full screen dialogs use dark icons while dark and dimmed dialogs use light icons`() {
        assertTrue(dialogUsesDarkSystemBarIcons(Color.White, dimmed = false))
        assertFalse(dialogUsesDarkSystemBarIcons(Color.Black, dimmed = false))
        assertFalse(dialogUsesDarkSystemBarIcons(Color.White, dimmed = true))
        assertFalse(dialogUsesDarkSystemBarIcons(Color.Black, dimmed = true))
    }
}
