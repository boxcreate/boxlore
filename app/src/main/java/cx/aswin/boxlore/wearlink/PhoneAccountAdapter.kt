package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.WatchAccountApi
import cx.aswin.boxlore.core.wearlink.PhoneAccountPort
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.sync.canSyncUser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Signs boxlore on the watch in to the account the phone is signed in to: it asks the API for a
 * code only the watch can use, once, and answers the watch with it. An account that cannot sync
 * yet (an unverified email) is treated as signed out, as cloud sync treats it.
 */
class PhoneAccountAdapter(
    private val user: () -> BoxLoreUser?,
    private val idToken: suspend () -> String?,
    /** Asks the API for a watch sign-in code under this `Authorization` value; null when that failed. */
    private val createCode: suspend (authorization: String) -> String?,
) : PhoneAccountPort {
    constructor(auth: AuthRepository, api: () -> WatchAccountApi, io: CoroutineDispatcher = Dispatchers.IO) : this(
        user = { auth.currentUser.value },
        idToken = { auth.getIdToken(forceRefresh = false) },
        createCode = { authorization ->
            withContext(io) { runCatching { api().createCode(authorization).execute() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.code }
        },
    )

    override suspend fun watchSignIn(): WatchSignInReply {
        val account = user()
        if (!canSyncUser(account)) return WatchSignInReply(WatchSignInReply.SIGNED_OUT)
        val code = idToken()?.let { createCode("Bearer $it") }
        return if (code.isNullOrBlank()) WatchSignInReply(WatchSignInReply.FAILED) else WatchSignInReply(WatchSignInReply.OK, code, PhoneAccountRules.name(account))
    }
}

internal object PhoneAccountRules {
    /** What the watch shows for the account: its email, else its name. */
    fun name(user: BoxLoreUser?): String? = user?.email?.takeIf { it.isNotBlank() } ?: user?.displayName?.takeIf { it.isNotBlank() }
}
