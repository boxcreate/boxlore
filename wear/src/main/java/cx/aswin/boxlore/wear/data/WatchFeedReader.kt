package cx.aswin.boxlore.wear.data

import android.util.Log
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.core.rss.ParsedFeedItem
import cx.aswin.boxlore.core.rss.RssFeedClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One episode as a show's own feed lists it, under the ID every boxlore app gives it. */
data class WatchFeedEpisode(
    val id: Long,
    val title: String,
    val audioUrl: String,
    val imageUrl: String?,
    val durationSeconds: Int,
    /** Epoch seconds; 0 when the feed gives no date. */
    val publishedAtSeconds: Long,
    /** The feed's own key for the episode: with the show, its identity on every device. */
    val catalogKey: String? = null,
)

/**
 * Reads a show's newest episodes from the show's own feed, the way the phone does, so the watch
 * sees an episode as soon as the publisher lists it. Null means the feed could not be read and
 * the caller should fall back to the API's list.
 */
fun interface WatchFeedReader {
    suspend fun newest(showId: Long, show: V2PodcastResponse): List<WatchFeedEpisode>?
}

class RssWatchFeedReader(
    private val api: BoxLoreApiV2,
    private val client: RssFeedClient = RssFeedClient(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WatchFeedReader {
    override suspend fun newest(showId: Long, show: V2PodcastResponse): List<WatchFeedEpisode>? {
        val epNs = show.validEpNs ?: return fallback(showId, "no episode namespace")
        return try {
            val target = show.readTarget(showId) ?: return fallback(showId, "no feed address")
            val (address, document) = read(showId, show, target) ?: return fallback(showId, "no document")
            // As on the phone: the parser names the feed by its address, and the show's own ID
            // plays no part until the episode IDs are worked out below.
            val items = client.parse(address, document).items
            items.mapNotNull { WatchFeedItems.episode(it, epNs) }.also {
                if (it.isEmpty()) fallback(showId, "no usable items among ${items.size}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Any read or parse failure sends the caller to the API's list instead.
            fallback(showId, failure.javaClass.simpleName)
        }
    }

    /** Notes why a show's feed was not used. The address itself is never logged. */
    private fun fallback(showId: Long, reason: String): List<WatchFeedEpisode>? {
        Log.w(TAG, "Feed of show $showId not used ($reason); falling back to the API list")
        return null
    }

    /** The feed's address and the start of its document: enough for the newest episodes. */
    private suspend fun read(showId: Long, show: V2PodcastResponse, target: V2FeedReadTarget): Pair<String, ByteArray>? =
        when (target) {
            is V2FeedReadTarget.Direct -> client.fetchNewest(target.url, NEWEST_ITEMS)?.result?.let { it.finalUrl to it.body }
            // An http-only address is read for the app by the API; the watch never fetches it itself.
            is V2FeedReadTarget.ViaApi ->
                withContext(io) {
                    api.getFeed(showId).execute().takeIf { it.isSuccessful }?.body()?.use { body ->
                        show.transportUrl?.let { it to body.bytes() }
                    }
                }
        }

    private companion object {
        const val TAG = "WatchFeedReader"
        const val NEWEST_ITEMS = 25
    }
}

/** Pure mapping from a parsed feed item to a watch episode. */
internal object WatchFeedItems {
    /**
     * The episode for [item] in the show's episode namespace [epNs], or null when the item has no
     * audio or no identity the shared ID recipe covers (the phone skips those the same way).
     */
    fun episode(item: ParsedFeedItem, epNs: Long): WatchFeedEpisode? {
        val key = item.catalogKey?.takeIf { item.keyFollowsContract } ?: return null
        val audio = item.episode.audioUrl.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val id = runCatching { EpisodeRecipeId.forNamespace(epNs, key) }.getOrNull() ?: return null
        return WatchFeedEpisode(
            id = id,
            title = item.episode.title,
            audioUrl = audio,
            imageUrl = item.episode.imageUrl?.takeIf { it.isNotBlank() },
            durationSeconds = item.episode.duration,
            publishedAtSeconds = item.episode.publishedDate,
            catalogKey = key,
        )
    }
}
