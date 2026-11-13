package cx.aswin.boxlore.core.designsystem.components

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepTimerPopupTest {
    @get:Rule val compose = createComposeRule()

    private val picked = mutableListOf<Int>()
    private val dismissed = mutableListOf<SleepTimerPopupDismissReason>()
    private var window by mutableStateOf(360.dp)
    private var shown by mutableStateOf(true)

    private fun show(restoration: StateRestorationTester? = null) {
        compose.mainClock.autoAdvance = false
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            BoxLoreTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.width(window)) {
                    SleepTimerPopup(
                        visible = shown,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag(PROMPT),
                        onSelectDuration = { picked += it },
                        onDismiss = { dismissed += it },
                    )
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        compose.mainClock.advanceTimeBy(1_200L)
    }

    @Test
    fun `with the watch just paused the same card offers it back, and waits five seconds longer`() {
        var playedOnWatch = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.width(window)) {
                    SleepTimerPopup(
                        visible = shown,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag(PROMPT),
                        onPlayOnWatch = { playedOnWatch++ },
                        onSelectDuration = { picked += it },
                        onDismiss = { dismissed += it },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_200L)
        // Both questions on one card.
        compose.onNodeWithText("Late night listening?").assertExists()
        compose.onNodeWithText("45m").assertExists()
        compose.onNodeWithText("Your watch was paused.").assertExists()
        compose.onNodeWithText("Play on watch instead").assertExists()
        // Still there after the usual eight seconds; gone only after thirteen.
        compose.mainClock.advanceTimeBy(8_000L)
        assertTrue(dismissed.isEmpty())
        compose.mainClock.advanceTimeBy(5_200L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Timeout), dismissed)
        dismissed.clear()

        compose.onNodeWithText("Play on watch instead").performClick()
        assertEquals(1, playedOnWatch)
        assertEquals(listOf(SleepTimerPopupDismissReason.PlayedOnWatch), dismissed)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `the prompt by itself still leaves after eight seconds and offers no watch`() {
        show()
        compose.onNodeWithText("Play on watch instead").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(7_200L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Timeout), dismissed)
    }

    @Test
    fun `picking a time sets it at once, confirms when playback will pause, then lets go`() {
        show()
        compose.onNodeWithText("Late night listening?").assertExists()
        listOf("30m", "45m", "1h", "2h", "End of episode").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("45m").performClick()
        assertEquals(listOf(45), picked)
        compose.mainClock.advanceTimeBy(1_800L)
        compose.onNodeWithText("Pausing in 45 min").assertExists()
        compose.onNodeWithText("Good night.").assertExists()
        assertTrue(dismissed.isEmpty())
        compose.mainClock.advanceTimeBy(1_400L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
    }

    @Test
    fun `a prompt shown again after a pick starts afresh, not on the last confirmation`() {
        show()
        compose.onNodeWithText("45m").performClick()
        compose.mainClock.advanceTimeBy(3_200L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
        // The app hides it, as the repository does on the confirmation.
        toggle(false)
        compose.mainClock.advanceTimeBy(600L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
        // Shown again later (the next night, or the next play with the night window bypassed).
        toggle(true)
        compose.mainClock.advanceTimeBy(1_200L)
        compose.onNodeWithText("Pausing in 45 min").assertDoesNotExist()
        compose.onNodeWithText("Late night listening?").assertExists()
        val times = compose.onNodeWithText("30m").getUnclippedBoundsInRoot()
        assertTrue("The choices are laid out, not folded away", (times.bottom - times.top).value > 0f)
        compose.onNodeWithText("1h").performClick()
        assertEquals(listOf(45, 60), picked)
        compose.mainClock.advanceTimeBy(1_800L)
        compose.onNodeWithText("Pausing in 1 hr").assertExists()
    }

    @Test
    fun `hidden from outside while confirming, the confirmation is reported once`() {
        show()
        compose.onNodeWithText("2h").performClick()
        compose.mainClock.advanceTimeBy(500L)
        toggle(false)
        compose.mainClock.advanceTimeBy(4_000L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
    }

    @Test
    fun `Dismiss after a pick only ends the confirmation, reported once`() {
        show()
        compose.onNodeWithText("30m").performClick()
        compose.mainClock.advanceTimeBy(400L)
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
        toggle(false)
        compose.mainClock.advanceTimeBy(4_000L)
        assertEquals(listOf(30), picked)
        assertEquals(listOf(SleepTimerPopupDismissReason.Confirmation), dismissed)
    }

    @Test
    fun `a prompt dismissed just before it would time out does not also time out`() {
        show()
        // 7.9 seconds into the 8-second auto-hide.
        compose.mainClock.advanceTimeBy(6_700L)
        compose.onNodeWithContentDescription("Dismiss").performClick()
        toggle(false)
        compose.mainClock.advanceTimeBy(1_000L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Manual), dismissed)
    }

    @Test
    fun `after a pick screen readers reach the answer, not the faded question or the old times`() {
        show()
        compose.onNodeWithText("2h").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithText("Late night listening?").assertDoesNotExist()
        compose.onNodeWithText("30m").assertDoesNotExist()
        compose.onNodeWithText("Pausing in 2 hr").assertExists()
    }

    @Test
    fun `with system animations off every step is at its final state at once`() {
        setAnimatorScale(0f)
        try {
            show()
            val open = promptHeight()
            compose.onNodeWithText("45m").performClick()
            // One frame: the choices are already folded away and the answer is there.
            compose.mainClock.advanceTimeBy(16L)
            compose.onNodeWithText("Pausing in 45 min").assertExists()
            assertTrue("The choices fold away at once", promptHeight() < open - 80f)
            assertEquals(listOf(45), picked)
        } finally {
            setAnimatorScale(1f)
        }
    }

    @Test
    fun `whole hours and the end of the episode read as such`() {
        show()
        compose.onNodeWithText("End of episode").performClick()
        compose.mainClock.advanceTimeBy(1_800L)
        compose.onNodeWithText("Pausing after this episode").assertExists()
        assertEquals(listOf(999), picked)
    }

    @Test
    fun `Dismiss and leaving it alone close the prompt for their own reasons`() {
        show()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf(SleepTimerPopupDismissReason.Manual), dismissed)
        dismissed.clear()
        compose.mainClock.advanceTimeBy(8_000L)
        assertEquals(listOf(SleepTimerPopupDismissReason.Timeout), dismissed)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `the confirmation survives recreation without setting the timer again`() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("1h").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(1_800L)
        compose.onNodeWithText("Pausing in 1 hr").assertExists()
        assertEquals(listOf(60), picked)
    }

    @Test
    fun `the prompt fills a phone and stays card-sized on a wide window, through window changes both ways`() {
        show()
        assertWidth(360.dp - 32.dp)
        compose.onNodeWithText("2h").performClick()
        window = 840.dp
        compose.mainClock.advanceTimeBy(400L)
        assertWidth(420.dp)
        window = 360.dp
        compose.mainClock.advanceTimeBy(1_600L)
        assertWidth(360.dp - 32.dp)
        compose.onNodeWithText("Pausing in 2 hr").assertExists()
        assertEquals(listOf(120), picked)
    }

    /** Shows or hides the prompt as the app does, letting Compose take the change in before time moves on. */
    private fun toggle(value: Boolean) {
        shown = value
        compose.waitForIdle()
    }

    /** The system animator duration scale, as Settings sets it: 0 turns animations off. */
    private fun setAnimatorScale(scale: Float) {
        ReflectionHelpers.callStaticMethod<Unit>(
            ValueAnimator::class.java,
            "setDurationScale",
            ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType, scale),
        )
    }

    private fun promptHeight(): Float {
        val bounds = compose.onNodeWithTag(PROMPT).getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    private fun assertWidth(expected: Dp) {
        val bounds = compose.onNodeWithTag(PROMPT).getUnclippedBoundsInRoot()
        assertEquals(expected.value, (bounds.right - bounds.left).value, 1f)
    }

    private companion object {
        const val PROMPT = "sleepPrompt"
    }
}
