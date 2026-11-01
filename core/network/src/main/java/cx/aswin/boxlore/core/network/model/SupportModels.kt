package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.Serializable

/** Wire shapes for [cx.aswin.boxlore.core.network.SupportApi]; unknown fields are ignored. */
@Serializable
data class SupportListResponse(
    val meta: SupportMetaDto? = null,
    val threads: List<SupportThreadDto> = emptyList(),
)

@Serializable
data class SupportMetaDto(
    val notice: String? = null,
    val canStartNew: Boolean = true,
    val photos: SupportPhotoLimitsDto = SupportPhotoLimitsDto(),
    val maxBody: Int = 2000,
    /** Every topic to subscribe to for reply pushes: this install's, and the account's when signed in. */
    val pushTopics: List<String> = emptyList(),
    val replyAuthor: String? = null,
)

@Serializable
data class SupportPhotoLimitsDto(
    val enabled: Boolean = false,
    val max: Int = 0,
    val maxBytes: Long = 0,
)

@Serializable
data class SupportThreadDto(
    val id: String,
    val category: String = "other",
    val title: String = "",
    val status: SupportStatusDto? = null,
    val updatedAt: Long = 0,
    val unread: Boolean = false,
    val canReply: Boolean = true,
)

@Serializable
data class SupportStatusDto(
    val key: String = "waiting",
    val label: String = "",
    val tone: String = "neutral",
    val showTime: Boolean = true,
)

@Serializable
data class SupportThreadResponse(val thread: SupportThreadDto? = null)

@Serializable
data class SupportPhotoResponse(val id: String)

@Serializable
data class SupportNewThreadRequest(
    val category: String,
    val body: String,
    val photos: List<String> = emptyList(),
    val appVersion: String? = null,
    val device: String? = null,
    val diagnostics: String? = null,
    val logs: String? = null,
    /** Same key for a retry of the same message, so the API saves it once. */
    val requestId: String? = null,
)

/** Error bodies (`paused`, `limit`, `photos_off`, ...) with an optional message to show as is. */
@Serializable
data class SupportErrorResponse(val error: String = "", val message: String? = null)
