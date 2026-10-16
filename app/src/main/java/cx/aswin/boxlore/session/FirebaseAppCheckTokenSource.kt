package cx.aswin.boxlore.session

import com.google.firebase.appcheck.FirebaseAppCheck
import cx.aswin.boxlore.core.network.session.AppCheckResult
import cx.aswin.boxlore.core.network.session.AppCheckTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * App Check for `/auth/session` only. API requests never wait on it; the session manager bounds
 * how long an exchange waits for it.
 */
internal class FirebaseAppCheckTokenSource(private val appCheck: () -> FirebaseAppCheck) : AppCheckTokenSource {
    override suspend fun fetch(): AppCheckResult = suspendCancellableCoroutine { continuation ->
        val task = runCatching { appCheck().getAppCheckToken(false) }.getOrElse { error ->
            continuation.resume(AppCheckResult.Failed(AppCheckFailureReason.of(error)))
            return@suspendCancellableCoroutine
        }
        task.addOnCompleteListener { done ->
            if (!continuation.isActive) return@addOnCompleteListener
            val token = done.takeIf { it.isSuccessful }?.result?.token
            continuation.resume(
                if (!token.isNullOrEmpty()) AppCheckResult.Token(token) else AppCheckResult.Failed(AppCheckFailureReason.of(done.exception)),
            )
        }
    }
}
