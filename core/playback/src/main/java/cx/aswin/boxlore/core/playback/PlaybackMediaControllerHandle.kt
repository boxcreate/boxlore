package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await

/**
 * Shared MediaController handle for PlaybackRepository coordinators.
 *
 * The connection future fails when the session rejects the controller (for example while the
 * service is recreating its session), so callers must never read it without handling failure.
 */
internal class PlaybackMediaControllerHandle {
    var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null

    /** Rebuilds the connection after a rejected or failed attempt. */
    var reconnect: (() -> Unit)? = null
    private var reconnector: MediaControllerReconnector? = null

    /** Retries failed connections through [connect], within [MediaControllerReconnectPolicy]. */
    fun enableReconnect(scope: CoroutineScope, connect: () -> Unit) {
        val retry = MediaControllerReconnector(scope, { future == null && controller == null }, connect)
        reconnector = retry
        reconnect = retry::schedule
    }

    /** The connected controller, or null when no connection is available yet. */
    suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        val pending = future ?: return null
        return try {
            pending.await().also { controller = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onConnectionFailed(pending, e)
            null
        }
    }

    /** The controller from a finished [attempt], or null after reporting its failure. */
    fun connectedOrNull(attempt: ListenableFuture<MediaController>): MediaController? = try {
        attempt.get().also { reconnector?.onConnected() }
    } catch (e: Exception) {
        onConnectionFailed(attempt, e)
        null
    }

    /** Clears a failed attempt so the next one can start, then asks the owner to reconnect. */
    fun onConnectionFailed(failed: ListenableFuture<MediaController>, error: Throwable) {
        Log.w(TAG, "MediaController connection failed", error)
        if (future !== failed) return
        future = null
        reconnect?.invoke()
    }

    private companion object {
        const val TAG = "PlaybackRepo"
    }
}
