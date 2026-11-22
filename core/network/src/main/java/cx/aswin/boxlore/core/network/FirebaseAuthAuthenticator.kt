package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 *
 * Only 401s from [apiBaseUrl]'s origin (scheme, host and port) are answered, so the ID token is
 * never sent to any other host that shares the client. An [apiBaseUrl] that isn't a valid HTTP(S)
 * URL answers no 401 at all, rather than failing app start.
 */
class FirebaseAuthAuthenticator(
    apiBaseUrl: String,
    private val authRepositoryProvider: () -> AuthRepository?,
    private val tokenTimeoutMs: Long = DEFAULT_TOKEN_TIMEOUT_MS,
) : Authenticator {

    constructor(apiBaseUrl: String, authRepositoryProvider: () -> AuthRepository?) :
        this(apiBaseUrl, authRepositoryProvider, DEFAULT_TOKEN_TIMEOUT_MS)

    constructor(apiBaseUrl: String, authRepository: AuthRepository) :
        this(apiBaseUrl, { authRepository }, DEFAULT_TOKEN_TIMEOUT_MS)

    constructor(apiBaseUrl: String, authRepository: AuthRepository, tokenTimeoutMs: Long) :
        this(apiBaseUrl, { authRepository }, tokenTimeoutMs)

    private val apiOrigin: HttpUrl? = apiBaseUrl.toHttpUrlOrNull()

    private val mutex = Mutex()

    /**
     * Authenticates an HTTP 401 response by resolving a fresh ID token.
     */
    override fun authenticate(route: Route?, response: Response): Request? {
        if (!isApiOrigin(response.request.url)) {
            return null
        }
        if (responseCount(response) >= MAX_RETRY_COUNT) {
            return null
        }
        // API session refusals belong to ApiSessionInterceptor; a Firebase token can't fix them.
        if (ApiErrorCodes.of(response) in ApiErrorCodes.SHIELD_CODES) {
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

    private fun isApiOrigin(url: HttpUrl): Boolean {
        val origin = apiOrigin ?: return false
        return url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port
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
