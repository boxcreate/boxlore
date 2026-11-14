package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Everything the watch screens read about the listener's library. Screens depend on this port
 * only, so the same screens work whether the data comes from the phone, the cloud or a preview.
 */
interface WatchLibrary {
    /** Subscribed shows, most useful first: new episodes and recent listening lead. */
    val shows: StateFlow<List<WatchShow>>

    /** Episodes the listener has started and not finished. */
    val jumpBackIn: StateFlow<List<WatchEpisode>>

    val newEpisodes: StateFlow<List<WatchEpisode>>

    /** The phone's Daily Mix in playing order; empty until a phone has sent one. */
    val dailyMix: StateFlow<List<WatchEpisode>>
        get() = NoEpisodes

    /** Whether a phone has ever linked. New episodes only come from one, so Home hides the tile until then. */
    val phoneLinked: StateFlow<Boolean>
        get() = NeverLinked

    /** The queue that plays after the current episode. */
    val upNext: StateFlow<List<WatchEpisode>>

    /** Episodes stored on this watch, including ones still downloading. */
    val downloads: StateFlow<List<WatchEpisode>>

    /** Space the finished downloads take, in bytes. */
    val storedBytes: StateFlow<Long>

    /** A download that could not start, for the screen to explain. */
    val downloadProblems: Flow<WatchDownloadProblem>

    fun show(showId: String): Flow<WatchShow?>

    fun episodes(showId: String): Flow<List<WatchEpisode>>

    fun episode(episodeId: String): Flow<WatchEpisode?>

    suspend fun search(query: String): WatchSearchResult

    fun setSubscribed(showId: String, subscribed: Boolean)

    fun setDownloaded(episodeId: String, downloaded: Boolean)

    fun playNext(episodeId: String)

    fun markPlayed(episodeId: String)
}

/** A library with nothing in it: what a watch shows before it has heard from a phone or account. */
class EmptyWatchLibrary : WatchLibrary {
    override val shows: StateFlow<List<WatchShow>> = MutableStateFlow(emptyList())
    override val jumpBackIn: StateFlow<List<WatchEpisode>> = MutableStateFlow(emptyList())
    override val newEpisodes: StateFlow<List<WatchEpisode>> = MutableStateFlow(emptyList())
    override val upNext: StateFlow<List<WatchEpisode>> = MutableStateFlow(emptyList())
    override val downloads: StateFlow<List<WatchEpisode>> = MutableStateFlow(emptyList())
    override val storedBytes: StateFlow<Long> = MutableStateFlow(0L)
    override val downloadProblems: Flow<WatchDownloadProblem> = emptyFlow()

    override fun show(showId: String): Flow<WatchShow?> = flowOf(null)

    override fun episodes(showId: String): Flow<List<WatchEpisode>> = flowOf(emptyList())

    override fun episode(episodeId: String): Flow<WatchEpisode?> = flowOf(null)

    override suspend fun search(query: String): WatchSearchResult = WatchSearchResult.NoConnection(query)

    override fun setSubscribed(showId: String, subscribed: Boolean) = Unit

    override fun setDownloaded(episodeId: String, downloaded: Boolean) = Unit

    override fun playNext(episodeId: String) = Unit

    override fun markPlayed(episodeId: String) = Unit
}

private val NoEpisodes: StateFlow<List<WatchEpisode>> = MutableStateFlow(emptyList())
private val NeverLinked: StateFlow<Boolean> = MutableStateFlow(false)
