package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cloud Sync DTOs for multi-tenant data synchronization across devices in boxlore.
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
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class ListeningHistorySyncDto(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("progressMs") val progressMs: Long = 0L,
    @SerialName("durationMs") val durationMs: Long = 0L,
    @SerialName("isCompleted") val isCompleted: Boolean = false,
    @SerialName("isLiked") val isLiked: Boolean = false,
    @SerialName("likedAt") val likedAt: Long = 0L,
    @SerialName("lastPlayedAt") val lastPlayedAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class QueueItemSyncDto(
    @SerialName("episodeId") val episodeId: String,
    @SerialName("podcastId") val podcastId: String,
    @SerialName("position") val position: Int,
    @SerialName("addedAt") val addedAt: Long,
    @SerialName("contextType") val contextType: String? = "MANUAL",
    @SerialName("contextSourceId") val contextSourceId: String? = null,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class QueueSyncDto(
    @SerialName("items") val items: List<QueueItemSyncDto> = emptyList(),
    @SerialName("queueUpdatedAt") val queueUpdatedAt: Long = 0L,
    @SerialName("queueSequence") val queueSequence: Long = 0L,
    @SerialName("lastModifiedDeviceId") val lastModifiedDeviceId: String? = null,
    @SerialName("recentRemovedEpisodeIds") val recentRemovedEpisodeIds: List<String> = emptyList(),
)

@Serializable
data class SyncPushRequest(
    @SerialName("subscriptions") val subscriptions: List<UserSubscriptionSyncDto> = emptyList(),
    @SerialName("history") val history: List<ListeningHistorySyncDto> = emptyList(),
    @SerialName("queue") val queue: QueueSyncDto? = null,
    @SerialName("clientTimestamp") val clientTimestamp: Long = 0L,
)

@Serializable
data class SyncPushResponse(
    @SerialName("status") val status: String = "ok",
    @SerialName("syncedAt") val syncedAt: Long,
)

@Serializable
data class SyncPullRequest(
    @SerialName("since") val since: Long = 0L,
)

@Serializable
data class SyncPullResponse(
    @SerialName("subscriptions") val subscriptions: List<UserSubscriptionSyncDto> = emptyList(),
    @SerialName("history") val history: List<ListeningHistorySyncDto> = emptyList(),
    @SerialName("queue") val queue: QueueSyncDto? = null,
    @SerialName("syncedAt") val syncedAt: Long,
)

@Serializable
data class SyncDeleteAccountResponse(
    @SerialName("status") val status: String = "ok",
)
