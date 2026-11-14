package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The watch's real library: what the listener keeps comes from [WatchLibraryStore]; shows and
 * search come from boxlore's API through [WatchCatalog]; episodes come from each show's own feed
 * through [WatchFeedReader]. Fetched shows and episodes are held in memory.
 */
class LiveWatchLibrary(
    private val catalog: WatchCatalog,
    private val feeds: WatchFeedReader,
    private val store: WatchLibraryStore,
    private val downloadManager: WatchDownloads,
    private val scope: CoroutineScope,
    /** A show's episodes as the phone has them saved; null when there is no phone to ask. */
    private val phoneEpisodes: suspend (showId: String, show: WatchShow?) -> List<WatchEpisode>? = { _, _ -> null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Episodes the phone names outside any show's list, such as its queue, so their pages open here too. */
    namedByPhone: Flow<List<WatchEpisode>> = emptyFlow(),
) : WatchLibrary {
    private val fetchedShows = MutableStateFlow<Map<String, WatchShow>>(emptyMap())
    private val fetchedEpisodes = MutableStateFlow<Map<String, WatchEpisode>>(emptyMap())
    private val showEpisodes = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    init {
        scope.launch { namedByPhone.collect { named -> fetchedEpisodes.update { it + named.associateBy(WatchEpisode::id) } } }
    }

    override val shows: StateFlow<List<WatchShow>> = kept { it.shows }
    override val jumpBackIn: StateFlow<List<WatchEpisode>> = kept { it.jumpBackIn }
    override val upNext: StateFlow<List<WatchEpisode>> = kept { it.upNext }
    override val downloads: StateFlow<List<WatchEpisode>> = kept { it.downloads }
    override val storedBytes: StateFlow<Long> = kept { it.storedBytes }
    override val downloadProblems: Flow<WatchDownloadProblem> = downloadManager.problems

    /** Both lists come from a linked phone; a watch on its own cannot know what is new. */
    override val newEpisodes: StateFlow<List<WatchEpisode>> = kept { it.newEpisodes }
    override val dailyMix: StateFlow<List<WatchEpisode>> = kept { it.dailyMix }
    override val phoneLinked: StateFlow<Boolean> = kept { it.phoneLinked }

    override fun show(showId: String): Flow<WatchShow?> =
        combine(fetchedShows, store.state) { fetched, state ->
            (state.shows.firstOrNull { it.id == showId } ?: fetched[showId])?.copy(subscribed = state.isSubscribed(showId))
        }.onStart { scope.launch { loadShow(showId) } }

    override fun episodes(showId: String): Flow<List<WatchEpisode>> =
        combine(showEpisodes, fetchedEpisodes, store.state) { byShow, fetched, state ->
            byShow[showId].orEmpty().mapNotNull(fetched::get).map(state::merged)
        }.onStart { scope.launch { loadEpisodes(showId) } }

    override fun episode(episodeId: String): Flow<WatchEpisode?> =
        combine(fetchedEpisodes, store.state) { fetched, state ->
            (fetched[episodeId] ?: state.episodes[episodeId])?.let(state::merged)
        }

    /** The episode as the watch knows it right now, for starting playback or a download. */
    fun known(episodeId: String): WatchEpisode? {
        val state = store.state.value
        return (fetchedEpisodes.value[episodeId] ?: state.episodes[episodeId])?.let(state::merged)
    }

    override suspend fun search(query: String): WatchSearchResult {
        val feeds = catalog.search(query) ?: return WatchSearchResult.NoConnection(query)
        val state = store.state.value
        val found = feeds.map { WatchMapping.show(it, state.isSubscribed(it.id.toString())) }
        fetchedShows.update { it + found.associateBy(WatchShow::id) }
        return WatchSearchResult.Shows(query, found)
    }

    override fun setSubscribed(showId: String, subscribed: Boolean) {
        if (!subscribed) {
            store.update { it.unsubscribe(showId, clock()) }
            return
        }
        val show = fetchedShows.value[showId] ?: return
        store.update { it.subscribe(show, clock()) }
    }

    override fun setDownloaded(episodeId: String, downloaded: Boolean) {
        if (!downloaded) {
            downloadManager.remove(episodeId)
            return
        }
        known(episodeId)?.let(downloadManager::start)
    }

    override fun playNext(episodeId: String) {
        val episode = known(episodeId) ?: return
        store.update { it.playNext(episode) }
    }

    override fun markPlayed(episodeId: String) {
        val episode = known(episodeId) ?: return
        store.update { it.markPlayed(episode, clock()) }
        // A finished episode no longer needs its stored audio.
        downloadManager.remove(episodeId)
    }

    private suspend fun loadShow(showId: String) {
        if (fetchedShows.value.containsKey(showId)) return
        details(showId)
    }

    /** A show's details from the API, remembering the show itself for the screens. */
    private suspend fun details(showId: String): V2PodcastResponse? {
        val id = showId.toLongOrNull() ?: return null
        val details = catalog.show(id) ?: return null
        details.feed?.let { feed -> fetchedShows.update { it + (showId to WatchMapping.show(feed, subscribed = false)) } }
        return details
    }

    /**
     * A phone in reach answers first with the episodes it already has saved. Otherwise episodes
     * come from the show's own feed, as on the phone. The API's list stands in only when the feed
     * cannot be read, so a show with a broken feed still has something to play.
     */
    private suspend fun loadEpisodes(showId: String) {
        val known = store.state.value.shows.firstOrNull { it.id == showId } ?: fetchedShows.value[showId]
        val mapped = phoneEpisodes(showId, known)?.takeIf { it.isNotEmpty() } ?: ownEpisodes(showId) ?: return
        fetchedEpisodes.update { it + mapped.associateBy(WatchEpisode::id) }
        showEpisodes.update { it + (showId to mapped.map(WatchEpisode::id)) }
    }

    private suspend fun ownEpisodes(showId: String): List<WatchEpisode>? {
        val id = showId.toLongOrNull() ?: return null
        val details = details(showId)
        val show = fetchedShows.value[showId] ?: store.state.value.shows.firstOrNull { it.id == showId }
        val fromFeed = details?.let { feeds.newest(id, it) }?.takeIf { it.isNotEmpty() }
        return fromFeed?.map { WatchMapping.episode(it, showId, show) }
            ?: catalog.episodes(id)?.mapNotNull { WatchMapping.episode(it, show) }
    }

    private fun <T> kept(read: (WatchLibraryState) -> T): StateFlow<T> =
        store.state.map(read).stateIn(scope, SharingStarted.Eagerly, read(store.state.value))
}
