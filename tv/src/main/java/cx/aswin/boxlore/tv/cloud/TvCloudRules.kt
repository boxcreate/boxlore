package cx.aswin.boxlore.tv.cloud

import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.tv.data.PendingShow
import cx.aswin.boxlore.tv.data.TvLibraryState
import cx.aswin.boxlore.tv.data.notingEpisode
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow

/** One push to the cloud, and the revision it brings the cloud up to. */
internal class CloudPush(val request: SyncPushRequest, val covered: Long)

/**
 * How the TV's library and the listener's account meet, by the same rows the phone and the watch
 * exchange with the cloud. The cloud merges each part of a listening row by its own time (play,
 * like), so a row from the TV only wins where the TV acted last. Follows the TV never touched are
 * never sent, so a TV cannot change a show's settings on the phone.
 */
internal object TvCloudRules {
    /** Listening changes sent at once; the rest follow with the next sync. */
    const val MAX_PUSHED_HISTORY = 100

    /** A first pull carries an account's whole history; the TV keeps only the most recent of it. */
    const val MAX_PULLED_HISTORY = 200

    private const val MINUTE_MS = 60_000L

    /** Signing in: everything listened to here waits for the cloud; follows wait for the first pull to see what the account has. */
    fun linked(state: TvLibraryState): TvLibraryState {
        val listened = (state.history + state.liked + state.recent).distinct().filter { it in state.episodes }
        val base = state.copy(cloudLinked = true, cloudAcked = 0L, cloudSince = 0L, pendingShows = emptyMap(), pendingEpisodes = emptyMap())
        return listened.fold(base) { next, id -> next.notingEpisode(id) }
    }

    /** Signing out: the library stays on the TV, and nothing waits for the cloud any more. */
    fun unlinked(state: TvLibraryState): TvLibraryState =
        state.copy(cloudLinked = false, pendingShows = emptyMap(), pendingEpisodes = emptyMap(), cloudAcked = 0L, cloudSince = 0L).forgetUnused()

    /** What the cloud still has to hear from this TV; null when nothing waits. */
    fun push(state: TvLibraryState, now: Long, maxHistory: Int = MAX_PUSHED_HISTORY): CloudPush? {
        val waiting = state.pendingEpisodes.entries.filter { it.value > state.cloudAcked }.sortedBy { it.value }
        val sent = waiting.take(maxHistory)
        // With more waiting than fits, this push covers only up to the last row it carries.
        val covered = if (waiting.size > sent.size) sent.last().value else state.revision
        val shows = state.pendingShows.values.filter { it.revision > state.cloudAcked && it.revision <= covered }.sortedBy { it.revision }
        if (sent.isEmpty() && shows.isEmpty()) return null
        return CloudPush(SyncPushRequest(subscriptions = shows.map { it.followRow() }, history = sent.mapNotNull { historyRow(state, it.key) }, clientTimestamp = now), covered)
    }

    /** The library after the cloud took a push that [covered] this revision: what it confirmed no longer waits. */
    fun pushed(state: TvLibraryState, covered: Long): TvLibraryState {
        val acked = maxOf(state.cloudAcked, covered)
        return state.copy(
            cloudAcked = acked,
            pendingShows = state.pendingShows.filterValues { it.revision > acked },
            pendingEpisodes = state.pendingEpisodes.filterValues { it > acked },
        ).forgetUnused()
    }

    /** Shows the cloud says are followed that the TV has no name or artwork for yet. */
    fun unknownShows(state: TvLibraryState, pull: SyncPullResponse): List<String> =
        pull.subscriptions.filter { it.isSubscribed && applies(state, it) && !state.isSubscribed(it.podcastId) }.map { it.podcastId }.distinct()

