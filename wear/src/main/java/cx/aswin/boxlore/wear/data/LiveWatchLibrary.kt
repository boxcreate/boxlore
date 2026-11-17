package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.core.network.model.V2PodcastResponse
import cx.aswin.boxlore.wear.model.ShowEpisodes
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import java.util.concurrent.ConcurrentHashMap
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
    /**
     * A show's episodes as the phone has them saved; null when there is no phone to ask. With
     * `fresh` the phone first reads the show's feed when its own rules allow.
     */
    private val phoneEpisodes: suspend (showId: String, show: WatchShow?, fresh: Boolean) -> PhoneEpisodeList? = { _, _, _ -> null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** Episodes the phone names outside any show's list, such as its queue, so their pages open here too. */
    namedByPhone: Flow<List<WatchEpisode>> = emptyFlow(),
) : WatchLibrary {
    private val fetchedShows = MutableStateFlow<Map<String, WatchShow>>(emptyMap())
    private val fetchedEpisodes = MutableStateFlow<Map<String, WatchEpisode>>(emptyMap())
    private val showEpisodes = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val episodeLoads = MutableStateFlow<Map<String, EpisodeLoad>>(emptyMap())

    /** Shows the API could not name and the listener does not follow: their page cannot be drawn. */
    private val unknownShows = MutableStateFlow<Set<String>>(emptySet())

    /** When the watch last went past the phone's old saved list for a show, so it does so once in a while, not on every visit. */
    private val fresherTries = ConcurrentHashMap<String, Long>()

    init {
        scope.launch { namedByPhone.collect { named -> fetchedEpisodes.update { it + named.associateBy(WatchEpisode::id) } } }
    }

    override val shows: StateFlow<List<WatchShow>> = store.kept(scope) { it.shows }
    override val jumpBackIn: StateFlow<List<WatchEpisode>> = store.kept(scope) { it.jumpBackIn }
    override val upNext: StateFlow<List<WatchEpisode>> = store.kept(scope) { it.upNext }
    override val downloads: StateFlow<List<WatchEpisode>> = store.kept(scope) { it.downloads }
    override val storedBytes: StateFlow<Long> = store.kept(scope) { it.storedBytes }
    override val downloadProblems: Flow<WatchDownloadProblem> = downloadManager.problems

    /** Both lists come from a linked phone; a watch on its own cannot know what is new. */
    override val newEpisodes: StateFlow<List<WatchEpisode>> = store.kept(scope) { it.newEpisodes }
    override val dailyMix: StateFlow<List<WatchEpisode>> = store.kept(scope) { it.dailyMix }
    override val phoneLinked: StateFlow<Boolean> = store.kept(scope) { it.phoneLinked }

    override fun show(showId: String): Flow<WatchShow?> =
        combine(fetchedShows, store.state) { fetched, state ->
            (state.shows.firstOrNull { it.id == showId } ?: fetched[showId])?.copy(subscribed = state.isSubscribed(showId))
        }.onStart { scope.launch { if (!fetchedShows.value.containsKey(showId)) details(showId) } }

    override fun episodes(showId: String): Flow<ShowEpisodes> =
        combine(showEpisodes, fetchedEpisodes, store.state, episodeLoads, unknownShows) { byShow, fetched, state, loads, unknown ->
            val episodes = byShow[showId].orEmpty().mapNotNull(fetched::get).map(state::merged)
            ShowEpisodeRules.state(loads[showId], episodes, showUnknown = showId in unknown && state.shows.none { it.id == showId })
        }.onStart { scope.launch { loadEpisodes(showId) } }

    override fun reloadShow(showId: String) {
        unknownShows.update { it - showId }
        episodeLoads.update { it - showId }
        scope.launch { if (!fetchedShows.value.containsKey(showId)) details(showId) }
        scope.launch { loadEpisodes(showId) }
    }

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
            // Keep the show as last followed: a show the API cannot describe, such as one added by
            // its RSS address, would otherwise leave its open page with nothing to draw or follow again.
            store.state.value.shows.firstOrNull { it.id == showId }?.let { followed ->
                fetchedShows.update { if (it.containsKey(showId)) it else it + (showId to followed.copy(subscribed = false)) }
            }
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

    /**
     * A show's details from the API, remembering the show itself for the screens. A show neither
     * followed nor described is noted, so its page says it could not load instead of staying blank.
     */
    private suspend fun details(showId: String): V2PodcastResponse? {
        val details = showId.toLongOrNull()?.let { catalog.show(it) }
        val feed = details?.feed
        if (feed != null) {
            fetchedShows.update { it + (showId to WatchMapping.show(feed, subscribed = false)) }
            unknownShows.update { it - showId }
        } else if (namedShow(showId, store.state.value, fetchedShows.value) == null) {
            unknownShows.update { it + showId }
        }
        return details
    }

    /**
     * A phone in reach answers first with the episodes it already has saved. When the phone read
     * the show's feed long ago, the watch asks it for a fresh list, and reads the feed itself when
     * the phone cannot give one. With no phone, episodes come from the show's own feed, as on the
     * phone. The API's list stands in only when the feed cannot be read, so a show with a broken
     * feed still has something to play. When nothing comes back the page says so.
     */
    private suspend fun loadEpisodes(showId: String) {
        episodeLoads.update { if (it[showId] == EpisodeLoad.Loaded) it else it + (showId to EpisodeLoad.Loading) }
        val known = namedShow(showId, store.state.value, fetchedShows.value)
        val saved = phoneEpisodes(showId, known, false)?.takeIf { it.episodes.isNotEmpty() }
        if (saved != null) {
            // A recent phone list is as good as a feed read; an old one only adds what is newer than the page.
            publish(showId, saved.episodes, current = ShowEpisodeRules.isFresh(saved.readAtMillis, clock()))
            if (!ShowEpisodeRules.wantsFresher(saved.readAtMillis, fresherTries[showId], clock())) return
            fresherTries[showId] = clock()
            val fresh = phoneEpisodes(showId, known, true)?.takeIf { it.episodes.isNotEmpty() && ShowEpisodeRules.isFresh(it.readAtMillis, clock()) }
            if (fresh != null) return publish(showId, fresh.episodes, current = true)
        }
        val shownBefore = !showEpisodes.value[showId].isNullOrEmpty()
        val own = ownEpisodes(showId, apiAllowed = saved == null && !shownBefore)
        when {
            own != null -> publish(showId, own.episodes, current = own.fromFeed)
            saved == null && !shownBefore -> episodeLoads.update { it + (showId to EpisodeLoad.Failed) }
        }
    }

    /**
     * Puts [episodes] on the show's page. A [current] list (the feed as just read) replaces what
     * the page lists from its oldest episode on; any other list only adds what is newer.
     */
    private fun publish(showId: String, episodes: List<WatchEpisode>, current: Boolean) {
        fetchedEpisodes.update { it + episodes.associateBy(WatchEpisode::id) }
        showEpisodes.update { byShow ->
            val shown = byShow[showId].orEmpty().mapNotNull(fetchedEpisodes.value::get)
            val listed = if (current) ShowEpisodeRules.replaced(shown, episodes) else ShowEpisodeRules.added(shown, episodes)
            byShow + (showId to listed.map(WatchEpisode::id))
        }
        episodeLoads.update { it + (showId to EpisodeLoad.Loaded) }
    }

    /**
     * The show's episodes as the watch reads them itself: from the feed, else (only when
     * [apiAllowed]) from the API's list, which carries other IDs and addresses for the same episodes.
     */
    private suspend fun ownEpisodes(showId: String, apiAllowed: Boolean): OwnEpisodes? {
        val id = showId.toLongOrNull() ?: return null
        val details = details(showId)
        val show = fetchedShows.value[showId] ?: store.state.value.shows.firstOrNull { it.id == showId }
        val fromFeed = details?.let { feeds.newest(id, it) }?.takeIf { it.isNotEmpty() }
        if (fromFeed != null) return OwnEpisodes(fromFeed.map { WatchMapping.episode(it, showId, show) }, fromFeed = true)
        if (!apiAllowed) return null
        return catalog.episodes(id)?.mapNotNull { WatchMapping.episode(it, show) }?.let { OwnEpisodes(it, fromFeed = false) }
    }
}

/** One part of the saved library, kept up to date for as long as [scope] lives. */
private fun <T> WatchLibraryStore.kept(scope: CoroutineScope, read: (WatchLibraryState) -> T): StateFlow<T> =
    state.map(read).stateIn(scope, SharingStarted.Eagerly, read(state.value))

/** The show as the listener follows it, else as the API last described it. */
private fun namedShow(showId: String, state: WatchLibraryState, fetched: Map<String, WatchShow>): WatchShow? =
    state.shows.firstOrNull { it.id == showId } ?: fetched[showId]
