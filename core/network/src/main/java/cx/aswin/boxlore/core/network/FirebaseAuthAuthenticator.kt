package cx.aswin.boxlore.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val tokenTimeoutMs: Long = DEFAULT_TOKEN_TIMEOUT_MS,
) : Authenticator {

    constructor(authRepositoryProvider: () -> AuthRepository?) :
        this(authRepositoryProvider, DEFAULT_TOKEN_TIMEOUT_MS)

    constructor(authRepository: AuthRepository) :
        this({ authRepository }, DEFAULT_TOKEN_TIMEOUT_MS)

    constructor(authRepository: AuthRepository, tokenTimeoutMs: Long) :
        this({ authRepository }, tokenTimeoutMs)

    private val mutex = Mutex()

    /**
     * Authenticates an HTTP 401 response by resolving a fresh ID token.
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            return null
        }

        val failedToken = extractFailedToken(response)

        val refreshedToken = try {
            runBlocking {
                withTimeoutOrNull(tokenTimeoutMs) {
                    mutex.withLock {
                        resolveRefreshedToken(failedToken)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }

        return refreshedToken?.let { token ->
            response.request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
    }

    private fun extractFailedToken(response: Response): String? {
        val header = response.request.header("Authorization") ?: return null
        return header.removePrefix("Bearer ").trim()
    }

    private suspend fun resolveRefreshedToken(failedToken: String?): String? {
        val authRepo = authRepositoryProvider() ?: return null

        // 1. Check if another concurrent thread already refreshed the token
        val cachedToken = fetchTokenSafe(authRepo, forceRefresh = false)
        if (!cachedToken.isNullOrEmpty() && cachedToken != failedToken) {
            return cachedToken
        }

        // 2. Token is still stale; perform a force-refresh via Firebase Auth
        return fetchTokenSafe(authRepo, forceRefresh = true)
    }

    private suspend fun fetchTokenSafe(authRepo: AuthRepository, forceRefresh: Boolean): String? =
        try {
            authRepo.getIdToken(forceRefresh = forceRefresh)
        } catch (e: Exception) {
            null
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
        const val DEFAULT_TOKEN_TIMEOUT_MS = 10_000L
    }
}
