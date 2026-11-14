package cx.aswin.boxlore.wear.preview

import cx.aswin.boxlore.wear.data.WatchDownloadProblem
import cx.aswin.boxlore.wear.data.WatchLibrary
import cx.aswin.boxlore.wear.data.WatchPlayback
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchSearchResult
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.ui.WatchFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** An in-memory library over [PreviewCatalog]; taps change it so flows can be tried end to end. */
internal class PreviewWatchLibrary : WatchLibrary {
    private val allShows = MutableStateFlow(PreviewCatalog.shows)
    private val allEpisodes = MutableStateFlow(PreviewCatalog.episodes)
    private val queue = MutableStateFlow(pick(PreviewCatalog.upNextIds))

    private val subscribedShows = MutableStateFlow(PreviewCatalog.shows.filter { it.subscribed })
    override val shows: StateFlow<List<WatchShow>> = subscribedShows.asStateFlow()
    override val jumpBackIn: StateFlow<List<WatchEpisode>> = MutableStateFlow(pick(PreviewCatalog.jumpBackInIds))
    override val newEpisodes: StateFlow<List<WatchEpisode>> = MutableStateFlow(pick(PreviewCatalog.newEpisodeIds))

    /** The sample library stands for a watch whose phone has linked, so every Home tile shows. */
    override val phoneLinked: StateFlow<Boolean> = MutableStateFlow(true)
    override val upNext: StateFlow<List<WatchEpisode>> = queue.asStateFlow()
    private val stored = MutableStateFlow(storedEpisodes(PreviewCatalog.episodes))
    override val downloads: StateFlow<List<WatchEpisode>> = stored.asStateFlow()
    override val storedBytes: StateFlow<Long> = MutableStateFlow(SAMPLE_STORED_BYTES)
    override val downloadProblems: Flow<WatchDownloadProblem> = emptyFlow()

    override fun show(showId: String): Flow<WatchShow?> = allShows.map { list -> list.firstOrNull { it.id == showId } }

    override fun episodes(showId: String): Flow<List<WatchEpisode>> =
        allEpisodes.map { list -> list.filter { it.showId == showId } }

    override fun episode(episodeId: String): Flow<WatchEpisode?> =
        allEpisodes.map { list -> list.firstOrNull { it.id == episodeId } }

    fun episodeNow(episodeId: String): WatchEpisode? = allEpisodes.value.firstOrNull { it.id == episodeId }

    override suspend fun search(query: String): WatchSearchResult {
        delay(SEARCH_DELAY_MS)
        if (query.trim().equals(OFFLINE_QUERY, ignoreCase = true)) return WatchSearchResult.NoConnection(query)
        val pool = PreviewCatalog.searchPool.mapNotNull { id -> allShows.value.firstOrNull { it.id == id } }
        return WatchSearchResult.Shows(query, pool)
    }

    override fun setSubscribed(showId: String, subscribed: Boolean) {
        allShows.update { list -> list.map { if (it.id == showId) it.copy(subscribed = subscribed) else it } }
        subscribedShows.value = allShows.value.filter { it.subscribed }
    }

    override fun setDownloaded(episodeId: String, downloaded: Boolean) {
        val state = if (downloaded) WatchDownload.Done else WatchDownload.None
        allEpisodes.update { list -> list.map { if (it.id == episodeId) it.copy(download = state) else it } }
        stored.value = storedEpisodes(allEpisodes.value)
    }

    override fun playNext(episodeId: String) {
        val episode = episodeNow(episodeId) ?: return
        queue.update { list -> listOf(episode) + list.filterNot { it.id == episodeId } }
    }

    override fun markPlayed(episodeId: String) {
        allEpisodes.update { list -> list.map { if (it.id == episodeId) it.copy(minutesLeft = 0) else it } }
    }

    private fun pick(ids: List<String>): List<WatchEpisode> =
        ids.mapNotNull { id -> PreviewCatalog.episodes.firstOrNull { it.id == id } }

    private fun storedEpisodes(all: List<WatchEpisode>): List<WatchEpisode> =
        all.filter { it.download != WatchDownload.None }

    private companion object {
        const val SEARCH_DELAY_MS = 600L
        const val SAMPLE_STORED_BYTES = 212L * 1024L * 1024L

        /** Searching for this word shows the no-connection screen. */
        const val OFFLINE_QUERY = "offline"
    }
}

/** Playback that only keeps state: nothing is heard, but every control answers. */
internal class PreviewWatchPlayback(private val library: PreviewWatchLibrary) : WatchPlayback {
    private val state = MutableStateFlow(initial())

    override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()
    override val hasDailyMix: Boolean = true
    override val canSwitchPlace: Boolean = true

    override fun play(episodeId: String) {
        val episode = library.episodeNow(episodeId) ?: return
        val left = episode.minutesLeft ?: episode.durationMinutes
        state.value =
            NowPlaying(
                episode = episode,
                isPlaying = true,
                place = PlaybackPlace.Watch,
                progress = 1f - left.toFloat() / episode.durationMinutes.coerceAtLeast(1),
                minutesLeft = left,
                upNextCount = library.upNext.value.size,
            )
    }

    override fun playDailyMix() {
        library.newEpisodes.value.firstOrNull()?.let { play(it.id) }
    }

    override fun togglePlayPause() = state.update { it?.copy(isPlaying = !it.isPlaying) }

    override fun pause() = state.update { it?.copy(isPlaying = false) }

    override fun seekBack() = seek { it.progress - it.seekBackSeconds / seconds(it) }

    override fun seekForward() = seek { it.progress + it.seekForwardSeconds / seconds(it) }

    override fun setSpeed(speed: Float) = state.update { it?.copy(speed = speed) }

    override fun setSleepTimer(choice: SleepTimerChoice) = state.update { it?.copy(sleepTimer = choice) }

    override fun switchPlace() =
        state.update { now ->
            now?.copy(place = if (now.place == PlaybackPlace.Phone) PlaybackPlace.Watch else PlaybackPlace.Phone)
        }

    private fun seek(target: (NowPlaying) -> Float) =
        state.update { now ->
            now?.let {
                val progress = target(it).coerceIn(0f, 1f)
                it.copy(progress = progress, minutesLeft = WatchFormat.minutesLeft(it.episode.durationMinutes, progress))
            }
        }

    private fun seconds(now: NowPlaying): Float = (now.episode.durationMinutes * 60).coerceAtLeast(1).toFloat()

    private fun initial(): NowPlaying? =
        library.episodeNow(PreviewCatalog.NOW_PLAYING_ID)?.let { episode ->
            NowPlaying(
                episode = episode,
                isPlaying = true,
                place = PlaybackPlace.Phone,
                progress = 0.36f,
                minutesLeft = 26,
                upNextCount = library.upNext.value.size,
            )
        }
}
