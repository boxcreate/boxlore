package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

/** What the TV asks boxlore's API for. Each call answers null when the API could not be reached. */
interface TvCatalog {
    suspend fun search(query: String): List<SearchFeed>?

    /** A show's details, with the feed address to read and its episode namespace. */
    suspend fun show(showId: Long): V2PodcastResponse?

    /** The API's own episode list: the fallback for a show whose feed cannot be read. */
    suspend fun episodes(showId: Long): List<EpisodeItem>?

    /** Shows popular now in [country] (two letters, lower case), in one [genre] or all of them. */
    suspend fun trending(country: String, genre: String? = null): List<TrendingFeed>?
}

/** A TV with no API address: nothing can be looked up. */
object NoTvCatalog : TvCatalog {
    override suspend fun search(query: String): List<SearchFeed>? = null

    override suspend fun show(showId: Long): V2PodcastResponse? = null

    override suspend fun episodes(showId: Long): List<EpisodeItem>? = null

    override suspend fun trending(country: String, genre: String?): List<TrendingFeed>? = null
}

class ApiTvCatalog(
    private val api: BoxLoreApiV2,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TvCatalog {
    override suspend fun search(query: String): List<SearchFeed>? = call { api.search(query, SEARCH_LIMIT) }?.feeds

    override suspend fun show(showId: Long): V2PodcastResponse? = call { api.getPodcast(showId) }

    override suspend fun episodes(showId: Long): List<EpisodeItem>? = call { api.getEpisodes(showId, EPISODE_LIMIT) }?.items

    override suspend fun trending(country: String, genre: String?): List<TrendingFeed>? = call { api.getTrending(country, TRENDING_LIMIT, genre) }?.feeds

    /** Any failure, from no network to an error answer, reads as "could not reach the API". */
    private suspend fun <T> call(request: () -> Call<T>): T? =
        withContext(io) {
            runCatching { request().execute() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
        }

    private companion object {
        const val SEARCH_LIMIT = 24
        const val EPISODE_LIMIT = 50
        const val TRENDING_LIMIT = 24
    }
}
