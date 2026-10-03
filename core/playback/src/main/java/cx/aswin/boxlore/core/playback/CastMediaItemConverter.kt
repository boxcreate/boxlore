package cx.aswin.boxlore.core.playback

import android.os.Bundle
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem
import java.net.InetAddress
import org.json.JSONObject

internal object CastMediaMetadata {
    internal const val REMOTE_URI_KEY = "boxlore.cast.remote_uri"

    fun queueTitle(title: CharSequence?): String? = title?.toString()?.trim()?.takeIf(String::isNotEmpty)

    fun extrasWithRemoteUri(existing: Bundle?, remoteUri: String?,): Bundle? {
        if (!CastMediaEligibility.isCastable(remoteUri)) return existing
        return Bundle(existing ?: Bundle()).apply {
            putString(REMOTE_URI_KEY, remoteUri)
        }
    }

    fun remoteUri(mediaItem: MediaItem): String? = mediaItem.mediaMetadata.extras
        ?.getString(REMOTE_URI_KEY)
        ?.takeIf(CastMediaEligibility::isCastable)

    fun effectiveUri(mediaItem: MediaItem): String? = remoteUri(mediaItem)
        ?: mediaItem.localConfiguration?.uri?.toString()

    fun isAnalyticsSafeExtra(key: String): Boolean = key != REMOTE_URI_KEY
}

/**
 * Keeps local/download playback URIs on-device while sending the original public stream URL
 * to a Cast receiver. Regular streaming items pass through unchanged.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class BoxLoreCastMediaItemConverter : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val contextKey = "boxlore.queue.context"

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val remoteUri = CastMediaMetadata.remoteUri(mediaItem)
        val effectiveUri = CastMediaMetadata.effectiveUri(mediaItem)
        require(CastMediaEligibility.isCastable(effectiveUri)) {
            "Cast media URI must be public HTTP(S)"
        }
        val castItem =
            if (remoteUri == null) {
                mediaItem
            } else {
                mediaItem.buildUpon().setUri(remoteUri).build()
            }
        val converted = delegate.toMediaQueueItem(castItem)
        if (!PlaybackQueueContext.isContextItem(castItem)) return converted
        // Media3's default converter does not serialize MediaMetadata.extras.
        val customData = JSONObject(converted.customData?.toString() ?: "{}")
            .put(contextKey, PlaybackQueueContext.NEW_EPISODES)
        return MediaQueueItem.Builder(converted)
            .setCustomData(customData)
            .build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val item = delegate.toMediaItem(mediaQueueItem)
        if (mediaQueueItem.customData?.optString(contextKey) != PlaybackQueueContext.NEW_EPISODES) return item
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            putString("source_entry_point", PlaybackQueueContext.NEW_EPISODES)
        }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }
}

object CastMediaEligibility {
    fun isCastable(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val parsed = runCatching { java.net.URI(uri) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && isPublicHost(host)
    }

    private fun isPublicHost(rawHost: String): Boolean {
        val host = rawHost.removePrefix("[").removeSuffix("]")
        if (host == "localhost" || host.endsWith(".local")) return false
        if (!host.contains('.') && !host.contains(':')) return false
        val literalAddress = parseLiteralAddress(host) ?: return true
        return !literalAddress.isAnyLocalAddress &&
            !literalAddress.isLoopbackAddress &&
            !literalAddress.isLinkLocalAddress &&
            !literalAddress.isSiteLocalAddress &&
            !literalAddress.isMulticastAddress &&
            !literalAddress.isUniqueLocalIpv6()
    }

    private fun parseLiteralAddress(host: String): InetAddress? {
        val isIpv4Literal = host.all { it.isDigit() || it == '.' }
        val isIpv6Literal = ':' in host
        if (!isIpv4Literal && !isIpv6Literal) return null
        return runCatching { InetAddress.getByName(host) }.getOrNull()
    }

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
