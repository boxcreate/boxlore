package cx.aswin.boxlore.core.catalog

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.TrendingFeed
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody

/**
 * Trending shows as they are parsed. With v2 discovery the stream reads `/v2/trending`, each
 * show's latest episode is translated to its local owner, and a request that yields nothing keeps
 * the last good list of the same request ([V2DiscoveryLastGood]).
 */
internal fun PodcastRepository.trendingPodcastsStream(
    country: String = "us",
    limit: Int = 50,
    category: String? = null,
    offset: Int = 0
): kotlinx.coroutines.flow.Flow<List<Podcast>> = kotlinx.coroutines.flow.flow {
    val podcasts = mutableListOf<Podcast>()
    val v2 = usesV2Discovery()
    val lastGoodKey = trendingLastGoodKey(country, limit, category, offset)
    // Only `/v2` keeps a last good list; read before this request so a failure can show it once.
    var lastGood = if (v2) v2LastGood.podcasts[lastGoodKey] else null
    try {
        android.util.Log.d("BoxCastRepo", "Stream: Requesting trending country=$country, limit=$limit, category=$category, offset=$offset")
        val call =
            if (v2) apiV2.getTrendingStream(country, limit, category, offset) else api.getTrendingStream(publicKey, country, limit, category, offset)
        val response = call.execute()

        response.raw().use {
            android.util.Log.d("BoxCastRepo", "Stream: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")

            val responseBody = response.body()
            if (!response.isSuccessful || responseBody == null) {
                android.util.Log.e("BoxCastRepo", "Stream: Failed! code=${response.code()}, body isNull=${responseBody == null}")
                // Closing a failed streaming answer can still throw below; emit the last good list only once.
                lastGood?.let { emit(it) }
                lastGood = null
                return@use
            }

            readTrendingPodcasts(responseBody, podcasts, v2) { emit(it) }
            if (v2) v2LastGood.podcasts[lastGoodKey] = podcasts.toList()

            android.util.Log.d("BoxCastRepo", "Stream: Parsed ${podcasts.size} podcasts for category=$category")

            // Final emission
            if (podcasts.isNotEmpty()) {
                emit(podcasts)
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("BoxCastRepo", "Stream: Exception for category=$category", e)
        (podcasts.takeIf { it.isNotEmpty() } ?: lastGood)?.let { emit(it) }
    }
}.flowOn(Dispatchers.IO)

private suspend fun PodcastRepository.readTrendingPodcasts(
    responseBody: ResponseBody,
    podcasts: MutableList<Podcast>,
    v2: Boolean,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    val reader = JsonReader(InputStreamReader(responseBody.byteStream(), "UTF-8"))
    reader.isLenient = true
    reader.beginObject()
    while (reader.hasNext()) {
        readTrendingProperty(reader, podcasts, v2, emitSnapshot)
    }
    reader.endObject()
}

private suspend fun PodcastRepository.readTrendingProperty(
    reader: JsonReader,
    podcasts: MutableList<Podcast>,
    v2: Boolean,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    if (reader.nextName() != "feeds") {
        reader.skipValue()
        return
    }

    reader.beginArray()
    readTrendingFeeds(reader, podcasts, v2, emitSnapshot)
    reader.endArray()
}

private suspend fun PodcastRepository.readTrendingFeeds(
    reader: JsonReader,
    podcasts: MutableList<Podcast>,
    v2: Boolean,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    val gson = Gson()
    while (reader.hasNext()) {
        val feed = readTrendingFeed(reader, gson) ?: continue
        val podcast = toPodcastModel(feed)
        // `/v2` names the latest episode by its recipe ID; show it under the phone's own ID.
        podcasts.add(if (v2) discoveryEpisodeIds.latestToLocalOwners(listOf(podcast)).first() else podcast)
        if (shouldEmitTrendingSnapshot(podcasts.size)) {
            emitSnapshot(podcasts.toList())
        }
    }
}

private fun readTrendingFeed(reader: JsonReader, gson: Gson,): TrendingFeed? = try {
    gson.fromJson(reader, TrendingFeed::class.java)
} catch (e: Exception) {
    android.util.Log.e("BoxCastRepo", "Stream: Feed parse error", e)
    null
}

private fun PodcastRepository.toPodcastModel(feed: TrendingFeed): Podcast = Podcast(
    id = feed.id.toString(),
    title = feed.title,
    artist = feed.author ?: "Unknown",
    imageUrl = (feed.artwork ?: feed.image).toHttps(),
    description = feed.description,
    genre = resolvePrimaryGenre(feed.categories),
    latestEpisode = feed.latestEpisode?.let { epItem ->
        mapToEpisode(epItem)?.copy(
            podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
            podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
        )
    },
    medium = feed.medium
)

private fun shouldEmitTrendingSnapshot(podcastCount: Int): Boolean = podcastCount == 1 || podcastCount % 4 == 0
