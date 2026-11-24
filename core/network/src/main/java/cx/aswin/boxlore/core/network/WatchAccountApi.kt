package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.TvApproveRequest
import cx.aswin.boxlore.core.network.model.TvPollRequest
import cx.aswin.boxlore.core.network.model.TvSignInStartResponse
import cx.aswin.boxlore.core.network.model.TvTokenResponse
import cx.aswin.boxlore.core.network.model.WatchSignInCodeResponse
import cx.aswin.boxlore.core.network.model.WatchTokenRequest
import cx.aswin.boxlore.core.network.model.WatchTokenResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Signing boxlore on a watch or a TV in to the listener's account, and their own cloud sync.
 *
 * A watch never shows a sign-in screen. A phone that is signed in asks for a short-lived,
 * single-use code ([createCode]) and hands it to the watch over the phone link; the watch trades it
 * for its own account token ([redeem]) and from then on sends that token as `Authorization` on the
 * sync routes, where the phone sends the account's ID token.
 *
 * A TV has no phone link, so it shows a code instead ([startTv]); a phone signed in to the account
 * approves it ([approveTv]), and the TV collects its own token ([pollTv]) and uses it as a watch does.
 *
 * Paths are relative, so they resolve under the configured base URL. Requests carry the API
 * session from the shared client and the device key's signature. A sync answer may carry a
 * fresh token in [RENEW_HEADER]; the watch keeps it in place of the one it sent. Build it with
 * [NetworkModule.createWatchAccountApi].
 */
interface WatchAccountApi {
    /** Phone: a code the watch can redeem once. [authorization] is `Bearer` and the account's ID token. */
    @POST("auth/watch/code")
    fun createCode(@Header("Authorization") authorization: String): Call<WatchSignInCodeResponse>

    /** Watch: trades a code from the phone for this watch's own account token. */
    @POST("auth/watch/token")
    fun redeem(@Body request: WatchTokenRequest): Call<WatchTokenResponse>

    /** Watch: gives its account token up when the listener signs out there. */
    @POST("auth/watch/revoke")
    fun revoke(@Header("Authorization") authorization: String): Call<ResponseBody>

    /** Watch: the same push as the phone's, authorized by the watch's account token. */
    @POST("user/sync/push")
    fun syncPush(@Header("Authorization") authorization: String, @Body request: SyncPushRequest): Call<SyncPushResponse>

    /** Watch: the same pull as the phone's, authorized by the watch's account token. */
    @POST("user/sync/pull")
    fun syncPull(@Header("Authorization") authorization: String, @Body request: SyncPullRequest): Call<SyncPullResponse>

    /**
     * Phone: approves the code boxlore on a TV shows, for the account behind [authorization]
     * (`Bearer` and the ID token). 404 when the code is wrong, ran out or was already approved.
     */
    @POST("auth/tv/approve")
    fun approveTv(@Header("Authorization") authorization: String, @Body request: TvApproveRequest): Call<ResponseBody>

    /** TV: a code to show for a phone to approve. Signed by the TV's device key. */
    @POST("auth/tv/start")
    fun startTv(): Call<TvSignInStartResponse>

    /**
     * TV: whether its code was approved. 202 while it waits, 200 with the TV's own account token
     * (used as the watch's is) once a phone approved it, 404 once the code ran out.
     */
    @POST("auth/tv/poll")
    fun pollTv(@Body request: TvPollRequest): Call<TvTokenResponse>

    companion object {
        /** On a sync answer: a fresh token for a watch whose token is nearing its end. */
        const val RENEW_HEADER = "X-Watch-Token-Renew"
    }
}
