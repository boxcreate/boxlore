package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import cx.aswin.boxlore.core.network.session.ApiSessionInterceptor
import cx.aswin.boxlore.core.wearlink.PhoneAccountPort
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.sync.LendingRules
import cx.aswin.boxlore.sync.canSyncUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signs boxlore on the watch in to the account the phone is signed in to: it asks the API for a
 * code only the watch can use, once, and answers the watch with it. An account that cannot sync
 * yet (an unverified email) is treated as signed out, as cloud sync treats it. A phone that must
 * pass boxlore's check before it can lend says so ([WatchSignInReply.CHECK_NEEDED]), and so does
 * one that lent too often lately ([WatchSignInReply.TRY_LATER]) or can't lend for now, check or
 * not ([WatchSignInReply.CANNOT_LEND]).
 */
class PhoneAccountAdapter(
    private val user: () -> BoxLoreUser?,
    private val idToken: suspend () -> String?,
    /** Asks the API for a watch sign-in code under this `Authorization` value. */
    private val createCode: suspend (authorization: String) -> CodeAnswer,
) : PhoneAccountPort {
    constructor(auth: AuthRepository, api: () -> WatchAccountApi, io: CoroutineDispatcher = Dispatchers.IO) : this(
        user = { auth.currentUser.value },
        idToken = { auth.getIdToken(forceRefresh = false) },
        createCode = { authorization ->
            withContext(io) {
                val response = runCatching { api().createCode(authorization).execute() }.getOrNull() ?: return@withContext CodeAnswer.NoAnswer
                val code = response.body()?.code?.takeIf { it.isNotBlank() }
                when {
                    code != null -> CodeAnswer.Code(code)
                    response.isSuccessful -> CodeAnswer.NoAnswer
                    else -> CodeAnswer.Refused(response.code(), ApiErrorCodes.of(response), ApiSessionInterceptor.sentAttestedSession(response.raw()))
                }
            }
        },
    )

    override suspend fun watchSignIn(): WatchSignInReply {
        val account = user()
        if (!canSyncUser(account)) return WatchSignInReply(WatchSignInReply.SIGNED_OUT)
        val token = idToken() ?: return WatchSignInReply(WatchSignInReply.FAILED)
        return when (val answer = createCode("Bearer $token")) {
            is CodeAnswer.Code -> WatchSignInReply(WatchSignInReply.OK, answer.value, PhoneAccountRules.name(account))
            is CodeAnswer.Refused -> WatchSignInReply(refusal(answer))
            CodeAnswer.NoAnswer -> WatchSignInReply(WatchSignInReply.FAILED)
        }
    }

    /** The [WatchSignInReply] status for a refused code. */
    private fun refusal(answer: CodeAnswer.Refused): String =
        when {
            LendingRules.cannotLend(answer.status, answer.error, answer.sentAttested) -> WatchSignInReply.CANNOT_LEND
            LendingRules.needsCheck(answer.status, answer.error, answer.sentAttested) -> WatchSignInReply.CHECK_NEEDED
            LendingRules.tooMany(answer.status) -> WatchSignInReply.TRY_LATER
            else -> WatchSignInReply.FAILED
        }

    /** What asking the API for a watch sign-in code got. */
    sealed interface CodeAnswer {
        data class Code(val value: String) : CodeAnswer {
            override fun toString(): String = "Code(…)"
        }

        /**
         * The API's [status] and `error` code ([error]), when it gave one; [sentAttested] when the
         * request went out with a session better than limited.
         */
        data class Refused(val status: Int, val error: String?, val sentAttested: Boolean = false) : CodeAnswer

        /** No answer, or one without a code. */
        data object NoAnswer : CodeAnswer
    }
}

internal object PhoneAccountRules {
    /** What the watch shows for the account: its email, else its name. */
    fun name(user: BoxLoreUser?): String? = user?.email?.takeIf { it.isNotBlank() } ?: user?.displayName?.takeIf { it.isNotBlank() }
}
