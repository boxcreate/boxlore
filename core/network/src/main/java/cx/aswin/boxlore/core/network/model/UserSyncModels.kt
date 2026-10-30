package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cloud Sync DTOs for multi-tenant data synchronization across devices in boxlore.
 *
 * The client JSON has `encodeDefaults` off: a field equal to its default is not sent. The episode
 * identity fields (`recipeId`, `catalogKey`, the rich queue removals) are therefore left out
 * whenever they are unknown, which is exactly what the server treats as "keep what is stored".
 */

@Serializable
data class UserSubscriptionSyncDto(
    @SerialName("podcastId") val podcastId: String,
    @SerialName("isSubscribed") val isSubscribed: Boolean = true,
    @SerialName("subscribedAt") val subscribedAt: Long,
    @SerialName("unsubscribedAt") val unsubscribedAt: Long = 0L,
    @SerialName("customGenre") val customGenre: String? = null,
    @SerialName("autoDownloadEnabled") val autoDownloadEnabled: Boolean = false,
    @SerialName("notificationsEnabled") val notificationsEnabled: Boolean = false,
    @SerialName("feedUrl") val feedUrl: String? = null,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class ListeningHistorySyncDto(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("episodeTitle") val episodeTitle: String? = null,
    @SerialName("episodeImageUrl") val episodeImageUrl: String? = null,
    @SerialName("podcastImageUrl") val podcastImageUrl: String? = null,
    @SerialName("podcastName") val podcastName: String? = null,
    @SerialName("episodeAudioUrl") val episodeAudioUrl: String? = null,
    @SerialName("progressMs") val progressMs: Long = 0L,
    @SerialName("durationMs") val durationMs: Long = 0L,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
    @SerialName("isLiked") val isLiked: Boolean = false,
    @SerialName("likedAt") val likedAt: Long = 0L,
    @SerialName("lastPlayedAt") val lastPlayedAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long,
    /**
     * The episode's recipe ID when this device has a trusted identity for the row, else null (read
     * it through [SyncEpisodeIdentityFields]). Added in P4 of the own-episode-IDs plan (plan
     * section 8, cloud sync identity).
     */
    @SerialName("recipeId") val recipeId: Long? = null,
    /** The episode's recipe-trimmed catalog key, under the same rule as [recipeId]. */
    @SerialName("catalogKey") val catalogKey: String? = null,
    /** First play and first finish by listening (Room v40): null unknown, 0 before recording, else epoch ms. Older servers ignore them. */
    @SerialName("firstPlayedAt") val firstPlayedAt: Long? = null,
    @SerialName("finishedAt") val finishedAt: Long? = null,
)

@Serializable
data class QueueItemSyncDto(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("podcastTitle") val podcastTitle: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("podcastImageUrl") val podcastImageUrl: String? = null,
    @SerialName("audioUrl") val audioUrl: String? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("pubDate") val pubDate: Long? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("position") val position: Int,
    @SerialName("addedAt") val addedAt: Long = 0L,
    @SerialName("contextType") val contextType: String? = "MANUAL",
    @SerialName("contextSourceId") val contextSourceId: String? = null,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
    /**
     * The episode's recipe ID when this device has a trusted identity for the row, else null (read
     * it through [SyncEpisodeIdentityFields]). Added in P4 of the own-episode-IDs plan (plan
     * section 8, cloud sync identity).
     */
    @SerialName("recipeId") val recipeId: Long? = null,
    /** The episode's recipe-trimmed catalog key, under the same rule as [recipeId]. */
    @SerialName("catalogKey") val catalogKey: String? = null,
)

/**
 * One recent queue removal with the episode's identity (`queue.recentRemovedEpisodes`). With the
 * show and the recipe ID or catalog key, the server removes every stored queue row of that
 * episode, and other devices remove it under their own IDs.
 *
 * @property id the removed queue row's episode ID
 * @property recipeId the episode's recipe ID, when known
 * @property show the episode's show ID, when the episode has an identity
 * @property catalogKey the episode's catalog key, when known
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
 * COMPAT(own-ids): queue removals across an episode's IDs on the sync wire. Permanent: never remove (MIGRATION.md §5).
 */
@Serializable
data class QueueRemovalSyncDto(
    @SerialName("id") val id: String,
    @SerialName("recipeId") val recipeId: Long? = null,
    @SerialName("show") val show: String? = null,
    @SerialName("catalogKey") val catalogKey: String? = null,
)

@Serializable
data class QueueSyncDto(
    @SerialName("items") val items: List<QueueItemSyncDto> = emptyList(),
    @SerialName("queueUpdatedAt") val queueUpdatedAt: Long = 0L,
    @SerialName("queueSequence") val queueSequence: Long = 0L,
    @SerialName("lastModifiedDeviceId") val lastModifiedDeviceId: String? = null,
    @SerialName("recentRemovedEpisodeIds") val recentRemovedEpisodeIds: List<String> = emptyList(),
    /**
     * Recent removals with the episode's identity. A push sends the device's whole list, even when
     * empty, because the server replaces its list with the one sent and keeps it only when the
     * field is absent (older builds). A pull carries it only when the request asked for it
     * ([SyncPullRequest.richRemovals]); null means the server didn't send it.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
     */
    @SerialName("recentRemovedEpisodes") val recentRemovedEpisodes: List<QueueRemovalSyncDto>? = null,
)

/**
 * One phone's listening of one episode on one local day (Room v40 daily summary). Each phone pushes
 * its own days in full; the server keeps the latest push of a (phone, day, episode).
 */
@Serializable
data class ListeningDaySyncDto(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("localDay") val localDay: Long,
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("consumedMs") val consumedMs: Long = 0L,
    @SerialName("wallMs") val wallMs: Long = 0L,
    @SerialName("sessionCount") val sessionCount: Int = 0,
    @SerialName("completionCount") val completionCount: Int = 0,
    @SerialName("morningMs") val morningMs: Long = 0L,
    @SerialName("afternoonMs") val afternoonMs: Long = 0L,
    @SerialName("eveningMs") val eveningMs: Long = 0L,
    @SerialName("nightMs") val nightMs: Long = 0L,
    @SerialName("lastListenedAt") val lastListenedAt: Long = 0L,
    /** The server's write time, on pulls. */
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

/** A show the listener played, followed or not (Room v40). The server merges every phone's. */
@Serializable
data class PlayedShowSyncDto(
    @SerialName("podcastId") val podcastId: String,
    @SerialName("podcastName") val podcastName: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("genre") val genre: String? = null,
    /** 0 when before boxlore recorded it. */
    @SerialName("firstPlayedAt") val firstPlayedAt: Long = 0L,
    @SerialName("lastPlayedAt") val lastPlayedAt: Long = 0L,
    /** The server's write time, on pulls. */
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

@Serializable
data class SyncPushRequest(
    @SerialName("subscriptions") val subscriptions: List<UserSubscriptionSyncDto> = emptyList(),
    @SerialName("history") val history: List<ListeningHistorySyncDto> = emptyList(),
    @SerialName("queue") val queue: QueueSyncDto? = null,
    /** This phone's daily listening (Room v40); not sent when empty. */
    @SerialName("listeningDays") val listeningDays: List<ListeningDaySyncDto> = emptyList(),
    /** Shows this phone played (Room v40); not sent when empty. */
    @SerialName("playedShows") val playedShows: List<PlayedShowSyncDto> = emptyList(),
    @SerialName("clientTimestamp") val clientTimestamp: Long = 0L,
)

@Serializable
data class SyncPushResponse(
    @SerialName("status") val status: String = "ok",
    @SerialName("syncedAt") val syncedAt: Long,
    /** The server stored the push's listening (Room v40 server); older servers leave it out. */
    @SerialName("listeningAccepted") val listeningAccepted: Boolean = false,
)

@Serializable
data class SyncPullRequest(
    @SerialName("since") val since: Long = 0L,
    /**
     * Asks for the queue's rich removals (`queue.recentRemovedEpisodes`). Callers set it to true
     * explicitly: with `encodeDefaults` off a false value is never sent, and the server then
     * leaves the rich list out (the answer older builds get).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, rich queue tombstones).
     */
    @SerialName("richRemovals") val richRemovals: Boolean = false,
    /** Asks for every phone's daily listening and the played shows (Room v40 builds). */
    @SerialName("listening") val listening: Boolean = false,
    /** The listening mark, sent even when 0 (nullable, so `encodeDefaults` off still sends 0); the server falls back to [since]. */
    @SerialName("listeningSince") val listeningSince: Long? = null,
)

@Serializable
data class SyncPullResponse(
    @SerialName("subscriptions") val subscriptions: List<UserSubscriptionSyncDto> = emptyList(),
    @SerialName("history") val history: List<ListeningHistorySyncDto> = emptyList(),
    @SerialName("queue") val queue: QueueSyncDto? = null,
    /** Only when the pull asked for `listening`. */
    @SerialName("listeningDays") val listeningDays: List<ListeningDaySyncDto>? = null,
    @SerialName("playedShows") val playedShows: List<PlayedShowSyncDto>? = null,
    @SerialName("syncedAt") val syncedAt: Long,
)

@Serializable
data class SyncDeleteAccountResponse(
    @SerialName("status") val status: String = "ok",
)

/**
 * The episode identity fields cloud sync carries on history and queue rows, read safely from the
 * wire: a recipe ID outside `[2^52, 2^53 − 1]` and a blank catalog key count as unknown.
 *
 * Legacy episode IDs stay opaque strings; only these optional fields are numbers.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, cloud sync identity).
 * COMPAT(own-ids): episode identity on the sync wire. Permanent: never remove (MIGRATION.md §5).
 */
object SyncEpisodeIdentityFields {
    /** [recipeId] when it is in the recipe range, else null. */
    fun recipeIdOrNull(recipeId: Long?): Long? = recipeId?.takeIf(V2EpisodeIds::isRecipeId)

    /** [catalogKey] when it is a usable key (not blank, at most [MAX_CATALOG_KEY_LENGTH] characters), else null. */
    fun catalogKeyOrNull(catalogKey: String?): String? =
        catalogKey?.takeIf { it.isNotBlank() && it.length <= MAX_CATALOG_KEY_LENGTH }

    /** The longest catalog key the server stores. */
    const val MAX_CATALOG_KEY_LENGTH = 2048
}
