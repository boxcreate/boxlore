package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.wearlink.PhoneLibrary
import cx.aswin.boxlore.core.wearlink.WatchChanges
import cx.aswin.boxlore.core.wearlink.WatchHistoryChange
import cx.aswin.boxlore.core.wearlink.WatchStoredEpisode
import cx.aswin.boxlore.core.wearlink.WatchSubscriptionChange
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.core.wearlink.WearLinkShow
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.model.PendingShowChange
import cx.aswin.boxlore.wear.model.WatchDownload
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow

/**
 * How the watch's library and the phone's meet. The phone is where the listener's library lives,
 * so its list of shows replaces the watch's, and its listening wins wherever it is newer. What
 * the listener did on the watch waits in the library until the phone confirms it, and sits on top
 * of the phone's answer until then, so nothing done at the gym is lost or flickers away.
 */
internal object WatchLinkRules {
    /** The newest listening changes sent at once; older ones follow once these are confirmed. */
    const val MAX_HISTORY_CHANGES = 60

    /** Started episodes the watch remembers; the phone sends about this many too. */
    const val MAX_RECENT = 30

    private const val MINUTE_MS = 60_000L

    /** What the phone still has to hear from this watch. */
    fun changes(state: WatchLibraryState, maxHistory: Int = MAX_HISTORY_CHANGES): WatchChanges {
        val waiting = state.pendingEpisodes.entries.sortedBy { it.value }
        val sent = waiting.take(maxHistory)
        // With more waiting than fits, this message covers only up to the last row it carries.
        val covered = if (waiting.size > sent.size) sent.last().value else state.revision
        return WatchChanges(
            watchId = state.watchId,
            revision = covered,
            subscriptions = state.pendingShows.values.filter { it.revision <= covered }.sortedBy { it.revision }.map { it.wire() },
            history = sent.mapNotNull { (id, revision) -> historyRow(state, id)?.let { WatchHistoryChange(revision, it) } },
            storedBytes = state.storedBytes,
            downloads = state.episodes.values.count { it.download == WatchDownload.Done },
            stored = state.downloads.map { WatchStoredEpisode(it.id, it.audioUrl, done = it.download == WatchDownload.Done) },
        )
    }

    /**
     * The library after hearing from the phone. [playingId] is the episode in the watch's player,
     * which the phone never moves.
     */
    fun withPhone(state: WatchLibraryState, library: PhoneLibrary, playingId: String?): WatchLibraryState {
        val acked = library.acked[state.watchId] ?: 0L
        var next =
            state.copy(
                pendingShows = state.pendingShows.filterValues { it.revision > acked },
                pendingEpisodes = state.pendingEpisodes.filterValues { it > acked },
            )
        next = next.copy(shows = shows(next, library))
        for (row in library.history) next = withRow(next, row, playingId)
        next = withLists(next, library)
        val recent = next.recent.sortedByDescending { next.episodes[it]?.lastPlayedAtMillis ?: 0L }.take(MAX_RECENT)
        return next.copy(recent = recent).forgetUnused()
    }

    /** The phone's New episodes and Daily Mix, each kept as the watch's own copy where it has one. */
    private fun withLists(state: WatchLibraryState, library: PhoneLibrary): WatchLibraryState {
        val fresh = library.newEpisodes.map { state.merged(watchEpisode(it)) }
        val mix = library.dailyMix.map { state.merged(watchEpisode(it)) }
        val kept = (fresh + mix).fold(state) { known, episode -> known.remember(episode) }
        return kept.copy(phoneLinked = true, newFromPhone = fresh.map { it.id }, mixFromPhone = mix.map { it.id })
    }

    /** The phone's shows, with the watch's unconfirmed follows and unfollows applied on top. */
    private fun shows(state: WatchLibraryState, library: PhoneLibrary): List<WatchShow> {
        val mine = state.shows.associateBy(WatchShow::id)
        val fromPhone = library.shows.map { it.toWatchShow(mine[it.podcastId]) }
        val phoneIds = fromPhone.mapTo(HashSet(), WatchShow::id)
        // A cut list says nothing about the shows it leaves out, so those stay as they were.
        val base = if (library.showsTruncated) fromPhone + state.shows.filterNot { it.id in phoneIds } else fromPhone
        return state.pendingShows.values.sortedBy { it.revision }.fold(base) { shows, change ->
            when {
                !change.subscribed -> shows.filterNot { it.id == change.show.id }
                shows.any { it.id == change.show.id } -> shows
                else -> listOf(change.show.copy(subscribed = true)) + shows
            }
        }
    }

    private fun withRow(state: WatchLibraryState, row: ListeningHistorySyncDto, playingId: String?): WatchLibraryState {
        val id = localId(state, row)
        val saved = state.episodes[id]
        val mine = saved?.lastPlayedAtMillis ?: 0L
        val known = saved != null || id in state.played
        return when {
            id == playingId -> state
            known && row.lastPlayedAt <= mine -> state
            row.isCompleted -> finished(state, id, row)
            row.progressMs <= 0L -> state
            else -> started(state, id, row)
        }
    }

    private fun finished(state: WatchLibraryState, id: String, row: ListeningHistorySyncDto): WatchLibraryState {
        val saved = state.episodes[id]?.copy(minutesLeft = 0, lastPlayedAtMillis = row.lastPlayedAt)
        return state.copy(
            episodes = if (saved == null) state.episodes else state.episodes + (id to saved),
            recent = state.recent.filterNot { it == id },
            positions = state.positions - id,
            played = state.played + id,
        )
    }

