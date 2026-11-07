package cx.aswin.boxlore.core.playback.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression (Crashlytics): Media3's late notification update, after artwork loads, started the
 * playback service in the foreground without catching Android 12+'s refusal, which crashed the app
 * in the background. The guarded provider delivers that update on the main thread and turns a
 * refusal into a plain notification.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ForegroundSafeNotificationProviderTest {
    /** Media3's provider: hands back a first notification and keeps the late-update callback. */
    private class LateArtwork : MediaNotification.Provider {
        lateinit var late: MediaNotification.Provider.Callback

        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback,
        ): MediaNotification {
            late = onNotificationChangedCallback
            return MediaNotification(1, Notification())
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle) = action == "known"

        val channel: MediaNotification.Provider.NotificationChannelInfo = mock(MediaNotification.Provider.NotificationChannelInfo::class.java)

        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo = channel
    }

    private val artwork = LateArtwork()
    private val refused = mutableListOf<MediaNotification>()
    private val onMain = mutableListOf<Runnable>()
    private val provider = ForegroundSafeNotificationProvider(artwork, runOnMain = { onMain += it }, onRefused = { refused += it })

    private fun open(media3: MediaNotification.Provider.Callback) =
        provider.createNotification(mock(MediaSession::class.java), ImmutableList.of(), mock(MediaNotification.ActionFactory::class.java), media3)

    @Test
    fun aRefusedForegroundStartFromTheLateUpdateShowsThePlainNotificationInsteadOfCrashing() {
        open { throw ForegroundServiceStartNotAllowedException("startForegroundService() not allowed") }
        val withArtwork = MediaNotification(1, Notification())

        artwork.late.onNotificationChanged(withArtwork)
        assertTrue("The late update waits for the main thread", refused.isEmpty())
        onMain.single().run()

        assertSame(withArtwork, refused.single())
    }

    @Test
    fun theLateUpdateReachesMedia3OnTheMainThread() {
        val delivered = mutableListOf<MediaNotification>()
        open { delivered += it }
        val withArtwork = MediaNotification(1, Notification())

        artwork.late.onNotificationChanged(withArtwork)
        onMain.single().run()

        assertSame(withArtwork, delivered.single())
        assertTrue(refused.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun otherFailuresStillSurface() {
        open { throw IllegalStateException("A programming error") }
        artwork.late.onNotificationChanged(MediaNotification(1, Notification()))
        onMain.single().run()
    }

    @Test
    fun theFirstNotificationAndCustomCommandsPassThrough() {
        assertEquals(1, open { }.notificationId)
        assertTrue(provider.handleCustomCommand(mock(MediaSession::class.java), "known", Bundle()))
        assertSame(artwork.channel, provider.notificationChannelInfo)
        assertTrue(isForegroundStartRefusal(ForegroundServiceStartNotAllowedException("refused")))
        assertTrue(!isForegroundStartRefusal(IllegalStateException("other")))
    }
}
