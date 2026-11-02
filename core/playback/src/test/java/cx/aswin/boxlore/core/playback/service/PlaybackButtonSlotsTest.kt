package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue 1231: with no slots, Media3 put the first button (rewind) in the forward slot whenever
 * nothing was queued next, and Samsung's Now Bar showed previous, play, rewind. Each button now
 * names its slot, so Media3 drops previous/next from the session's actions and system players show
 * rewind, play, forward.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackButtonSlotsTest {
    // This module's tests run without merged resources; the labels don't matter here.
    private val context: Context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
        private val labels = object : Resources(baseContext.assets, baseContext.resources.displayMetrics, baseContext.resources.configuration) {
            override fun getString(id: Int): String = "label"

            override fun getString(id: Int, vararg formatArgs: Any?): String = "label"
        }

        override fun getResources(): Resources = labels
    }
    private val factory = PlaybackServicePlayerFactory(context, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `rewind takes the back slot, forward the forward slot and Mark played the overflow`() {
        val seek = factory.buildSeekButtons(seekBackwardMs = 15_000L, seekForwardMs = 45_000L)
        val markComplete = factory.buildCustomActions().markComplete

        val buttons = playbackButtonPreferences(seek.seekBack, seek.seekForward, markComplete)

        assertEquals(listOf("SEEK_BACK", "SEEK_FORWARD", AutoBrowseContract.COMMAND_MARK_COMPLETE), buttons.map { it.sessionCommand?.customAction })
        assertEquals(listOf(listOf(CommandButton.SLOT_BACK), listOf(CommandButton.SLOT_FORWARD), listOf(CommandButton.SLOT_OVERFLOW)), buttons.map { it.slots.asList() })
    }

    @Test
    fun `custom skip lengths keep the same slots`() {
        val seek = factory.buildSeekButtons(seekBackwardMs = 5_000L, seekForwardMs = 90_000L)

        assertEquals(listOf(CommandButton.SLOT_BACK), seek.seekBack.slots.asList())
        assertEquals(listOf(CommandButton.SLOT_FORWARD), seek.seekForward.slots.asList())
    }
}
