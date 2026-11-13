package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.PendingShowChange
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import kotlinx.serialization.Serializable

/**
 * Everything the watch keeps about the listener's library, as one value. Every change is a pure
 * function that returns the next state, so the rules can be tested without a watch.
 *
 * [episodes] holds only episodes the watch has a reason to remember: started, queued or stored.
 */
@Serializable
data class WatchLibraryState(
    val shows: List<WatchShow> = emptyList(),
    val episodes: Map<String, WatchEpisode> = emptyMap(),
    /** Episode IDs that play after the current one, first to last. */
    val queue: List<String> = emptyList(),
    /** Episode IDs the listener has started, most recent first. */
    val recent: List<String> = emptyList(),
    /** Where each started episode stopped, in milliseconds. */
    val positions: Map<String, Long> = emptyMap(),
    /** Episode IDs the listener has finished or marked played. */
    val played: Set<String> = emptySet(),
    /** Names this install of the watch app to the phone; made the first time the link starts. */
    val watchId: String = "",
    /** Grows with every follow, unfollow and listening change made on this watch. */
    val revision: Long = 0L,
    /** Follows and unfollows the phone has not confirmed, by show ID. */
    val pendingShows: Map<String, PendingShowChange> = emptyMap(),
    /** Episodes whose listening the phone has not confirmed, with the revision of the last change. */
    val pendingEpisodes: Map<String, Long> = emptyMap(),
    /**
     * When this watch's own player last saved a place. Listening merged from the phone never moves
     * it, so it says which of the two devices was used last.
     */
    val playedHereAt: Long = 0L,
) {
    val jumpBackIn: List<WatchEpisode>
        get() = recent.mapNotNull(episodes::get).filter { (it.minutesLeft ?: 0) > 0 }

    val upNext: List<WatchEpisode>
        get() = queue.mapNotNull(episodes::get)

    val downloads: List<WatchEpisode>
        get() = episodes.values.filter { it.download != WatchDownload.None }

    /** Space taken by finished downloads, in bytes. */
    val storedBytes: Long
        get() = episodes.values.sumOf(WatchEpisode::storedBytes)

    fun isSubscribed(showId: String): Boolean = shows.any { it.id == showId }

    /** The saved copy of [episode] when the watch has one, so progress and downloads carry over. */
    fun merged(episode: WatchEpisode): WatchEpisode {
        val saved = episodes[episode.id] ?: sameAudio(episode)
        return when {
            saved != null ->
                episode.copy(
                    // The phone may know this episode under another ID; one copy keeps one ID.
                    id = saved.id,
                    minutesLeft = saved.minutesLeft,
                    download = saved.download,
                    storedBytes = saved.storedBytes,
                    storedAtMillis = saved.storedAtMillis,
                    lastPlayedAtMillis = saved.lastPlayedAtMillis,
                    durationMs = saved.durationMs,
                )
            episode.id in played -> episode.copy(minutesLeft = 0)
            else -> episode
        }
    }

    /** A saved episode of the same show with the same audio address as [episode], under another ID. */
    private fun sameAudio(episode: WatchEpisode): WatchEpisode? {
        val audio = episode.audioUrl ?: return null
        return episodes.values.firstOrNull { it.showId == episode.showId && it.audioUrl == audio }
    }

    fun subscribe(show: WatchShow, nowMs: Long = 0L): WatchLibraryState =
        if (isSubscribed(show.id)) {
            this
        } else {
            copy(shows = listOf(show.copy(subscribed = true)) + shows).notingShow(show, subscribed = true, nowMs = nowMs)
        }

    fun unsubscribe(showId: String, nowMs: Long = 0L): WatchLibraryState {
        val show = shows.firstOrNull { it.id == showId } ?: return this
        return copy(shows = shows.filterNot { it.id == showId }).notingShow(show, subscribed = false, nowMs = nowMs)
    }

    /** Puts [episode] first in the queue; an episode already queued moves up instead of repeating. */
    fun playNext(episode: WatchEpisode): WatchLibraryState =
        remember(episode).copy(queue = listOf(episode.id) + queue.filterNot { it == episode.id })

    /** An episode the listener starts by hand no longer waits in Up next. */
    fun dequeue(episodeId: String): WatchLibraryState = if (episodeId in queue) copy(queue = queue - episodeId) else this

    /** Takes the first queued episode off the queue, for when the current one ends. */
    fun takeNext(): Pair<WatchEpisode?, WatchLibraryState> {
        val next = queue.firstOrNull() ?: return null to this
        return episodes[next] to copy(queue = queue.drop(1)).forgetUnused()
    }

    /** Records where [episode] stopped at [nowMs]. A position at or past the end counts as played. */
    fun progress(episode: WatchEpisode, positionMs: Long, durationMs: Long, nowMs: Long = 0L): WatchLibraryState {
        if (durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS) return markPlayed(episode)
        val position = positionMs.coerceAtLeast(0L)
        val minutes = if (durationMs > 0) wholeMinutes(durationMs) else episode.durationMinutes
        // Until the player knows the length, an unknown time left still has to read as "started".
        val left = if (durationMs > 0) wholeMinutes(durationMs - position) else episode.minutesLeft ?: episode.durationMinutes.coerceAtLeast(1)
        val exact = if (durationMs > 0) durationMs else episode.durationMs
        return remember(episode.copy(durationMinutes = minutes, minutesLeft = left, lastPlayedAtMillis = nowMs, durationMs = exact)).copy(
            recent = listOf(episode.id) + recent.filterNot { it == episode.id },
            positions = positions + (episode.id to position),
            played = played - episode.id,
            playedHereAt = maxOf(playedHereAt, nowMs),
        ).notingEpisode(episode.id)
    }

    fun markPlayed(episode: WatchEpisode, nowMs: Long = 0L): WatchLibraryState =
        remember(episode.copy(minutesLeft = 0, lastPlayedAtMillis = maxOf(nowMs, episode.lastPlayedAtMillis))).copy(
            recent = recent.filterNot { it == episode.id },
            positions = positions - episode.id,
            played = played + episode.id,
        ).notingEpisode(episode.id).forgetUnused()

    /** Sets the state of [episode]'s download. [WatchDownload.None] forgets the stored audio. */
    fun download(episode: WatchEpisode, download: WatchDownload): WatchLibraryState {
        val saved = episodes[episode.id] ?: episode
        val kept =
            if (download == WatchDownload.None) {
                saved.copy(download = download, storedBytes = 0L, storedAtMillis = 0L)
            } else {
                saved.copy(download = download)
            }
        return copy(episodes = episodes + (episode.id to kept)).forgetUnused()
    }

    /** Marks [episode]'s audio as fully stored: [bytes] on the watch since [nowMs]. */
    fun stored(episode: WatchEpisode, bytes: Long, nowMs: Long): WatchLibraryState {
        val kept = (episodes[episode.id] ?: episode).copy(download = WatchDownload.Done, storedBytes = bytes, storedAtMillis = nowMs)
        return copy(episodes = episodes + (episode.id to kept))
    }

    /** Keeps [episode], carrying over what is already known about it unless the copy says more. */
    internal fun remember(episode: WatchEpisode): WatchLibraryState {
        val saved = episodes[episode.id]
        val kept =
            if (saved == null) {
                episode
            } else {
                episode.copy(
                    minutesLeft = episode.minutesLeft ?: saved.minutesLeft,
                    // The stored audio is told by the saved copy alone. The player holds a copy from
                    // when it started, and that one must never rewind a download that has moved on.
                    download = saved.download,
                    storedBytes = saved.storedBytes,
                    storedAtMillis = saved.storedAtMillis,
                    lastPlayedAtMillis = maxOf(episode.lastPlayedAtMillis, saved.lastPlayedAtMillis),
                    durationMs = if (episode.durationMs > 0) episode.durationMs else saved.durationMs,
                    catalogKey = episode.catalogKey ?: saved.catalogKey,
                )
            }
        return copy(episodes = episodes + (episode.id to kept))
    }

    /** Drops episodes that are no longer started, queued, stored or waiting to reach the phone. */
    internal fun forgetUnused(): WatchLibraryState {
        val needed = queue.toSet() + recent + pendingEpisodes.keys
        return copy(episodes = episodes.filter { (id, episode) -> id in needed || episode.download != WatchDownload.None })
    }
}

private const val MINUTE_MS = 60_000L

/** Minutes rounded up, so the last seconds still read as one minute. */
private fun wholeMinutes(millis: Long): Int = ((millis + MINUTE_MS - 1) / MINUTE_MS).toInt()

/** Stopping this close to the end counts as finishing the episode. */
private const val FINISHED_MARGIN_MS = 15_000L
