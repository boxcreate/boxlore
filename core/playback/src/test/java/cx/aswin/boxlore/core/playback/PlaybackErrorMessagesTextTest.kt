package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.text.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The English words behind each playback error toast: paused outcomes never claim to be skipping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackErrorMessagesTextTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(action: PlaybackErrorSkipGuard.Action, isRemote: Boolean): String = context.getString(PlaybackErrorMessages.forAction(action, isRemote))

    @Test
    fun `stop and paused messages never say skipping, and Cast ones name Cast`() {
        for (action in listOf(PlaybackErrorSkipGuard.Action.STOP, PlaybackErrorSkipGuard.Action.SKIP_PAUSED)) {
            assertFalse(text(action, isRemote = false).contains("skipping"))
            assertFalse(text(action, isRemote = true).contains("skipping"))
            assertTrue(text(action, isRemote = true).contains("Cast"))
        }
        assertEquals("That episode couldn't play. The next one is ready.", text(PlaybackErrorSkipGuard.Action.SKIP_PAUSED, isRemote = false))
        assertEquals("Stream unavailable, skipping…", text(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, isRemote = false))
    }

    @Test
    fun `a Cast device without a name reads Cast device`() {
        val unnamed = PlaybackRouteResolver.resolveState(isRemote = true, deviceName = " ", volume = 0, minimumVolume = 0, maximumVolume = 10, isMuted = false)
        val named = unnamed.copy(deviceName = "Living room")
        assertEquals("Cast device", unnamed.displayNameText.resolve(context))
        assertEquals("Living room", named.displayNameText.resolve(context))
    }
}
