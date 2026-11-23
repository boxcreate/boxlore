package cx.aswin.boxlore

import android.app.Application
import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A notification tap opens the player once: not again when the activity is recreated with the same launch intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerOpenIntentTest {
    @Test
    fun `fresh launches and new intents open the player, restored launches keep the saved request, and the flag is used up`() {
        val launch = tap()
        assertEquals(1_000L, takePlayerOpenRequest(launch, restored = false, now = 1_000L))
        assertFalse(launch.hasExtra(EXTRA))

        // Rotating recreates the activity with the original launch intent, flag included. It keeps the
        // request it saved, so a player that had not opened yet still opens, and makes no new one.
        val saved = Bundle().apply { putPlayerOpenRequest(1_000L) }
        assertEquals(1_000L, saved.savedPlayerOpenRequest())
        val restoredLaunch = tap()
        assertNull(takePlayerOpenRequest(restoredLaunch, restored = true, now = 2_000L))
        assertFalse(restoredLaunch.hasExtra(EXTRA))
        assertEquals(0L, null.savedPlayerOpenRequest())

        // A later tap reaches the running activity through onNewIntent.
        val newIntent = tap()
        assertEquals(3_000L, takePlayerOpenRequest(newIntent, restored = false, now = 3_000L))
        assertFalse(newIntent.hasExtra(EXTRA))

        assertNull(takePlayerOpenRequest(Intent(), restored = false, now = 4_000L))
    }

    private fun tap() = Intent().putExtra(EXTRA, true)

    private companion object {
        const val EXTRA = "EXTRA_OPEN_PLAYER"
    }
}
