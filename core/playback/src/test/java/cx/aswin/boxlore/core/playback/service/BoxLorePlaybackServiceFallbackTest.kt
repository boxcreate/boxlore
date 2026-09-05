package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxLorePlaybackServiceFallbackTest {

    private class TestPlaybackService : BoxLorePlaybackService() {
        init {
            attachBaseContext(ApplicationProvider.getApplicationContext())
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `initMediaSession falls back safely when assembleSession throws SecurityException`() {
        val service = TestPlaybackService()
        val mockPlayer = mock(Player::class.java)
        service.playbackPlayer = mockPlayer

        val dummyButton =
            CommandButton.Builder()
                .setDisplayName("Dummy")
                .setSessionCommand(SessionCommand("DUMMY", Bundle.EMPTY))
                .build()

        val dummySeekButtons =
            PlaybackServicePlayerFactory.SeekButtons(
                seekBack = dummyButton,
                seekForward = dummyButton,
            )
        val dummyCustomActions =
            PlaybackServicePlayerFactory.CustomActions(
                like = dummyButton,
                addToQueue = dummyButton,
                markComplete = dummyButton,
            )

        val throwingFactory =
            object : PlaybackServicePlayerFactory(context, CoroutineScope(Dispatchers.Unconfined)) {
                override fun assembleSession(
                    service: MediaLibraryService,
                    player: Player,
                    config: SessionConfig,
                ): BuiltSession = throw SecurityException("Too many PendingIntent created for uid")

                override fun buildSeekButtons(seekBackwardMs: Long, seekForwardMs: Long): SeekButtons =
                    dummySeekButtons

                override fun buildCustomActions(): CustomActions =
                    dummyCustomActions
            }

        service.customPlayerFactory = throwingFactory
        service.initMediaSession(mockPlayer)

        // Verify mediaSession is null and fallback action buttons are initialized
        assertNull(service.mediaSession)
        val mockControllerInfo = mock(MediaSession.ControllerInfo::class.java)
        assertNull(service.onGetSession(mockControllerInfo))
        assertNotNull(service.seekBackAction)
        assertNotNull(service.seekForwardAction)
        assertNotNull(service.likeAction)
        assertNotNull(service.addToQueueAction)
        assertNotNull(service.markCompleteAction)

        // Verify teardown releases the owned player through fallback path
        service.releasePlayers()
        verify(mockPlayer).release()
        assertNull(service.playbackPlayer)
    }
}
