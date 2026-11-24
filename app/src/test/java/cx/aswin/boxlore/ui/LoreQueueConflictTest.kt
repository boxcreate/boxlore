package cx.aswin.boxlore.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield
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
 * "Start a Lore queue?" stays open when the activity is recreated, and Start always
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
                yield() // Stopping and clearing the queue suspends.
                cleared++
                started += pending.id
            }
        }
        compose.onNodeWithText("Start Lore queue").performClick()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(dialogs.loreQueueConflictEpisode)
        assertEquals(1, cleared)
        assertEquals(listOf("lore-1"), started)
    }

    @Test fun `an accepted Start interrupted by a recreation after the queue clears starts Lore once`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val finish = CompletableDeferred<Unit>()
        var cleared = 0
        val started = mutableListOf<String>()
        val startLore: suspend (Episode) -> Unit = { pending ->
            cleared++
            finish.await() // Lore's queue is still being built when the activity is recreated.
            started += pending.id
        }
        fun show(): ShellPendingDialogs {
            lateinit var dialogs: ShellPendingDialogs
            val activity = controller.get()
            activity.setContent {
                dialogs = rememberShellPendingDialogs(activity)
                LoreQueueConflictHost(dialogs, startLore)
            }
            shadowOf(Looper.getMainLooper()).idle()
            return dialogs
        }
        val dialogs = show()
        dialogs.loreQueueConflictEpisode = episode
        shadowOf(Looper.getMainLooper()).idle()
        // What the dialog's Start does.
        dialogs.startLoreQueue(episode, startLore)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, cleared)

        controller.recreate()
        val after = show()
        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(dialogs, after)
        assertEquals(1, cleared)
        assertEquals(listOf("lore-1"), started)
        assertNull("the question stays closed", after.loreQueueConflictEpisode)
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
