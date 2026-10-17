package cx.aswin.boxlore.core.auth

import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailLinkSignInTest {
    private class LinkAuthRepository : AuthRepository {
        override val currentUser: StateFlow<BoxLoreUser?> = MutableStateFlow(null)
        override val currentUserId: String? = null
        val results = ArrayDeque<Result<BoxLoreUser>>()
        var gate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> {
            calls += email to emailLink
            gate?.await()
            return results.removeFirstOrNull() ?: Result.success(user(email))
        }

        override fun isSignInWithEmailLink(link: String): Boolean = link.contains("oobCode")
        override suspend fun signInWithGoogle(idToken: String) = unsupported<BoxLoreUser>()
        override suspend fun signInWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
        override suspend fun signUpWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
        override suspend fun sendMagicLink(email: String) = unsupported<Unit>()
        override suspend fun sendPasswordReset(email: String) = unsupported<Unit>()
        override suspend fun sendEmailVerification() = unsupported<Unit>()
        override suspend fun reloadUser() = unsupported<BoxLoreUser?>()
        override fun signOut() = Unit
        override suspend fun deleteAccount() = unsupported<Unit>()
        override suspend fun getIdToken(forceRefresh: Boolean): String? = null

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    private val link = "https://boxlore.in/__/auth/links?link=https%3A%2F%2Fx%3FoobCode%3Dabc"

    private fun TestScope.signIn(repo: LinkAuthRepository, clock: () -> Long = { 0L }) = EmailLinkSignIn(repo, this, clock)

    @Test
    fun `a sign-in link shows signing in, then signed in, and runs onSignedIn once`() = runTest {
        val repo = LinkAuthRepository().apply { gate = CompletableDeferred() }
        val sut = signIn(repo)
        var signedInCalls = 0

        sut.signIn("me@example.com", link) { signedInCalls++ }
        advanceUntilIdle()
        assertEquals(EmailLinkSignInStatus.SigningIn, sut.status.value)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(EmailLinkSignInStatus.SignedIn, sut.status.value)
        assertEquals(1, signedInCalls)
    }

    @Test
    fun `a used or expired link is reported as expired with the address for a new link`() = runTest {
        val repo = LinkAuthRepository().apply { results += Result.failure(SignInLinkExpiredException()) }
        val sut = signIn(repo)

        sut.signIn("me@example.com", link)
        advanceUntilIdle()

        assertEquals(EmailLinkSignInStatus.Failed(expired = true, email = "me@example.com"), sut.status.value)
    }

    @Test
    fun `any other failure can be tried again with the same link`() = runTest {
        val repo = LinkAuthRepository().apply { results += Result.failure(IllegalStateException("offline")) }
        val sut = signIn(repo)

        sut.signIn("me@example.com", link)
        advanceUntilIdle()
        assertEquals(EmailLinkSignInStatus.Failed(expired = false, email = "me@example.com"), sut.status.value)

        sut.retry()
        advanceUntilIdle()
        assertEquals(EmailLinkSignInStatus.SignedIn, sut.status.value)
        assertEquals(listOf("me@example.com" to link, "me@example.com" to link), repo.calls)
    }

    @Test
    fun `the same link arriving again while signing in is not sent twice`() = runTest {
        val repo = LinkAuthRepository().apply { gate = CompletableDeferred() }
        val sut = signIn(repo)

        sut.signIn("me@example.com", link)
        advanceUntilIdle()
        sut.signIn("me@example.com", link)
        advanceUntilIdle()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.calls.size)
        assertEquals(EmailLinkSignInStatus.SignedIn, sut.status.value)
    }

    @Test
    fun `retry does nothing unless the last attempt failed`() = runTest {
        val repo = LinkAuthRepository()
        val sut = signIn(repo)
        sut.retry()
        advanceUntilIdle()
        assertTrue(repo.calls.isEmpty())

        sut.signIn("me@example.com", link)
        advanceUntilIdle()
        sut.retry()
        advanceUntilIdle()
        assertEquals(1, repo.calls.size)
    }

    @Test
    fun `dismiss clears a finished attempt but never one in flight`() = runTest {
        val repo = LinkAuthRepository().apply {
            results += Result.failure(IllegalStateException("offline"))
            gate = CompletableDeferred()
        }
        val sut = signIn(repo)

        sut.signIn("me@example.com", link)
        advanceUntilIdle()
        sut.dismiss()
        assertEquals(EmailLinkSignInStatus.SigningIn, sut.status.value)

        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        sut.dismiss()
        assertEquals(EmailLinkSignInStatus.Idle, sut.status.value)
    }

    @Test
    fun `account page requests always rise, even when the clock does not`() = runTest {
        var now = 1_000L
        val sut = signIn(LinkAuthRepository()) { now }
        assertEquals(0L, sut.accountPageRequests.value)

        sut.requestAccountPage()
        val first = sut.accountPageRequests.value
        sut.requestAccountPage()
        val second = sut.accountPageRequests.value
        now = 5L
        sut.requestAccountPage()

        assertEquals(1_000L, first)
        assertTrue(second > first)
        assertTrue(sut.accountPageRequests.value > second)
    }

    @Test
    fun `only real sign-in links are accepted`() = runTest {
        val sut = signIn(LinkAuthRepository())
        assertTrue(sut.isSignInLink(link))
        assertFalse(sut.isSignInLink("https://boxlore.in/share?type=podcast&id=1"))
    }

    @Test
    fun `Firebase used and expired link codes count as expired links`() {
        assertTrue(isUsedOrExpiredLinkError("ERROR_INVALID_ACTION_CODE"))
        assertTrue(isUsedOrExpiredLinkError("ERROR_EXPIRED_ACTION_CODE"))
        assertFalse(isUsedOrExpiredLinkError("ERROR_NETWORK_REQUEST_FAILED"))
        assertFalse(isUsedOrExpiredLinkError(null))
    }

    private companion object {
        fun user(email: String) = BoxLoreUser(uid = "uid", email = email, displayName = null, isEmailVerified = true)
    }
}
