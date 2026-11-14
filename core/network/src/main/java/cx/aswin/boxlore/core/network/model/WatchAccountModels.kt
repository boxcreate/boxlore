package cx.aswin.boxlore.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A code a signed-in phone hands to its watch; good once, and only for a short time. */
@Serializable
data class WatchSignInCodeResponse(
    @SerialName("code") val code: String,
    @SerialName("expiresInSeconds") val expiresInSeconds: Long = 0L,
)

@Serializable
data class WatchTokenRequest(
    @SerialName("code") val code: String,
)

/** The watch's own account token. It is sent as `Authorization: Bearer` on the sync routes. */
@Serializable
data class WatchTokenResponse(
    @SerialName("token") val token: String,
    /** When the token stops working, as epoch milliseconds; 0 when the API does not say. */
    @SerialName("expiresAt") val expiresAt: Long = 0L,
)
