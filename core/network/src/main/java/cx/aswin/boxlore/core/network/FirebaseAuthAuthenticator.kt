package cx.aswin.boxlore.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Thread-safe OkHttp [Authenticator] that handles HTTP 401 Unauthorized responses
 * by refreshing the Firebase ID token.
 *
 * Uses a Kotlin [Mutex] to serialize token refreshes across concurrent network threads,
 * preventing token refresh stampedes to Google STS. If another thread has already refreshed
 * the token while a thread was waiting for the lock, the freshly cached token is reused
 * without an extra network call.
 */
class FirebaseAuthAuthenticator(
    private val authRepositoryProvider: () -> AuthRepository?,
) : Authenticator {

    constructor(authRepository: AuthRepository) : this({ authRepository })

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite retry loops
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            return null
        }

        val originalHeader = response.request.header("Authorization")
        val failedToken = originalHeader?.removePrefix("Bearer ")?.trim()

        return try {
            runBlocking {
                mutex.withLock {
                    val authRepo = authRepositoryProvider() ?: return@withLock null

                    // 1. Check if another concurrent thread already refreshed the token
                    val cachedToken = try {
                        authRepo.getIdToken(forceRefresh = false)
                    } catch (e: Exception) {
                        null
                    }

                    val effectiveToken = if (!cachedToken.isNullOrEmpty() && cachedToken != failedToken) {
                        cachedToken
                    } else {
                        // 2. Token is still stale; perform a force-refresh via Firebase Auth
                        try {
                            authRepo.getIdToken(forceRefresh = true)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (effectiveToken.isNullOrEmpty()) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $effectiveToken")
                            .build()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }
}