    /**
     * The library after a pull. [shows] are the followed shows looked up for it; a show that could
     * not be looked up keeps the pull's time where it was, so the next pull names it again. On the
     * first pull, shows followed only on this TV are sent up as new follows. [playingId] is the
     * episode in the TV's player, which the cloud never moves.
     */
    fun withCloud(state: TvLibraryState, pull: SyncPullResponse, shows: Map<String, TvShow>, playingId: String?, now: Long): TvLibraryState {
        var missing = false
        var followed = state.shows
        for (row in pull.subscriptions.sortedBy { it.updatedAt }) {
            val named = shows[row.podcastId]
            when {
                !applies(state, row) -> Unit
                !row.isSubscribed -> followed = followed.filterNot { it.id == row.podcastId }
                followed.any { it.id == row.podcastId } -> Unit
                named != null -> followed = listOf(named.copy(subscribed = true)) + followed
                else -> missing = true
            }
        }
        var next = state.copy(shows = followed)
        if (state.cloudSince == 0L) {
            val known = pull.subscriptions.mapTo(HashSet()) { it.podcastId }
            next = followed.filterNot { it.id in known || it.id in next.pendingShows }.fold(next) { acc, show -> acc.followedHereOnly(show, now) }
        }
        for (row in pull.history.sortedByDescending { maxOf(it.lastPlayedAt, it.likedAt) }.take(MAX_PULLED_HISTORY)) next = withRow(next, row, playingId)
        return settled(next).copy(cloudSince = if (missing) state.cloudSince else pull.syncedAt)
    }

    /** The listening the TV holds for [id], as the row the phone and the cloud merge. */
    fun historyRow(state: TvLibraryState, id: String): ListeningHistorySyncDto? {
        val episode = state.episodes[id] ?: return null
        val completed = id in state.played
        val duration = state.durations[id] ?: (episode.durationMinutes * MINUTE_MS)
        val playedAt = state.playedAt[id] ?: 0L
        val likedAt = state.likedAt[id] ?: 0L
        return ListeningHistorySyncDto(
            episodeId = id,
            podcastId = episode.showId,
            episodeTitle = episode.title.takeIf { it.isNotBlank() },
            episodeImageUrl = episode.artworkUrl,
            podcastName = episode.showTitle.takeIf { it.isNotBlank() },
            episodeAudioUrl = episode.audioUrl,
            progressMs = if (completed) duration else state.positions[id] ?: 0L,
            durationMs = duration,
            isCompleted = completed,
            isLiked = id in state.liked,
            likedAt = likedAt,
            lastPlayedAt = playedAt,
            updatedAt = maxOf(playedAt, likedAt),
            recipeId = id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId),
            catalogKey = episode.catalogKey,
        )
    }

    /** One listening row from the cloud: its play and its like each win where they are newer, except over the episode playing here. */
    fun withRow(state: TvLibraryState, row: ListeningHistorySyncDto, playingId: String?): TvLibraryState {
        val id = localId(state, row)
        val listened =
            when {
                id == playingId || row.lastPlayedAt <= (state.playedAt[id] ?: 0L) -> state
                row.isCompleted -> finished(state, id, row)
                row.progressMs > 0L -> started(state, id, row)
                // Marked unplayed elsewhere: the TV forgets its finish and its place too.
                else -> reset(state, id, row)
            }
        return withLike(listened, id, row)
    }

    private fun withLike(state: TvLibraryState, id: String, row: ListeningHistorySyncDto): TvLibraryState {
        if (row.likedAt <= (state.likedAt[id] ?: 0L)) return state
        val liked = if (row.isLiked) listOf(id) + state.liked.filterNot { it == id } else state.liked - id
        return state.copy(episodes = state.episodes + (id to episodeOf(state, id, row)), liked = liked, likedAt = state.likedAt + (id to row.likedAt))
    }

    private fun finished(state: TvLibraryState, id: String, row: ListeningHistorySyncDto): TvLibraryState =
        state.copy(
            episodes = state.episodes + (id to episodeOf(state, id, row).copy(minutesLeft = null)),
            recent = state.recent - id,
            positions = state.positions - id,
            played = state.played + id,
            history = listOf(id) + state.history.filterNot { it == id },
            playedAt = state.playedAt + (id to row.lastPlayedAt),
        )

    private fun reset(state: TvLibraryState, id: String, row: ListeningHistorySyncDto): TvLibraryState {
        val known = state.episodes[id] ?: return state.copy(playedAt = state.playedAt + (id to row.lastPlayedAt))
        return state.copy(
            episodes = state.episodes + (id to known.copy(minutesLeft = null)),
            recent = state.recent - id,
            positions = state.positions - id,
            played = state.played - id,
            playedAt = state.playedAt + (id to row.lastPlayedAt),
        )
    }

    private fun started(state: TvLibraryState, id: String, row: ListeningHistorySyncDto): TvLibraryState {
        val episode = episodeOf(state, id, row)
        val total = if (row.durationMs > 0L) row.durationMs else episode.durationMinutes * MINUTE_MS
        val left = TvLibraryState.wholeMinutes(total - row.progressMs).coerceAtLeast(1)
        return state.copy(
            episodes = state.episodes + (id to episode.copy(minutesLeft = left)),
            recent = listOf(id) + state.recent.filterNot { it == id },
            positions = state.positions + (id to row.progressMs),
            played = state.played - id,
            history = listOf(id) + state.history.filterNot { it == id },
            playedAt = state.playedAt + (id to row.lastPlayedAt),
            durations = if (row.durationMs > 0L) state.durations + (id to row.durationMs) else state.durations,
        )
    }

    /** The TV's copy of the row's episode, filled in from the row where the TV knows less. */
    private fun episodeOf(state: TvLibraryState, id: String, row: ListeningHistorySyncDto): TvEpisode {
        val base = state.episodes[id] ?: TvEpisode(id, row.podcastId, title = "", showTitle = "", artworkUrl = null, durationMinutes = 0)
        val total = if (row.durationMs > 0L) row.durationMs else base.durationMinutes * MINUTE_MS
        return base.copy(
            title = base.title.ifBlank { row.episodeTitle.orEmpty() },
            showTitle = base.showTitle.ifBlank { row.podcastName.orEmpty() },
            artworkUrl = listOfNotNull(base.artworkUrl, row.episodeImageUrl, row.podcastImageUrl).firstOrNull(),
            durationMinutes = if (base.durationMinutes > 0) base.durationMinutes else TvLibraryState.wholeMinutes(total),
            audioUrl = base.audioUrl ?: row.episodeAudioUrl,
            catalogKey = base.catalogKey ?: row.catalogKey,
        )
    }

    /** After a pull: started episodes, history and likes in the order they happened, no more than the TV keeps. */
    private fun settled(state: TvLibraryState): TvLibraryState {
        val playOrder = compareByDescending<String> { state.playedAt[it] ?: 0L }
        return state.copy(
            recent = state.recent.filter { it !in state.played }.sortedWith(playOrder),
            history = state.history.distinct().sortedWith(playOrder).take(TvLibraryState.HISTORY_LIMIT),
            liked = state.liked.distinct().sortedByDescending { state.likedAt[it] ?: 0L },
        ).forgetUnused()
    }
}