    private fun started(state: WatchLibraryState, id: String, row: ListeningHistorySyncDto): WatchLibraryState {
        val base = state.episodes[id] ?: WatchEpisode(id, row.podcastId, title = "", showTitle = "", artworkUrl = null, durationMinutes = 0)
        val total = if (row.durationMs > 0L) row.durationMs else base.durationMinutes * MINUTE_MS
        val episode =
            base.copy(
                title = row.episodeTitle ?: base.title,
                showTitle = row.podcastName ?: base.showTitle,
                artworkUrl = row.episodeImageUrl ?: row.podcastImageUrl ?: base.artworkUrl,
                durationMinutes = minutes(total),
                minutesLeft = minutes(total - row.progressMs).coerceAtLeast(1),
                audioUrl = row.episodeAudioUrl ?: base.audioUrl,
                lastPlayedAtMillis = row.lastPlayedAt,
                durationMs = if (row.durationMs > 0L) row.durationMs else base.durationMs,
                catalogKey = row.catalogKey ?: base.catalogKey,
            )
        return state.copy(
            episodes = state.episodes + (id to episode),
            recent = if (id in state.recent) state.recent else state.recent + id,
            positions = state.positions + (id to row.progressMs),
            played = state.played - id,
        )
    }

    /**
     * The ID the watch holds this episode under: one it already uses (the shared ID, the phone's
     * own, or a saved copy with the same audio address), else the shared ID when the phone knows it.
     */
    private fun localId(state: WatchLibraryState, row: ListeningHistorySyncDto): String {
        val shared = row.recipeId?.toString()
        val held = listOfNotNull(shared, row.episodeId).firstOrNull { it in state.episodes || it in state.played }
        val sameAudio =
            row.episodeAudioUrl?.let { audio ->
                state.episodes.values.firstOrNull { it.showId == row.podcastId && it.audioUrl == audio }?.id
            }
        return held ?: sameAudio ?: shared ?: row.episodeId
    }

    /** The listening the watch holds for [id], as the row the phone merges. */
    private fun historyRow(state: WatchLibraryState, id: String): ListeningHistorySyncDto? {
        val episode = state.episodes[id] ?: return null
        val completed = id in state.played
        return ListeningHistorySyncDto(
            episodeId = id,
            podcastId = episode.showId,
            episodeTitle = episode.title.takeIf { it.isNotBlank() },
            episodeImageUrl = episode.artworkUrl,
            podcastName = episode.showTitle.takeIf { it.isNotBlank() },
            episodeAudioUrl = episode.audioUrl,
            progressMs = if (completed) episode.durationMs else state.positions[id] ?: 0L,
            durationMs = episode.durationMs,
            isCompleted = completed,
            lastPlayedAt = episode.lastPlayedAtMillis,
            updatedAt = episode.lastPlayedAtMillis,
            recipeId = id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId),
            catalogKey = episode.catalogKey,
        )
    }

    fun episode(episode: WatchEpisode): WearLinkEpisode =
        WearLinkEpisode(
            episodeId = episode.id,
            podcastId = episode.showId,
            title = episode.title,
            podcastName = episode.showTitle,
            imageUrl = episode.artworkUrl,
            audioUrl = episode.audioUrl,
            durationMs = if (episode.durationMs > 0) episode.durationMs else episode.durationMinutes * MINUTE_MS,
            publishedAt = episode.releasedAtMillis ?: 0L,
            recipeId = episode.id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId),
            catalogKey = episode.catalogKey,
        )

    /** An episode the phone named, as the watch lists and plays it. */
    fun watchEpisode(episode: WearLinkEpisode, show: WatchShow? = null): WatchEpisode =
        WatchEpisode(
            id = episode.recipeId?.toString() ?: episode.episodeId,
            showId = episode.podcastId,
            title = episode.title,
            showTitle = episode.podcastName.ifBlank { show?.title.orEmpty() },
            artworkUrl = episode.imageUrl ?: show?.artworkUrl,
            durationMinutes = minutes(episode.durationMs),
            releasedAtMillis = episode.publishedAt.takeIf { it > 0L },
            audioUrl = episode.audioUrl,
            durationMs = episode.durationMs,
            catalogKey = episode.catalogKey,
        )

    private fun minutes(millis: Long): Int = if (millis <= 0L) 0 else ((millis + MINUTE_MS - 1) / MINUTE_MS).toInt()

    private fun WearLinkShow.toWatchShow(mine: WatchShow?): WatchShow =
        WatchShow(
            id = podcastId,
            title = title,
            publisher = author,
            artworkUrl = imageUrl,
            newEpisodes = mine?.newEpisodes ?: 0,
            subscribed = true,
        )

    private fun PendingShowChange.wire(): WatchSubscriptionChange =
        WatchSubscriptionChange(
            revision = revision,
            change =
                UserSubscriptionSyncDto(
                    podcastId = show.id,
                    isSubscribed = subscribed,
                    subscribedAt = if (subscribed) atMillis else 0L,
                    unsubscribedAt = if (subscribed) 0L else atMillis,
                    updatedAt = atMillis,
                ),
            show = WearLinkShow(podcastId = show.id, title = show.title, author = show.publisher, imageUrl = show.artworkUrl),
        )
}
