package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FirebaseAuthAuthenticatorTest {

    private open class FakeAuthRepository(
        var cachedToken: String? = "stale-token",
        var freshToken: String? = "fresh-refreshed-token",
    ) : AuthRepository {
        var forceRefreshCount = 0
        var cachedReadCount = 0

        override val currentUser: StateFlow<BoxLoreUser?> = MutableStateFlow(null)
        override val currentUserId: String? = "user-123"

        override suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser> =
            Result.failure(NotImplementedError())

        override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> =
            Result.failure(NotImplementedError())

        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> =
            Result.failure(NotImplementedError())

        override suspend fun sendMagicLink(email: String): Result<Unit> =
            Result.failure(NotImplementedError())

        override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> =
            Result.failure(NotImplementedError())

        override fun isSignInWithEmailLink(link: String): Boolean = false

        override suspend fun sendPasswordReset(email: String): Result<Unit> =
            Result.failure(NotImplementedError())

        var throwOnRefresh: Boolean = false

        override fun signOut() {}

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun getIdToken(forceRefresh: Boolean): String? {
            if (throwOnRefresh) {
                throw RuntimeException("Simulated Firebase token fetch failure")
            }
            return if (forceRefresh) {
                forceRefreshCount++
                freshToken
            } else {
                cachedReadCount++
                cachedToken
            }
        }
    }

    private fun create401Response(
        url: String = "https://api.boxlore.example/user/sync/push",
        authorizationHeader: String? = "Bearer stale-token",
        priorResponse: Response? = null,
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        if (authorizationHeader != null) {
            requestBuilder.header("Authorization", authorizationHeader)
        }
        val request = requestBuilder.build()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()
    }

    @Test
    fun `authenticates with force-refreshed token when token is stale`() {
        val fakeRepo = FakeAuthRepository(cachedToken = "stale-token", freshToken = "fresh-refreshed-token")
        val authenticator = FirebaseAuthAuthenticator(fakeRepo)

        val response = create401Response(authorizationHeader = "Bearer stale-token")
        val authenticatedRequest = authenticator.authenticate(null, response)

        assertNotNull(authenticatedRequest)
        assertEquals("Bearer fresh-refreshed-token", authenticatedRequest?.header("Authorization"))
        assertEquals(1, fakeRepo.forceRefreshCount)
    }

    @Test
    fun `reuses cached token if another thread already refreshed it`() {
        val fakeRepo = FakeAuthRepository(
            cachedToken = "new-token-from-concurrent-thread",
            freshToken = "should-not-be-called",
        )
        val authenticator = FirebaseAuthAuthenticator(fakeRepo)

        // The request that failed had "stale-token", but fakeRepo already has "new-token-from-concurrent-thread"
        val response = create401Response(authorizationHeader = "Bearer stale-token")
        val authenticatedRequest = authenticator.authenticate(null, response)

        assertNotNull(authenticatedRequest)
        assertEquals("Bearer new-token-from-concurrent-thread", authenticatedRequest?.header("Authorization"))
        assertEquals(1, fakeRepo.cachedReadCount)
        assertEquals(0, fakeRepo.forceRefreshCount) // Stamped call avoided!
    }

    @Test
    fun `stops retrying when response retry count exceeds maximum`() {
        val fakeRepo = FakeAuthRepository(cachedToken = "stale-token", freshToken = "stale-token")
        val authenticator = FirebaseAuthAuthenticator(fakeRepo)

        val r1 = create401Response()
        val r2 = create401Response(priorResponse = r1)
        val r3 = create401Response(priorResponse = r2)

        val authenticatedRequest = authenticator.authenticate(null, r3)
        assertNull(authenticatedRequest)
    }

    @Test
    fun `returns null if token cannot be refreshed or repository is null`() {
        val authenticatorWithNullRepo = FirebaseAuthAuthenticator { null }
        val response = create401Response()

        assertNull(authenticatorWithNullRepo.authenticate(null, response))

        val fakeRepo = FakeAuthRepository(cachedToken = null, freshToken = null)
        val authenticatorWithEmptyToken = FirebaseAuthAuthenticator(fakeRepo)

        assertNull(authenticatorWithEmptyToken.authenticate(null, response))
    }

    @Test
    fun `returns null safely when authRepository throws exception during refresh`() {
        val fakeRepo = FakeAuthRepository(cachedToken = "stale-token", freshToken = null)
        fakeRepo.throwOnRefresh = true
        val authenticator = FirebaseAuthAuthenticator(fakeRepo)

        val response = create401Response(authorizationHeader = "Bearer stale-token")
        val authenticatedRequest = authenticator.authenticate(null, response)

        assertNull(authenticatedRequest)
    }

    @Test
    fun `returns null when token retrieval times out`() {
        val fakeRepo = object : FakeAuthRepository() {
            override suspend fun getIdToken(forceRefresh: Boolean): String? {
                kotlinx.coroutines.delay(1_000L)
                return "never-reached"
            }
        }
        val authenticator = FirebaseAuthAuthenticator(
            authRepositoryProvider = { fakeRepo },
            tokenTimeoutMs = 100L,
        )

        val response = create401Response(authorizationHeader = "Bearer stale-token")
        val authenticatedRequest = authenticator.authenticate(null, response)

        assertNull(authenticatedRequest)
    }
}