/** A show followed on this TV before it signed in, which the account has never heard of: sent up as a new follow. */
private fun TvLibraryState.followedHereOnly(show: TvShow, now: Long): TvLibraryState =
    (revision + 1).let { next -> copy(revision = next, pendingShows = pendingShows + (show.id to PendingShow(show, subscribed = true, atMillis = now, revision = next))) }

/** A follow or unfollow made on the TV, as the row the phone and the cloud merge. Settings stay as the phone has them. */
private fun PendingShow.followRow(): UserSubscriptionSyncDto =
    UserSubscriptionSyncDto(
        podcastId = show.id,
        isSubscribed = subscribed,
        subscribedAt = if (subscribed) atMillis else 0L,
        unsubscribedAt = if (subscribed) 0L else atMillis,
        updatedAt = atMillis,
    )

/** The ID the TV holds this episode under: one it already uses, else boxlore's own ID when the row has it. */
private fun localId(state: TvLibraryState, row: ListeningHistorySyncDto): String {
    val shared = row.recipeId?.toString()
    val held = listOfNotNull(shared, row.episodeId).firstOrNull { it in state.episodes || it in state.played }
    val sameAudio = row.episodeAudioUrl?.let { audio -> state.episodes.values.firstOrNull { it.showId == row.podcastId && it.audioUrl == audio }?.id }
    return held ?: sameAudio ?: shared ?: row.episodeId
}

/** A follow or unfollow made on this TV after the cloud's row is still on its way up; it stands. */
private fun applies(state: TvLibraryState, row: UserSubscriptionSyncDto): Boolean = state.pendingShows[row.podcastId]?.let { it.atMillis < row.updatedAt } ?: true
