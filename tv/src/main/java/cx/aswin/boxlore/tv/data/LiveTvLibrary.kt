package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvSearchResult
import cx.aswin.boxlore.tv.model.TvShow
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The listener's library on this TV: the saved part in [store], shows and episodes looked up as the
 * screens need them. [scope] must not be the main thread: feeds are parsed in it.
 */
class LiveTvLibrary(
    private val store: TvLibraryStore,
    catalog: TvCatalog,
    feeds: TvFeedReader?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    country: () -> String = { Locale.getDefault().country.lowercase(Locale.ROOT).ifBlank { DEFAULT_COUNTRY } },
) : TvLibrary {
    private val source = TvShowSource(store, catalog, feeds, country)
    private val fetchedEpisodes = MutableStateFlow<Map<String, TvEpisode>>(emptyMap())
    private val showEpisodes = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val episodeLoads = MutableStateFlow<Map<String, EpisodeLoad>>(emptyMap())
    private val inFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // When each show's whole feed was last read for its page, by show ID.
    private val wholeFeedReadAt = ConcurrentHashMap<String, Long>()

    // Feeds read at once for Daily Mix, across every refresh: old TVs and slow networks share them.
    private val reads = Semaphore(PARALLEL_READS)

    override val shows: StateFlow<List<TvShow>> =
        store.kept(scope) { state -> TvLibraryRules.sorted(state.shows, state, ShowSort.Smart).map { it.copy(newEpisodes = TvLibraryRules.newEpisodes(state, it.id)) } }
    override val jumpBackIn: StateFlow<List<TvEpisode>> = store.kept(scope) { it.jumpBackIn }
    override val dailyMix: StateFlow<List<TvEpisode>> = store.kept(scope) { it.dailyMix }
    override val newEpisodes: StateFlow<List<TvEpisode>> = store.kept(scope) { it.newEpisodes }
    override val upNext: StateFlow<List<TvEpisode>> = store.kept(scope) { it.upNext }
    override val liked: StateFlow<List<TvEpisode>> = store.kept(scope) { it.likedEpisodes }
    override val history: StateFlow<List<TvEpisode>> = store.kept(scope) { it.historyEpisodes }
    override val trending: StateFlow<List<TvShow>> = chart(ALL_GENRES).stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // At start, and whenever the followed shows change (here or from the account), shows not read lately are read.
        scope.launch { store.state.map { state -> state.shows.map(TvShow::id) }.distinctUntilChanged().collect { refreshFollowed() } }
    }

    override fun show(showId: String): Flow<TvShow?> =
        combine(source.shows, store.state) { described, state ->
            state.shows.firstOrNull { it.id == showId } ?: described[showId]?.copy(subscribed = false)
        }.distinctUntilChanged().onStart {
            if (source.named(showId) == null) scope.launch { source.details(showId) }
        }

    override fun episodes(showId: String): Flow<ShowEpisodes> =
        combine(showEpisodes, fetchedEpisodes, episodeLoads, source.unknownShows, store.state) { byShow, fetched, loads, unknown, state ->
            val listed = byShow[showId].orEmpty().mapNotNull(fetched::get).map(state::shown)
            TvLibraryRules.state(loads[showId], listed, showId in unknown)
        }.distinctUntilChanged().onStart { scope.launch { loadEpisodes(showId) } }
            // A whole feed can list thousands of episodes: matched with the library off the main thread.
            .flowOn(Dispatchers.Default)

    override fun episode(episodeId: String): Flow<TvEpisode?> =
        combine(fetchedEpisodes, store.state) { fetched, state -> (fetched[episodeId] ?: state.episodes[episodeId])?.let(state::shown) }.distinctUntilChanged()

    /** A chart with each show marked followed or not, loaded the first time it is asked for. */
    override fun chart(genre: String): Flow<List<TvShow>> =
        combine(source.charts, store.state) { charts, state -> charts[genre].orEmpty().map { it.copy(subscribed = state.isSubscribed(it.id)) } }
            .distinctUntilChanged()
            .onStart { if (source.charts.value[genre] == null) scope.launch { source.loadChart(genre.ifEmpty { null }) } }

    override fun sortedShows(sort: ShowSort): Flow<List<TvShow>> = store.state.map { TvLibraryRules.sorted(it.shows, it, sort) }.distinctUntilChanged()

    override suspend fun search(query: String): TvSearchResult {
        if (source.charts.value[ALL_GENRES] == null) scope.launch { source.loadChart(null) }
        val found = source.search(query) ?: return TvSearchResult.NoConnection(query)
        return TvSearchResult.Found(query, found, emptyList())
    }

    /** A show's name and artwork from the API, for a show followed elsewhere; null when that failed. */
    suspend fun lookUpShow(showId: String): TvShow? = source.details(showId)?.feed?.let { TvMapping.show(it, subscribed = true) }

    /** The episode as the TV knows it right now, for starting playback. */
    fun known(episodeId: String): TvEpisode? {
        val state = store.state.value
        return (fetchedEpisodes.value[episodeId] ?: state.episodes[episodeId])?.let(state::shown)
    }

    override fun setSubscribed(showId: String, subscribed: Boolean) {
        if (!subscribed) {
            store.state.value.shows.firstOrNull { it.id == showId }?.let { source.remember(it.copy(subscribed = false)) }
            store.update { it.unsubscribe(showId, clock()) }
            return
        }
        val show = source.named(showId) ?: return
        store.update { it.subscribe(show, clock()) }
        // Its newest episode joins Daily Mix straight away.
        scope.launch { loadEpisodes(showId) }
    }

    override fun setLiked(episodeId: String, liked: Boolean) {
        val episode = known(episodeId) ?: return
        store.update { it.setLiked(episode, liked, clock()) }
    }

    override fun addToUpNext(episodeId: String) {
        val episode = known(episodeId) ?: return
        store.update { it.addToUpNext(episode) }
    }

    override fun markPlayed(episodeId: String) {
        val episode = known(episodeId) ?: return
        store.update { it.markPlayed(episode, clock()) }
    }

    /**
     * Reads the newest episode of every followed show whose feed was not read lately, a few at a
     * time, for Daily Mix. Only the newest is kept, not the whole list.
     */
    private fun refreshFollowed() {
        val now = clock()
        val state = store.state.value
        state.shows.filter { TvLibraryRules.wantsRefresh(state.latestReadAt[it.id], now) }.forEach { show ->
            scope.launch {
                if (inFlight.add(show.id)) {
                    try {
                        reads.withPermit {
                            val newest = source.episodes(show.id, NEWEST_ONLY)?.episodes?.maxByOrNull { it.releasedAtMillis ?: 0L }
                            if (newest != null) store.update { it.newest(newest, clock()) }
                        }
                    } finally {
                        inFlight.remove(show.id)
                    }
                }
            }
        }
    }

    /**
     * Reads a show's episodes for its page: the newest at once, then, for a feed with more, the
     * whole feed (at most every [TvLibraryRules.WHOLE_FEED_AGAIN_MS] per show) while the page
     * shows the newest. A list once on screen stays when a later read fails; with nothing to show,
     * the page says the episodes could not be loaded.
     */
    private suspend fun loadEpisodes(showId: String) {
        if (!inFlight.add(showId)) return
        fun keep(read: TvShowSource.Read) {
            fetchedEpisodes.update { it + read.episodes.associateBy(TvEpisode::id) }
            showEpisodes.update { byShow ->
                val shown = byShow[showId].orEmpty().mapNotNull(fetchedEpisodes.value::get)
                val listed = if (read.fromFeed) TvLibraryRules.replaced(shown, read.episodes) else read.episodes
                byShow + (showId to listed.map(TvEpisode::id))
            }
        }
        try {
            episodeLoads.update { if (it[showId] == EpisodeLoad.Loaded) it else it + (showId to EpisodeLoad.Loading) }
            val read = source.episodes(showId, PAGE_EPISODES)
            if (read == null) {
                if (showEpisodes.value[showId].isNullOrEmpty()) episodeLoads.update { it + (showId to EpisodeLoad.Failed) }
                return
            }
            keep(read)
            read.episodes.maxByOrNull { it.releasedAtMillis ?: 0L }?.let { newest -> store.update { it.newest(newest, clock()) } }
            if (TvLibraryRules.wantsWholeFeed(read.fromFeed, read.episodes.size, PAGE_EPISODES, wholeFeedReadAt[showId], clock())) {
                episodeLoads.update { it + (showId to EpisodeLoad.LoadingMore) }
                source.episodes(showId, WHOLE_FEED)?.takeIf { it.fromFeed }?.let { whole ->
                    keep(whole)
                    wholeFeedReadAt[showId] = clock()
                }
            }
            episodeLoads.update { it + (showId to EpisodeLoad.Loaded) }
        } finally {
            inFlight.remove(showId)
        }
    }

    private companion object {
        const val DEFAULT_COUNTRY = "us"
        const val PARALLEL_READS = 3
        const val NEWEST_ONLY = 3
        const val PAGE_EPISODES = 50

        // Every episode the feed lists; the feed reader still stops at its size limit.
        const val WHOLE_FEED = Int.MAX_VALUE
        const val ALL_GENRES = ""
    }
}

/** One part of the saved library, kept up to date for as long as [scope] lives. */
private fun <T> TvLibraryStore.kept(scope: CoroutineScope, read: (TvLibraryState) -> T): StateFlow<T> =
    state.map(read).distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, read(state.value))
