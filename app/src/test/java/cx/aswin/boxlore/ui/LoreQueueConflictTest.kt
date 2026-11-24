package cx.aswin.boxlore.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "Replace your queue with Lore?" stays open when the activity is recreated, and Start always
 * finishes: the queue is cleared once and Lore starts once, even though the dialog closes first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LoreQueueConflictTest {
    @get:Rule val compose = createComposeRule()
    private val episode = TestFixtures.episode(id = "lore-1", title = "Why the sea is salty")

    @Test fun `the question stays open with the same episode when the activity is recreated`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        fun show(): ShellPendingDialogs {
            lateinit var dialogs: ShellPendingDialogs
            val activity = controller.get()
            activity.setContent {
                dialogs = rememberShellPendingDialogs(activity)
                LoreQueueConflictHost(dialogs) {}
            }
            shadowOf(Looper.getMainLooper()).idle()
            return dialogs
        }
        val before = show()
        before.loreQueueConflictEpisode = episode

        controller.recreate()
        val after = show()

        assertSame(before, after)
        // The host shows the question whenever it holds an episode (the click tests below).
        assertEquals("lore-1", after.loreQueueConflictEpisode?.id)
    }

    @Test fun `Start clears the queue once and then starts Lore once`() {
        val dialogs = ShellPendingDialogs().apply { loreQueueConflictEpisode = episode }
        var cleared = 0
        val started = mutableListOf<String>()
        compose.setContent {
            LoreQueueConflictHost(dialogs) { pending ->
                delay(50) // Stopping and clearing the queue suspends.
                cleared++
                started += pending.id
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Start Lore queue").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        assertNull(dialogs.loreQueueConflictEpisode)
        assertEquals(1, cleared)
        assertEquals(listOf("lore-1"), started)
    }

    @Test fun `Keep current queue does neither`() {
        val dialogs = ShellPendingDialogs().apply { loreQueueConflictEpisode = episode }
        var calls = 0
        compose.setContent { LoreQueueConflictHost(dialogs) { calls++ } }
        compose.onNodeWithText("Keep current queue").performClick()
        compose.waitForIdle()

        assertNull(dialogs.loreQueueConflictEpisode)
        assertEquals(0, calls)
    }
}
