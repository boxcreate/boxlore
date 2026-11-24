package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.model.TvApproveRequest
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
    /** Sends the approval under this `Authorization` value: the answer's HTTP status, or null when none came. */
    private val send: suspend (authorization: String, code: String) -> Int?,
) : TvSignInApprover {
    constructor(auth: AuthRepository, api: () -> WatchAccountApi, io: CoroutineDispatcher = Dispatchers.IO) : this(
        user = { auth.currentUser.value },
        idToken = { auth.getIdToken(forceRefresh = false) },
        send = { authorization, code ->
            withContext(io) {
                runCatching {
                    val response = api().approveTv(authorization, TvApproveRequest(code)).execute()
                    response.body()?.close()
                    response.errorBody()?.close()
                    response.code()
                }.getOrNull()
            }
        },
    )

    override suspend fun approve(code: String): TvApproval {
        if (!canSyncUser(user())) return TvApproval.SignInNeeded
        val token = idToken() ?: return TvApproval.NoConnection
        return when (send("Bearer $token", code)) {
            OK -> TvApproval.Approved
            NOT_FOUND, BAD_REQUEST -> TvApproval.WrongCode
            UNAUTHORIZED -> TvApproval.SignInNeeded
            else -> TvApproval.NoConnection
        }
    }

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
    }
}
