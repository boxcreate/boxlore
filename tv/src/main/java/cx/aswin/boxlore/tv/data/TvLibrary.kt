package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.ShowSort
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvSearchResult
import cx.aswin.boxlore.tv.model.TvShow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the TV screens read about the listener's library. Screens depend on this port only,
 * so the same screens run on sample shows now and on the listener's real library later.
 */
interface TvLibrary {
    /** Subscribed shows in the phone's Your shows order: pinned first, then ranked. */
    val shows: StateFlow<List<TvShow>>

    /** Episodes the listener has started and not finished, most recent first. */
    val jumpBackIn: StateFlow<List<TvEpisode>>

    /** Each followed show's newest episode not started yet, newest first. */
    val newEpisodes: StateFlow<List<TvEpisode>>

    /** Daily Mix in playing order: one episode per show. */
    val dailyMix: StateFlow<List<TvEpisode>>

    val upNext: StateFlow<List<TvEpisode>>

    val liked: StateFlow<List<TvEpisode>>

    /** Episodes played, newest first. */
    val history: StateFlow<List<TvEpisode>>

    /** Shows to start from when the library is empty or nothing matches yet. */
    val trending: StateFlow<List<TvShow>>

    /** Shows popular now in the TV's country in one genre (the API's genre name, such as "true crime"). */
    fun chart(genre: String): Flow<List<TvShow>>

    fun show(showId: String): Flow<TvShow?>

    /** A show's episodes, newest first, and whether they are still loading or could not be loaded. */
    fun episodes(showId: String): Flow<ShowEpisodes>

    fun episode(episodeId: String): Flow<TvEpisode?>

    /** Your shows in one of the phone's sort orders. */
    fun sortedShows(sort: ShowSort): Flow<List<TvShow>>

    suspend fun search(query: String): TvSearchResult

    fun setSubscribed(showId: String, subscribed: Boolean)

    fun setLiked(episodeId: String, liked: Boolean)

    fun addToUpNext(episodeId: String)

    fun markPlayed(episodeId: String)
}

/** A show page's episode list. A list once shown stays on screen even when a later read fails. */
sealed interface ShowEpisodes {
    data object Loading : ShowEpisodes

    /** [more]: the rest of the show's feed is still being read. */
    data class Loaded(val episodes: List<TvEpisode>, val more: Boolean = false) : ShowEpisodes

    data object Failed : ShowEpisodes
}
