package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w393dp-h900dp-mdpi", application = Application::class)
class ImportAccountHandoffTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `sync selection retains the import surface until its fade finishes and prevents duplicate handoffs`() {
        var state by mutableStateOf<OpmlImportState>(OpmlImportState.ShowSelector)
        var opened = 0
        var accountComposed = false
        var accountClicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    if (state is OpmlImportState.Idle) {
                        SideEffect { accountComposed = true }
                        Surface(Modifier.fillMaxSize()) { Button(onClick = { accountClicks++ }) { Text("Account destination") } }
                    }
                    OpmlImportDialog(
                        state,
                        OpmlImportDialogActions(onDismissRequest = { state = OpmlImportState.Idle }, onSyncAccountSelected = {
                        opened++
                        state = OpmlImportState.Idle
                    })
                    )
                }
            }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("In my boxlore account").performClick()
        assertEquals("Sync selection navigates immediately", 1, opened)
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        assertTrue("Account is composed beneath the fading overlay", accountComposed)
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("In my boxlore account").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("In my boxlore account").assertDoesNotExist()
        composeRule.onNodeWithText("Account destination").assertIsDisplayed()
        composeRule.onNodeWithText("Account destination").performClick()
        assertEquals("The completed overlay does not intercept Account touches", 1, accountClicks)
        composeRule.runOnUiThread { state = OpmlImportState.ShowSelector }
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("In my boxlore account").performClick()
        assertEquals(2, opened)
        composeRule.mainClock.autoAdvance = true
    }
}
