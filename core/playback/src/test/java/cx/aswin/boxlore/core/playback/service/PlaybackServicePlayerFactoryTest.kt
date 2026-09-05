package cx.aswin.boxlore.core.playback.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServicePlayerFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val factory = PlaybackServicePlayerFactory(context, scope)

    @Test
    fun `createPlayerSessionActivityIntent returns valid PendingIntent`() {
        val pendingIntent = factory.createPlayerSessionActivityIntent()
        assertNotNull(pendingIntent)
    }

    @Test
    fun `createPlayerSessionActivityIntent catches SecurityException and returns null`() {
        val pendingIntent = factory.createPlayerSessionActivityIntent { _, _, _, _ ->
            throw SecurityException("Too many PendingIntent created for uid 10234")
        }
        assertNull(pendingIntent)
    }

    @Test
    fun `createPlayerSessionActivityIntent configures intent targeting MainActivity with EXTRA_OPEN_PLAYER`() {
        var capturedIntent: Intent? = null
        var capturedFlags: Int? = null

        val pendingIntent = factory.createPlayerSessionActivityIntent { ctx, code, intent, flags ->
            capturedIntent = intent
            capturedFlags = flags
            PendingIntent.getActivity(ctx, code, intent, flags)
        }

        assertNotNull(pendingIntent)
        assertNotNull(capturedIntent)
        assertEquals("cx.aswin.boxlore.MainActivity", capturedIntent?.component?.className)
        assertEquals(context.packageName, capturedIntent?.component?.packageName)
        assertTrue(capturedIntent?.getBooleanExtra("EXTRA_OPEN_PLAYER", false) == true)
        assertEquals(
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            capturedFlags,
        )
    }

    internal class TestMediaLibraryService : androidx.media3.session.MediaLibraryService() {
        override fun onGetSession(
            controllerInfo: androidx.media3.session.MediaSession.ControllerInfo,
        ): androidx.media3.session.MediaLibraryService.MediaLibrarySession? = null
    }

    @Test
    fun `buildMediaLibrarySession with null pendingIntent does not set sessionActivity`() {
        val service = org.robolectric.Robolectric.buildService(TestMediaLibraryService::class.java).create().get()
        val mockPlayer = org.mockito.Mockito.mock(androidx.media3.common.Player::class.java)
        org.mockito.Mockito.`when`(mockPlayer.applicationLooper).thenReturn(android.os.Looper.getMainLooper())
        org.mockito.Mockito.`when`(mockPlayer.canAdvertiseSession()).thenReturn(true)
        org.mockito.Mockito.`when`(mockPlayer.availableCommands)
            .thenReturn(androidx.media3.common.Player.Commands.EMPTY)
        org.mockito.Mockito.`when`(mockPlayer.currentTimeline)
            .thenReturn(androidx.media3.common.Timeline.EMPTY)
        org.mockito.Mockito.`when`(mockPlayer.deviceInfo)
            .thenReturn(androidx.media3.common.DeviceInfo.UNKNOWN)
        org.mockito.Mockito.`when`(mockPlayer.deviceVolume).thenReturn(0)
        org.mockito.Mockito.`when`(mockPlayer.playbackState)
            .thenReturn(androidx.media3.common.Player.STATE_IDLE)
        org.mockito.Mockito.`when`(mockPlayer.playbackParameters)
            .thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
        org.mockito.Mockito.`when`(mockPlayer.audioAttributes)
            .thenReturn(androidx.media3.common.AudioAttributes.DEFAULT)
        val mockCallback =
            org.mockito.Mockito.mock(cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryCallback::class.java)
        val dummyButton =
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Dummy")
                .setSessionCommand(androidx.media3.session.SessionCommand("DUMMY", android.os.Bundle.EMPTY))
                .build()
        val seekButtons =
            PlaybackServicePlayerFactory.SeekButtons(
                seekBack = dummyButton,
                seekForward = dummyButton,
            )
        val customActions =
            PlaybackServicePlayerFactory.CustomActions(
                like = dummyButton,
                addToQueue = dummyButton,
                markComplete = dummyButton,
            )

        val session =
            factory.buildMediaLibrarySession(
                service = service,
                forwardingPlayer = mockPlayer,
                callback = mockCallback,
                pendingIntent = null,
                seekButtons = seekButtons,
                customActions = customActions,
                sessionId = "test_session_null_activity",
            )

        assertNotNull(session)
        assertNull(session.sessionActivity)
        session.release()
    }

    @Test
    fun `buildMediaLibrarySession with non-null pendingIntent sets sessionActivity`() {
        val service = org.robolectric.Robolectric.buildService(TestMediaLibraryService::class.java).create().get()
        val mockPlayer = org.mockito.Mockito.mock(androidx.media3.common.Player::class.java)
        org.mockito.Mockito.`when`(mockPlayer.applicationLooper).thenReturn(android.os.Looper.getMainLooper())
        org.mockito.Mockito.`when`(mockPlayer.canAdvertiseSession()).thenReturn(true)
        org.mockito.Mockito.`when`(mockPlayer.availableCommands)
            .thenReturn(androidx.media3.common.Player.Commands.EMPTY)
        org.mockito.Mockito.`when`(mockPlayer.currentTimeline)
            .thenReturn(androidx.media3.common.Timeline.EMPTY)
        org.mockito.Mockito.`when`(mockPlayer.deviceInfo)
            .thenReturn(androidx.media3.common.DeviceInfo.UNKNOWN)
        org.mockito.Mockito.`when`(mockPlayer.deviceVolume).thenReturn(0)
        org.mockito.Mockito.`when`(mockPlayer.playbackState)
            .thenReturn(androidx.media3.common.Player.STATE_IDLE)
        org.mockito.Mockito.`when`(mockPlayer.playbackParameters)
            .thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
        org.mockito.Mockito.`when`(mockPlayer.audioAttributes)
            .thenReturn(androidx.media3.common.AudioAttributes.DEFAULT)
        val mockCallback =
            org.mockito.Mockito.mock(cx.aswin.boxlore.core.playback.service.auto.AutoBrowseLibraryCallback::class.java)
        val dummyButton =
            androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Dummy")
                .setSessionCommand(androidx.media3.session.SessionCommand("DUMMY", android.os.Bundle.EMPTY))
                .build()
        val seekButtons =
            PlaybackServicePlayerFactory.SeekButtons(
                seekBack = dummyButton,
                seekForward = dummyButton,
            )
        val customActions =
            PlaybackServicePlayerFactory.CustomActions(
                like = dummyButton,
                addToQueue = dummyButton,
                markComplete = dummyButton,
            )
        val pendingIntent = factory.createPlayerSessionActivityIntent()
        assertNotNull(pendingIntent)

        val session =
            factory.buildMediaLibrarySession(
                service = service,
                forwardingPlayer = mockPlayer,
                callback = mockCallback,
                pendingIntent = pendingIntent,
                seekButtons = seekButtons,
                customActions = customActions,
                sessionId = "test_session_with_activity",
            )

        assertNotNull(session)
        assertEquals(pendingIntent, session.sessionActivity)
        session.release()
    }
}
