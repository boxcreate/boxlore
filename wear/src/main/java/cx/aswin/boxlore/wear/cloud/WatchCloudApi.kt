package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.WatchTokenRequest
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

/** What the API said to a call made with the watch's account token. */
internal sealed interface CloudAnswer<out T> {
    class Ok<T>(val value: T) : CloudAnswer<T>

    /** The API no longer takes this watch's token: the watch is signed out. */
    data object Refused : CloudAnswer<Nothing>

    /** No answer, or an error that says nothing about the token; the same call is worth trying later. */
    data object Unreachable : CloudAnswer<Nothing>
}

/** The account routes as the watch uses them. */
internal interface WatchCloudApi {
    /** Trades the phone's code for this watch's account token; null when that did not work. */
    suspend fun redeem(code: String): String?

    /** Gives the token up; a failure is ignored, the token is forgotten here either way. */
    suspend fun revoke(token: String)

    suspend fun push(token: String, request: SyncPushRequest): CloudAnswer<SyncPushResponse>

    suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse>
}

internal class RetrofitWatchCloudApi(
    private val api: WatchAccountApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WatchCloudApi {
    override suspend fun redeem(code: String): String? =
        withContext(io) {
            runCatching { api.redeem(WatchTokenRequest(code)).execute() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.token?.takeIf { it.isNotBlank() }
        }

    override suspend fun revoke(token: String) {
        withContext(io) { runCatching { api.revoke(bearer(token)).execute().body()?.close() } }
    }

    override suspend fun push(token: String, request: SyncPushRequest): CloudAnswer<SyncPushResponse> = answer { api.syncPush(bearer(token), request) }

    override suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse> = answer { api.syncPull(bearer(token), request) }

    private suspend fun <T> answer(request: () -> Call<T>): CloudAnswer<T> =
        withContext(io) {
            val response = runCatching { request().execute() }.getOrNull() ?: return@withContext CloudAnswer.Unreachable
            val body = response.body()
            when {
                response.isSuccessful && body != null -> CloudAnswer.Ok(body)
                refusesToken(response.code(), runCatching { response.errorBody()?.string() }.getOrNull()) -> CloudAnswer.Refused
                else -> CloudAnswer.Unreachable
            }
        }

    private fun bearer(token: String) = "Bearer $token"

    internal companion object {
        /** The API's code for a watch token it no longer takes (signed out elsewhere, expired, or the account is gone). */
        const val TOKEN_REFUSED = "watch_token_invalid"

        /** Only that code signs the watch out: any other 401 is about the device's session, which renews itself. */
        fun refusesToken(status: Int, body: String?): Boolean = status == UNAUTHORIZED && ApiErrorCodes.parse(body) == TOKEN_REFUSED

        private const val UNAUTHORIZED = 401
    }
}
