package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

/** What the watch asks boxlore's API for. Each call answers null when the API could not be reached. */
interface WatchCatalog {
    suspend fun search(query: String): List<SearchFeed>?

    /** A show's details, with the feed address to read and its episode namespace. */
    suspend fun show(showId: Long): V2PodcastResponse?

    /** The API's own episode list: the fallback for a show whose feed cannot be read. */
    suspend fun episodes(showId: Long): List<EpisodeItem>?
}

/** A watch with no API address: nothing can be looked up. */
object NoWatchCatalog : WatchCatalog {
    override suspend fun search(query: String): List<SearchFeed>? = null

    override suspend fun show(showId: Long): V2PodcastResponse? = null

    override suspend fun episodes(showId: Long): List<EpisodeItem>? = null
}

class ApiWatchCatalog(
    private val api: BoxLoreApiV2,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WatchCatalog {
    override suspend fun search(query: String): List<SearchFeed>? = call { api.search(query, SEARCH_LIMIT) }?.feeds

    override suspend fun show(showId: Long): V2PodcastResponse? = call { api.getPodcast(showId) }

    override suspend fun episodes(showId: Long): List<EpisodeItem>? = call { api.getEpisodes(showId, EPISODE_LIMIT) }?.items

    /** Any failure, from no network to an error answer, reads as "could not reach the API". */
    private suspend fun <T> call(request: () -> Call<T>): T? =
        withContext(io) {
            runCatching { request().execute() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
        }

    private companion object {
        /** A watch screen shows a handful of results; more only costs data. */
        const val SEARCH_LIMIT = 12
        const val EPISODE_LIMIT = 25
    }
}
