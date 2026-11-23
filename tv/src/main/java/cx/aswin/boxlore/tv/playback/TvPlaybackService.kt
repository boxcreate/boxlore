package cx.aswin.boxlore.tv.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cx.aswin.boxlore.tv.MainActivity
import cx.aswin.boxlore.tv.data.TvPlayback

/**
 * Plays audio on the TV and publishes it as a media session, so the remote's media keys, the TV's
 * own media controls and Google Assistant all drive the same player, also with the app closed.
 */
class TvPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val speech = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()
        val player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(speech, true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(TvPlayback.SEEK_BACK_SECONDS * MILLIS)
                .setSeekForwardIncrementMs(TvPlayback.SEEK_FORWARD_SECONDS * MILLIS)
                .build()
        session = MediaSession.Builder(this, player).setSessionActivity(openApp()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Closing the app stops the service unless something is playing. */
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

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}
