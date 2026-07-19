package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.catalog.content.ContentContext
import cx.aswin.boxlore.core.catalog.content.ContentSectionsDaypartResolver
import cx.aswin.boxlore.core.catalog.content.GroupedContentSections
import cx.aswin.boxlore.core.catalog.content.buildContentSignalProfile
import cx.aswin.boxlore.core.catalog.content.contentSectionsCacheKey
import cx.aswin.boxlore.core.catalog.content.contentSectionsProfileFingerprint
import cx.aswin.boxlore.core.catalog.content.contentSectionsStaleCachePrefix
import cx.aswin.boxlore.core.catalog.content.toGroupedContentSections
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Transcript
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import okhttp3.ResponseBody
import java.io.InputStreamReader

import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.ContentSectionRecentSeedDto
import cx.aswin.boxlore.core.network.model.ContentSectionSeedFallbackDto
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Response
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.rss.RssPodcastRepository

internal fun PodcastRepository.trendingPodcastsStream(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0): kotlinx.coroutines.flow.Flow<List<Podcast>> = kotlinx.coroutines.flow.flow {
    val podcasts = mutableListOf<Podcast>()
    try {
        android.util.Log.d("BoxCastRepo", "Stream: Requesting trending country=$country, limit=$limit, category=$category, offset=$offset")
        val call = api.getTrendingStream(publicKey, country, limit, category, offset)
        val response = call.execute()

        android.util.Log.d("BoxCastRepo", "Stream: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")

        if (!response.isSuccessful || response.body() == null) {
            android.util.Log.e("BoxCastRepo", "Stream: Failed! code=${response.code()}, body isNull=${response.body() == null}")
            return@flow
        }

        val responseBody = response.body()!!
        val stream = responseBody.byteStream()
        val reader = com.google.gson.stream.JsonReader(java.io.InputStreamReader(stream, "UTF-8"))

        reader.isLenient = true

        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "feeds") {
                reader.beginArray() // [
                while (reader.hasNext()) {
                    try {
                        val feed = com.google.gson.Gson().fromJson<cx.aswin.boxlore.core.network.model.TrendingFeed>(
                            reader,
                            cx.aswin.boxlore.core.network.model.TrendingFeed::class.java
                        )

                        if (feed != null) {
                            val podcast = Podcast(
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
                            podcasts.add(podcast)

                            if (podcasts.size % 4 == 0 || podcasts.size == 1) {
                                 emit(podcasts.toList())
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastRepo", "Stream: Feed parse error", e)
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()

        android.util.Log.d("BoxCastRepo", "Stream: Parsed ${podcasts.size} podcasts for category=$category")

        // Final emission
        if (podcasts.isNotEmpty()) {
            emit(podcasts)
        }

    } catch (e: Exception) {
        android.util.Log.e("BoxCastRepo", "Stream: Exception for category=$category", e)
        if (podcasts.isNotEmpty()) emit(podcasts)
    }
}.flowOn(Dispatchers.IO)
