package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem

internal object CastMediaMetadata {
    private const val REMOTE_URI_KEY = "boxlore.cast.remote_uri"

    fun queueTitle(title: CharSequence?): String? = title?.toString()?.trim()?.takeIf(String::isNotEmpty)

    fun extrasWithRemoteUri(
        existing: Bundle?,
        remoteUri: String?,
    ): Bundle? {
        if (!CastMediaEligibility.isCastable(remoteUri)) return existing
        return Bundle(existing ?: Bundle()).apply {
            putString(REMOTE_URI_KEY, remoteUri)
        }
    }

    fun remoteUri(mediaItem: MediaItem): String? =
        mediaItem.mediaMetadata.extras
            ?.getString(REMOTE_URI_KEY)
            ?.takeIf(CastMediaEligibility::isCastable)
}

/**
 * Keeps local/download playback URIs on-device while sending the original public stream URL
 * to a Cast receiver. Regular streaming items pass through unchanged.
 */
internal class BoxLoreCastMediaItemConverter : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val remoteUri = CastMediaMetadata.remoteUri(mediaItem)
        val castItem =
            if (remoteUri == null) {
                mediaItem
            } else {
                mediaItem.buildUpon().setUri(remoteUri).build()
            }
        return delegate.toMediaQueueItem(castItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = delegate.toMediaItem(mediaQueueItem)
}

object CastMediaEligibility {
    fun isCastable(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val parsed = runCatching { java.net.URI(uri) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !isDeviceLocalHost(host)
    }

    private fun isDeviceLocalHost(host: String): Boolean {
        if (
            host == "localhost" ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host.endsWith(".local") ||
            host.startsWith("127.") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("169.254.")
        ) {
            return true
        }
        val octets = host.split('.')
        if (octets.size != 4 || octets[0] != "172") return false
        val secondOctet = octets[1].toIntOrNull() ?: return false
        return secondOctet in 16..31
    }
}
