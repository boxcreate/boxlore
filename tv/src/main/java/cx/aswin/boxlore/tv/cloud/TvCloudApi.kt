package cx.aswin.boxlore.tv.cloud

import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.TvPollRequest
import cx.aswin.boxlore.core.network.model.TvSignInStartResponse
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

/** What the API said to a call made with the TV's account token. */
internal sealed interface CloudAnswer<out T> {
    /** [renewedToken] is a fresh account token the API sent along, to keep in place of the one used. */
    class Ok<T>(val value: T, val renewedToken: String? = null) : CloudAnswer<T>

    /** The API no longer takes this TV's token: the TV is signed out. */
    data object Refused : CloudAnswer<Nothing>

    /** No answer, or an error that says nothing about the token; the same call is worth trying later. */
    data object Unreachable : CloudAnswer<Nothing>
}

/** Where the code the TV shows stands. */
internal sealed interface CodeCheck {
    data object Waiting : CodeCheck

    /** [account] is the approving account's email, when the API says. */
    data class Approved(val token: String, val account: String? = null) : CodeCheck

    /** The code ran out or is unknown: the TV needs a new one. */
    data object Expired : CodeCheck

    data object Unreachable : CodeCheck
}

/** The account routes as the TV uses them. */
internal interface TvCloudApi {
    /** A code to show; null when the API could not be reached. */
    suspend fun start(): TvSignInStartResponse?

    suspend fun poll(code: String, pair: String): CodeCheck

    /** Gives the token up; a failure is ignored, the token is forgotten here either way. */
    suspend fun revoke(token: String)

    suspend fun push(token: String, request: SyncPushRequest): CloudAnswer<SyncPushResponse>

    suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse>
}

internal class RetrofitTvCloudApi(
    private val api: WatchAccountApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TvCloudApi {
    override suspend fun start(): TvSignInStartResponse? =
        withContext(io) { runCatching { api.startTv().execute() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.takeIf { it.code.isNotBlank() } }

    override suspend fun poll(code: String, pair: String): CodeCheck =
        withContext(io) {
            val response = runCatching { api.pollTv(TvPollRequest(code, pair)).execute() }.getOrNull() ?: return@withContext CodeCheck.Unreachable
            val body = response.body()
            val token = body?.token?.takeIf { it.isNotBlank() }
            response.errorBody()?.close()
            when {
                response.code() == OK && token != null -> CodeCheck.Approved(token, body.account?.takeIf { it.isNotBlank() })
                response.code() == ACCEPTED -> CodeCheck.Waiting
                response.code() == NOT_FOUND -> CodeCheck.Expired
                else -> CodeCheck.Unreachable
            }
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
                response.isSuccessful && body != null -> CloudAnswer.Ok(body, response.headers()[WatchAccountApi.RENEW_HEADER]?.takeIf { it.isNotBlank() })
                refusesToken(response.code(), runCatching { response.errorBody()?.string() }.getOrNull()) -> CloudAnswer.Refused
                else -> CloudAnswer.Unreachable
            }
        }

    private fun bearer(token: String) = "Bearer $token"

    internal companion object {
        /** The API's code for a token it no longer takes (signed out elsewhere, expired, or the account is gone). */
        const val TOKEN_REFUSED = "watch_token_invalid"

        /** Only that code signs the TV out: any other 401 is about the device's session, which renews itself. */
        fun refusesToken(status: Int, body: String?): Boolean = status == UNAUTHORIZED && ApiErrorCodes.parse(body) == TOKEN_REFUSED

        private const val OK = 200
        private const val ACCEPTED = 202
        private const val UNAUTHORIZED = 401
        private const val NOT_FOUND = 404
    }
}
