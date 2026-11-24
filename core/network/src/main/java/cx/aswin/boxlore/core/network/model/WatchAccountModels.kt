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

/** A code for a TV to show; a phone signed in to the account approves it. */
@Serializable
data class TvSignInStartResponse(
    @SerialName("code") val code: String,
    /** Proves the TV that asked is the one asking again; never shown. */
    @SerialName("pair") val pair: String,
    @SerialName("expiresInSeconds") val expiresInSeconds: Long = 0L,
    /** How often to ask whether the code was approved. */
    @SerialName("intervalSeconds") val intervalSeconds: Long = 0L,
)

/** The code a TV shows, as a phone sends it to approve. */
@Serializable
data class TvApproveRequest(
    @SerialName("code") val code: String,
)

/** The TV's own account token once a phone approved its code, and which account that is. */
@Serializable
data class TvTokenResponse(
    @SerialName("token") val token: String,
    @SerialName("expiresAt") val expiresAt: Long = 0L,
    /** The account's email, shown on the TV; null when the API does not say. */
    @SerialName("account") val account: String? = null,
)

/** The TV asking whether its code was approved. */
@Serializable
data class TvPollRequest(
    @SerialName("code") val code: String,
    @SerialName("pair") val pair: String,
)
