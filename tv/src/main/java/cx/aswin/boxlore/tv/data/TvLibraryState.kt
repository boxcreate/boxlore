package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow
import kotlinx.serialization.Serializable

/**
 * Everything the TV keeps about the listener's library, as one value. Every change is a pure
 * function that returns the next state, so the rules can be tested without a TV.
 *
 * [episodes] holds only episodes the TV has a reason to remember: started, queued, liked, in the
 * history, or the newest of a followed show.
 */
@Serializable
data class TvLibraryState(
    /** Followed shows, the latest follow first. */
    val shows: List<TvShow> = emptyList(),
    val episodes: Map<String, TvEpisode> = emptyMap(),
    /** Episode IDs that play after the current one, first to last. */
    val queue: List<String> = emptyList(),
    /** Episode IDs the listener has started and not finished, most recent first. */
    val recent: List<String> = emptyList(),
    /** Where each started episode stopped, in milliseconds. */
    val positions: Map<String, Long> = emptyMap(),
    /** Episode IDs the listener has finished or marked played. */
    val played: Set<String> = emptySet(),
    /** Episode IDs listened to, newest first. */
    val history: List<String> = emptyList(),
    /** Liked episode IDs, the latest like first. */
    val liked: List<String> = emptyList(),
    /** The newest episode of each followed show, by show ID: what Daily Mix picks from. */
    val latest: Map<String, String> = emptyMap(),
    /** When each followed show's feed was last read, by show ID. */
    val latestReadAt: Map<String, Long> = emptyMap(),
    /** How many episodes of each show the listener has played, by show ID: the Most listened order. */
    val listens: Map<String, Int> = emptyMap(),
    /** When the listener last played each episode here or elsewhere, by episode ID: listening merges by it. */
    val playedAt: Map<String, Long> = emptyMap(),
    /** When each episode was last liked or unliked, by episode ID: likes merge by it. */
    val likedAt: Map<String, Long> = emptyMap(),
    /** Each started episode's exact length, in milliseconds, once the player knew it. */
    val durations: Map<String, Long> = emptyMap(),
    /** Whether this TV is signed in to the listener's account, so its changes go to the cloud. */
    val cloudLinked: Boolean = false,
    /** Grows with every follow, unfollow and listening change made on this TV while signed in. */
    val revision: Long = 0L,
    /** Follows and unfollows the cloud has not confirmed yet, by show ID. */
    val pendingShows: Map<String, PendingShow> = emptyMap(),
    /** Episodes whose listening the cloud has not confirmed yet, with the revision of the last change. */
    val pendingEpisodes: Map<String, Long> = emptyMap(),
    /** The newest revision the cloud has confirmed. */
    val cloudAcked: Long = 0L,
    /** The cloud's own time at the last pull; the next pull asks for what changed since. 0 before the first. */
    val cloudSince: Long = 0L,
) {
    /** Episodes started and not finished, most recent first. */
    val jumpBackIn: List<TvEpisode>
        get() = recent.mapNotNull(episodes::get).filter { it.id !in played }.map(::shown)

    val upNext: List<TvEpisode>
        get() = queue.mapNotNull(episodes::get).map(::shown)

    val likedEpisodes: List<TvEpisode>
        get() = liked.mapNotNull(episodes::get).map(::shown)

    val historyEpisodes: List<TvEpisode>
        get() = history.mapNotNull(episodes::get).map(::shown)

    /**
     * Daily Mix, as on the phone: one episode per followed show, the newest one the listener has
     * not finished, newest first.
     */
    val dailyMix: List<TvEpisode>
        get() =
            shows.mapNotNull { latest[it.id] }
                .filterNot { it in played }
                .mapNotNull(episodes::get)
                .sortedByDescending { it.releasedAtMillis ?: 0L }
                .take(MIX_LIMIT)
                .map(::shown)

    /** New episodes, as on the phone: each followed show's newest episode not started or finished yet, newest first. */
    val newEpisodes: List<TvEpisode>
        get() =
            shows.mapNotNull { latest[it.id] }
                .filterNot { it in played || it in recent }
                .mapNotNull(episodes::get)
                .sortedByDescending { it.releasedAtMillis ?: 0L }
                .map(::shown)

    fun isSubscribed(showId: String): Boolean = shows.any { it.id == showId }

    /** [episode] as the listener left it: time left, played and liked. */
    fun shown(episode: TvEpisode): TvEpisode {
        val saved = episodes[episode.id]
        val played = episode.id in played
        return episode.copy(
            minutesLeft = if (played) null else saved?.minutesLeft ?: episode.minutesLeft,
            played = played,
            liked = episode.id in liked,
        )
    }

    fun subscribe(show: TvShow, nowMs: Long = 0L): TvLibraryState =
        if (isSubscribed(show.id)) this else copy(shows = listOf(show.copy(subscribed = true)) + shows).notingShow(show, subscribed = true, nowMs)

    fun unsubscribe(showId: String, nowMs: Long = 0L): TvLibraryState {
        val show = shows.firstOrNull { it.id == showId } ?: return this
        return copy(shows = shows - show, latest = latest - showId, latestReadAt = latestReadAt - showId).notingShow(show, subscribed = false, nowMs).forgetUnused()
    }

    /** Adds [episode] to the end of Up next; one already queued stays where it is. */
    fun addToUpNext(episode: TvEpisode): TvLibraryState =
        if (episode.id in queue) this else remember(episode).copy(queue = queue + episode.id)

    /** An episode the listener starts by hand no longer waits in Up next. */
    fun dequeue(episodeId: String): TvLibraryState = if (episodeId in queue) copy(queue = queue - episodeId) else this

    /** Takes the first queued episode off Up next, for when the current one ends. */
    fun takeNext(): Pair<TvEpisode?, TvLibraryState> {
        val next = queue.firstOrNull() ?: return null to this
        return episodes[next]?.let(::shown) to copy(queue = queue.drop(1)).forgetUnused()
    }

    /** Records where [episode] stopped. A position at or past the end counts as played. */
    fun progress(episode: TvEpisode, positionMs: Long, durationMs: Long, nowMs: Long = 0L): TvLibraryState {
        if (durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS) return markPlayed(episode, nowMs)
        val position = positionMs.coerceAtLeast(0L)
        val minutes = if (durationMs > 0) wholeMinutes(durationMs) else episode.durationMinutes
        // Until the player knows the length, the episode still has to read as started.
        val left = if (durationMs > 0) wholeMinutes(durationMs - position) else episode.minutesLeft ?: episode.durationMinutes.coerceAtLeast(1)
        return remember(episode.copy(durationMinutes = minutes, minutesLeft = left.coerceAtLeast(1))).copy(
            recent = listOf(episode.id) + recent.filterNot { it == episode.id },
            positions = positions + (episode.id to position),
            played = played - episode.id,
            history = (listOf(episode.id) + history.filterNot { it == episode.id }).take(HISTORY_LIMIT),
            playedAt = playedAt + (episode.id to maxOf(nowMs, playedAt[episode.id] ?: 0L)),
            durations = if (durationMs > 0) durations + (episode.id to durationMs) else durations,
        ).notingEpisode(episode.id)
    }

    fun markPlayed(episode: TvEpisode, nowMs: Long = 0L): TvLibraryState {
        val first = episode.id !in played
        return remember(episode.copy(minutesLeft = null)).copy(
            recent = recent.filterNot { it == episode.id },
            positions = positions - episode.id,
            played = played + episode.id,
            history = (listOf(episode.id) + history.filterNot { it == episode.id }).take(HISTORY_LIMIT),
            listens = if (first) listens + (episode.showId to (listens[episode.showId] ?: 0) + 1) else listens,
            playedAt = playedAt + (episode.id to maxOf(nowMs, playedAt[episode.id] ?: 0L)),
        ).notingEpisode(episode.id).forgetUnused()
    }

    fun setLiked(episode: TvEpisode, liked: Boolean, nowMs: Long = 0L): TvLibraryState {
        val likes = if (liked) listOf(episode.id) + this.liked.filterNot { it == episode.id } else this.liked - episode.id
        return remember(episode).copy(liked = likes, likedAt = likedAt + (episode.id to nowMs)).notingEpisode(episode.id).forgetUnused()
    }

    /** Notes [episode] as the newest of its show, read at [readAtMillis]. Only followed shows are kept. */
    fun newest(episode: TvEpisode, readAtMillis: Long): TvLibraryState {
        if (!isSubscribed(episode.showId)) return this
        return remember(episode).copy(latest = latest + (episode.showId to episode.id), latestReadAt = latestReadAt + (episode.showId to readAtMillis)).forgetUnused()
    }

    /** Keeps [episode], carrying over what is already known about it unless the copy says more. */
    internal fun remember(episode: TvEpisode): TvLibraryState {
        val saved = episodes[episode.id]
        val kept = if (saved == null) episode else episode.copy(minutesLeft = episode.minutesLeft ?: saved.minutesLeft)
        return copy(episodes = episodes + (episode.id to kept.copy(played = false, liked = false)))
    }

    /** Drops episodes the TV has no reason left to remember. */
    internal fun forgetUnused(): TvLibraryState {
        val needed = queue.toSet() + recent + liked + history + latest.values + pendingEpisodes.keys
        val kept = episodes.filterKeys { it in needed }
        return copy(episodes = kept, likedAt = likedAt.filterKeys { it in kept }, durations = durations.filterKeys { it in kept })
    }

    companion object {
        /** The last stretch of an episode (end credits, a sign-off) counts as finished, as on the phone. */
        const val FINISHED_MARGIN_MS = 30_000L
        const val HISTORY_LIMIT = 200
        const val MIX_LIMIT = 15
        private const val MINUTE_MS = 60_000L

        /** Whole minutes, rounded up, so the last seconds still read as one minute left. */
        fun wholeMinutes(ms: Long): Int = ((ms.coerceAtLeast(0L) + MINUTE_MS - 1) / MINUTE_MS).toInt()
    }
}

/** A follow or unfollow made on this TV while signed in, waiting for the cloud to confirm it. */
@Serializable
data class PendingShow(val show: TvShow, val subscribed: Boolean, val atMillis: Long, val revision: Long)

/** Notes a listening change for the cloud; nothing is noted while the TV is signed out. */
internal fun TvLibraryState.notingEpisode(id: String): TvLibraryState =
    if (!cloudLinked) this else (revision + 1).let { next -> copy(revision = next, pendingEpisodes = pendingEpisodes + (id to next)) }

private fun TvLibraryState.notingShow(show: TvShow, subscribed: Boolean, nowMs: Long): TvLibraryState =
    if (!cloudLinked) {
        this
    } else {
        (revision + 1).let { next -> copy(revision = next, pendingShows = pendingShows + (show.id to PendingShow(show.copy(subscribed = subscribed), subscribed, nowMs, next))) }
    }
