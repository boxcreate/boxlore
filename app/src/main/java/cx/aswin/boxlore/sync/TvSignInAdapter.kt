package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.model.TvApproveRequest
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import cx.aswin.boxlore.core.network.session.ApiSessionInterceptor
import cx.aswin.boxlore.feature.settings.pages.TvApproval
import cx.aswin.boxlore.feature.settings.pages.TvSignInApprover
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signs boxlore on a TV in to the account this phone is signed in to, by approving the code the TV
 * shows. An account that cannot sync yet (an unverified email) cannot sign a TV in either.
 */
class TvSignInAdapter(
    private val user: () -> BoxLoreUser?,
    private val idToken: suspend () -> String?,
    /** Sends the approval under this `Authorization` value: the API's answer, or null when none came. */
    private val send: suspend (authorization: String, code: String) -> Answer?,
) : TvSignInApprover {
    constructor(auth: AuthRepository, api: () -> WatchAccountApi, io: CoroutineDispatcher = Dispatchers.IO) : this(
        user = { auth.currentUser.value },
        idToken = { auth.getIdToken(forceRefresh = false) },
        send = { authorization, code ->
            withContext(io) {
                runCatching {
                    val response = api().approveTv(authorization, TvApproveRequest(code)).execute()
                    response.body()?.close()
                    // Reads and closes the error body.
                    Answer(response.code(), ApiErrorCodes.of(response), ApiSessionInterceptor.sentAttestedSession(response.raw()))
                }.getOrNull()
            }
        },
    )

    override suspend fun approve(code: String): TvApproval {
        if (!canSyncUser(user())) return TvApproval.SignInNeeded
        val token = idToken() ?: return TvApproval.NoConnection
        val answer = send("Bearer $token", code) ?: return TvApproval.NoConnection
        return approval(answer.status, answer.error, answer.sentAttested)
    }

    /**
     * The API's HTTP [status] and its `error` code ([error]), when it gave one; [sentAttested] when
     * the approval went out with a session better than limited ([ApiSessionInterceptor.sentAttestedSession]).
     */
    data class Answer(val status: Int, val error: String?, val sentAttested: Boolean = false)

    internal companion object {
        /** What the API's answer means for the listener. */
        fun approval(status: Int, error: String?, sentAttested: Boolean = false): TvApproval =
            when {
                status == OK -> TvApproval.Approved
                status == NOT_FOUND || status == BAD_REQUEST -> TvApproval.WrongCode
                LendingRules.cannotLend(status, error, sentAttested) -> TvApproval.CannotLend
                LendingRules.needsCheck(status, error, sentAttested) -> TvApproval.CheckNeeded
                status == UNAUTHORIZED && (error == null || error in STALE_SIGN_IN) -> TvApproval.SignInAgain
                // A sign-in the web showed never lends the account; a fresh one on this phone does.
                status == FORBIDDEN && error == WEB_SIGN_IN_NOT_ACCEPTED -> TvApproval.SignInAgain
                LendingRules.tooMany(status) -> TvApproval.TooManyTries
                status == FORBIDDEN && error == SIGN_IN_CLOSED -> TvApproval.Closed
                // boxlore answered but couldn't check the sign-in just now (503 sync_unavailable, sign_in_unavailable).
                status >= SERVER_ERROR -> TvApproval.Unavailable
                else -> TvApproval.NoConnection
            }

        /** The phone's sign-in no longer lends the account: not a full sign-in, or older than the account's reset. */
        private val STALE_SIGN_IN = setOf("sign_in_required", "account_deleted")
        private const val WEB_SIGN_IN_NOT_ACCEPTED = "web_sign_in_not_accepted"
        private const val SIGN_IN_CLOSED = "watch_sign_in_closed"
        private const val OK = 200
        private const val BAD_REQUEST = 400
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val SERVER_ERROR = 500
    }
}
