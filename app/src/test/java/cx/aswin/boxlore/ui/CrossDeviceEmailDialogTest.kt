package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CrossDeviceEmailDialogTest {
    @get:Rule val compose = createComposeRule()

    /**
     * The phone sizes host this dialog in a platform window that never settles under Robolectric
     * with a text field inside, so the layout checks run on the tablet panel and its neighbours.
     */
    @Test
    fun `a typed email survives moving between tablet windows and Sign in is a full-height button`() {
        val window = mutableStateOf(BoxloreTestWindows.W600_H900)
        var dismissed = 0
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    CrossDeviceEmailDialog(onDismiss = { dismissed++ }, onConfirm = {})
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("listener@boxlore.example")
        listOf(BoxloreTestWindows.TABLET, BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.W600_H900).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.onNode(hasSetTextAction()).assertTextContains("listener@boxlore.example")
        }
        val signIn = compose.onNodeWithText("Sign In").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Sign in is ${signIn.height} tall", signIn.height >= 48.dp)
        assertEquals(0, dismissed)
    }

    @Test
    fun `a typed email survives the dialog moving between the phone dialog and the tablet panel`() {
        val window = mutableStateOf(BoxloreTestWindows.W599_H900)
        var dismissed = 0
        // The phone dialog's focused field keeps Compose busy, so the clock is stepped by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    CrossDeviceEmailDialog(onDismiss = { dismissed++ }, onConfirm = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
        compose.onNode(hasSetTextAction()).performTextReplacement("listener@boxlore.example")
        listOf(BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900).forEach { next ->
            compose.runOnUiThread { window.value = next }
            compose.mainClock.advanceTimeBy(FRAME_STEP_MS)
            compose.onNode(hasSetTextAction()).assertTextContains("listener@boxlore.example")
            compose.onNodeWithText("Sign In").assertExists()
        }
        assertEquals(0, dismissed)
    }

    @Test
    fun isValidAuthEmail_validEmails_returnsTrue() {
        assertTrue(isValidAuthEmail("listener@boxlore.example"))
        assertTrue(isValidAuthEmail("user.name+tag@subdomain.domain.org"))
        assertTrue(isValidAuthEmail("simple@domain.co"))
    }

    @Test
    fun isValidAuthEmail_invalidEmails_returnsFalse() {
        assertFalse(isValidAuthEmail(""))
        assertFalse(isValidAuthEmail("   "))
        assertFalse(isValidAuthEmail("missingatsign.com"))
        assertFalse(isValidAuthEmail("@missinguser.com"))
        assertFalse(isValidAuthEmail("user@"))
        assertFalse(isValidAuthEmail("user@domain"))
    }

    private companion object {
        const val FRAME_STEP_MS = 500L
    }
}
