package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode

/** Why a download could not start. */
enum class WatchDownloadProblem {
    /** The episode alone is larger than the listener's storage limit, or nothing can be cleared for it. */
    OverLimit,

    /** The watch itself is too full, whatever the limit says. */
    WatchFull,
}

/** Whether a new download may start. */
internal sealed interface StorageRoom {
    /** It may, once the episodes in [evict] are removed, oldest first. */
    data class Available(val evict: List<String>) : StorageRoom

    data class Refused(val problem: WatchDownloadProblem) : StorageRoom
}

/**
 * The rules that keep downloads from filling the watch. Pure: it only says which stored episodes
 * should go; deleting the files is the caller's job.
 */
internal object StoragePolicy {
    private const val MB = 1024L * 1024L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** The limits the listener can pick, in megabytes; the middle one is the default. */
    val LIMIT_CHOICES_MB: List<Int> = listOf(250, 500, 1000, 2000)
    const val DEFAULT_LIMIT_MB = 500

    /** A download left alone this long is removed. */
    const val STALE_AFTER_MS = 30L * DAY_MS

    /** Downloads never take the watch's own free space below this. */
    const val FREE_SPACE_FLOOR_BYTES = 300L * MB

    fun limitBytes(limitMb: Int): Long = limitMb * MB

    /**
     * The stored episodes to remove now: ones untouched for [STALE_AFTER_MS], then the least
     * recently used until the rest fits in [limitBytes]. The episode in the player stays.
     *
     * Finishing an episode removes its download at that moment, not here: a played episode the
     * listener downloads again is kept like any other.
     */
    fun cleanup(state: WatchLibraryState, limitBytes: Long, nowMs: Long, playingId: String?): List<String> {
        val stored = state.episodes.values.filter { it.download == WatchDownload.Done && it.id != playingId }
        val stale = stored.filter { nowMs - touchedAt(it) > STALE_AFTER_MS }.map(WatchEpisode::id)
        val pinned = state.episodes[playingId]?.takeIf { it.download == WatchDownload.Done }?.storedBytes ?: 0L
        return stale + overflow(stored.filterNot { it.id in stale }, limitBytes - pinned)
    }

    /**
     * Whether a download of [bytes] (null when the size is unknown) may start, and what has to be
     * removed to make room for it.
     */
    fun room(
        bytes: Long?,
        state: WatchLibraryState,
        limitBytes: Long,
        freeWatchBytes: Long,
        playingId: String?,
    ): StorageRoom {
        val needed = bytes ?: 0L
        if (freeWatchBytes - needed < FREE_SPACE_FLOOR_BYTES) return StorageRoom.Refused(WatchDownloadProblem.WatchFull)
        if (needed > limitBytes) return StorageRoom.Refused(WatchDownloadProblem.OverLimit)
        val stored = state.episodes.values.filter { it.download == WatchDownload.Done }
        val pinned = stored.firstOrNull { it.id == playingId }?.storedBytes ?: 0L
        val evict = overflow(stored.filterNot { it.id == playingId }, limitBytes - pinned - needed)
        val left = stored.filterNot { it.id in evict }.sumOf(WatchEpisode::storedBytes)
        return if (left + needed > limitBytes) StorageRoom.Refused(WatchDownloadProblem.OverLimit) else StorageRoom.Available(evict)
    }

    /**
     * The same question for a download of unknown size while it arrives. Its [writtenBytes] are
     * already on the watch, so the free space is judged as it stands, and the limit is held with
     * everything else that is stored: without this, such a download could grow to the whole limit
     * on top of what was already there.
     */
    fun roomWhileArriving(
        writtenBytes: Long,
        state: WatchLibraryState,
        limitBytes: Long,
        freeWatchBytes: Long,
        playingId: String?,
    ): StorageRoom {
        if (freeWatchBytes < FREE_SPACE_FLOOR_BYTES) return StorageRoom.Refused(WatchDownloadProblem.WatchFull)
        return room(writtenBytes, state, limitBytes, freeWatchBytes = Long.MAX_VALUE, playingId = playingId)
    }

    /** The least recently used of [stored] to drop so the rest takes no more than [budgetBytes]. */
    private fun overflow(stored: List<WatchEpisode>, budgetBytes: Long): List<String> {
        var total = stored.sumOf(WatchEpisode::storedBytes)
        val dropped = ArrayList<String>()
        for (episode in stored.sortedBy(::touchedAt)) {
            if (total <= budgetBytes) break
            total -= episode.storedBytes
            dropped += episode.id
        }
        return dropped
    }

    /** When the listener last had a use for the stored audio: downloading it or playing it. */
    private fun touchedAt(episode: WatchEpisode): Long = maxOf(episode.storedAtMillis, episode.lastPlayedAtMillis)
}
