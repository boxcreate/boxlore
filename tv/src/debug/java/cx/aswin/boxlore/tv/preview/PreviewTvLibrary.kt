package cx.aswin.boxlore.tv.preview

import cx.aswin.boxlore.tv.data.ShowEpisodes
import cx.aswin.boxlore.tv.data.TvLibrary
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvSearchResult
import cx.aswin.boxlore.tv.model.TvShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The sample library debug builds run on. Changes live only as long as the app does. */
internal class PreviewTvLibrary : TvLibrary {
    private val allShows = MutableStateFlow(PreviewCatalog.shows)
    private val allEpisodes = MutableStateFlow(PreviewCatalog.episodes)
    private val upNextIds = MutableStateFlow(PreviewCatalog.upNextIds)

    private fun pick(ids: List<String>): List<TvEpisode> {
        val byId = allEpisodes.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override val shows: StateFlow<List<TvShow>> = MutableStateFlow(PreviewCatalog.shows.filter { it.subscribed })
    override val jumpBackIn: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.jumpBackInIds))
    override val dailyMix: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.dailyMixIds))
    override val newEpisodes: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.dailyMixIds).filter { it.minutesLeft == null }.take(6))
    override val upNext: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.upNextIds))
    override val liked: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.likedIds))
    override val history: StateFlow<List<TvEpisode>> = MutableStateFlow(pick(PreviewCatalog.historyIds))
    override val trending: StateFlow<List<TvShow>> =
        MutableStateFlow(PreviewCatalog.trendingIds.mapNotNull { id -> PreviewCatalog.shows.firstOrNull { it.id == id } })

    /** Every genre's chart is the sample shows in another order. */
    override fun chart(genre: String): Flow<List<TvShow>> =
        allShows.map { shows -> shows.sortedBy { (it.id + genre).hashCode() } }

    fun episodeNow(episodeId: String): TvEpisode? = allEpisodes.value.firstOrNull { it.id == episodeId }

    override fun show(showId: String): Flow<TvShow?> = allShows.map { list -> list.firstOrNull { it.id == showId } }

    override fun episodes(showId: String): Flow<ShowEpisodes> =
        allEpisodes.map { list -> list.filter { it.showId == showId }.sortedByDescending { it.releasedAtMillis ?: 0L } }.map { ShowEpisodes.Loaded(it) }

    override fun episode(episodeId: String): Flow<TvEpisode?> = allEpisodes.map { list -> list.firstOrNull { it.id == episodeId } }

    override fun sortedShows(sort: ShowSort): Flow<List<TvShow>> =
        shows.map { list ->
            when (sort) {
                ShowSort.AToZ -> list.sortedBy { it.title.lowercase() }
                ShowSort.RecentlyUpdated -> list.sortedByDescending { it.newEpisodes }
                ShowSort.Smart, ShowSort.MostListened, ShowSort.Manual -> list
            }
        }

    override suspend fun search(query: String): TvSearchResult {
        val words = query.trim().lowercase()
        val shows = PreviewCatalog.shows.filter { words.isNotEmpty() && (words in it.title.lowercase() || it.genres.any { g -> words in g.lowercase() }) }
        val episodes = allEpisodes.value.filter { words.isNotEmpty() && words in it.title.lowercase() }
        return TvSearchResult.Found(query, shows, episodes)
    }

    override fun setSubscribed(showId: String, subscribed: Boolean) {
        allShows.update { list -> list.map { if (it.id == showId) it.copy(subscribed = subscribed) else it } }
    }

    override fun setLiked(episodeId: String, liked: Boolean) {
        allEpisodes.update { list -> list.map { if (it.id == episodeId) it.copy(liked = liked) else it } }
    }

    override fun addToUpNext(episodeId: String) {
        upNextIds.update { ids -> if (episodeId in ids) ids else ids + episodeId }
    }

    override fun markPlayed(episodeId: String) {
        allEpisodes.update { list -> list.map { if (it.id == episodeId) it.copy(played = true, minutesLeft = null) else it } }
    }
}

/** A sample player: it moves the state the screens show, without any audio. */
internal class PreviewTvPlayback(private val library: PreviewTvLibrary) : TvPlayback {
    private val state = MutableStateFlow(library.episodeNow(PreviewCatalog.NOW_PLAYING_ID)?.let { nowPlayingOf(it, playing = false) })
    override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

    private fun nowPlayingOf(episode: TvEpisode, playing: Boolean): NowPlaying {
        val left = episode.minutesLeft ?: episode.durationMinutes
        val elapsed = (episode.durationMinutes - left) * SECONDS_PER_MINUTE
        return NowPlaying(episode, playing, episode.progress, elapsed, left)
    }

    override fun play(episodeId: String) {
        val episode = library.episodeNow(episodeId) ?: return
        state.value = nowPlayingOf(episode, playing = true)
    }

    override fun playDailyMix() {
        library.dailyMix.value.firstOrNull()?.let { play(it.id) }
    }

    override fun togglePlayPause() {
        state.update { it?.copy(isPlaying = !it.isPlaying) }
    }

    override fun seekBack() = seekBy(-TvPlayback.SEEK_BACK_SECONDS)

    override fun seekForward() = seekBy(TvPlayback.SEEK_FORWARD_SECONDS)

    private fun seekBy(seconds: Int) {
        state.update { now ->
            now ?: return@update null
            val total = now.episode.durationMinutes * SECONDS_PER_MINUTE
            val elapsed = (now.elapsedSeconds + seconds).coerceIn(0, total)
            now.copy(
                elapsedSeconds = elapsed,
                progress = if (total == 0) 0f else elapsed.toFloat() / total,
                minutesLeft = (total - elapsed) / SECONDS_PER_MINUTE,
            )
        }
    }

    override fun setSpeed(speed: Float) {
        state.update { it?.copy(speed = speed) }
    }

    override fun setSleepTimer(choice: SleepTimerChoice) {
        state.update { it?.copy(sleepTimer = choice) }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}
