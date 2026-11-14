package cx.aswin.boxlore.wear.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cx.aswin.boxlore.wear.MainActivity
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.ui.WatchRoutes

/**
 * Plays audio on the watch itself and publishes it as a media session, so the watch's own media
 * controls, Bluetooth headphones and the notification all drive the same player.
 */
class WatchPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val speech = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()
        val player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(speech, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(NowPlaying.DEFAULT_SEEK_BACK_SECONDS * MILLIS)
                .setSeekForwardIncrementMs(NowPlaying.DEFAULT_SEEK_FORWARD_SECONDS * MILLIS)
                .build()
        session = MediaSession.Builder(this, player).setSessionActivity(openPlayer()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops the service unless something is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun openPlayer(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).putExtra(WatchRoutes.EXTRA_ROUTE, WatchRoutes.PLAYER)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}
