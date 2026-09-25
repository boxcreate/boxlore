package cx.aswin.boxlore.core.auth

import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthRepositoryContractTest {

    private class TestAuthRepository(
        initialUser: BoxLoreUser? = null,
    ) : AuthRepository {
        private val _currentUser = MutableStateFlow(initialUser)
        override val currentUser: StateFlow<BoxLoreUser?> = _currentUser.asStateFlow()
        override val currentUserId: String? get() = _currentUser.value?.uid

        var emailVerificationSentCount = 0
        var reloadCount = 0
        var shouldFailVerification = false
        var shouldFailReload = false
        var nextReloadUser: BoxLoreUser? = null

        override suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser> =
            Result.failure(UnsupportedOperationException())

        override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> {
            val user = BoxLoreUser(uid = "uid-$email", email = email, displayName = null, isEmailVerified = true)
            _currentUser.value = user
            return Result.success(user)
        }

        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> {
            val user = BoxLoreUser(uid = "uid-$email", email = email, displayName = null, isEmailVerified = false)
            _currentUser.value = user
            return Result.success(user)
        }

        override suspend fun sendMagicLink(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> =
            Result.failure(UnsupportedOperationException())

        override fun isSignInWithEmailLink(link: String): Boolean = false

        override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun sendEmailVerification(): Result<Unit> {
            if (_currentUser.value == null) {
                return Result.failure(IllegalStateException("No authenticated user to verify"))
            }
            if (shouldFailVerification) {
                return Result.failure(RuntimeException("Verification send failed"))
            }
            emailVerificationSentCount++
            return Result.success(Unit)
        }

        override suspend fun reloadUser(): Result<BoxLoreUser?> {
            val current = _currentUser.value ?: return Result.success(null)
            if (shouldFailReload) {
                return Result.failure(RuntimeException("Network error on reload"))
            }
            reloadCount++
            val updated = nextReloadUser ?: current.copy(isEmailVerified = true)
            _currentUser.value = updated
            return Result.success(updated)
        }

        override fun signOut() {
            _currentUser.value = null
        }

        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)

        override suspend fun getIdToken(forceRefresh: Boolean): String? = "test-token"
    }

    @Test
    fun `signUp creates unverified user and sendEmailVerification succeeds`() = runTest {
        val repo = TestAuthRepository()
        assertNull(repo.currentUser.value)

        val signUpResult = repo.signUpWithEmailPassword("listener@boxlore.example", "pass123")
        assertTrue(signUpResult.isSuccess)
        val user = signUpResult.getOrThrow()
        assertFalse(user.isEmailVerified)
        assertEquals("listener@boxlore.example", user.email)
        assertEquals(user, repo.currentUser.value)

        val verifyResult = repo.sendEmailVerification()
        assertTrue(verifyResult.isSuccess)
        assertEquals(1, repo.emailVerificationSentCount)
    }

    @Test
    fun `sendEmailVerification fails when no user is signed in`() = runTest {
        val repo = TestAuthRepository(initialUser = null)
        val result = repo.sendEmailVerification()
        assertTrue(result.isFailure)
        assertEquals(0, repo.emailVerificationSentCount)
    }

    @Test
    fun `reloadUser updates currentUser stateFlow and returns updated user`() = runTest {
        val initialUser = BoxLoreUser(
            uid = "user-1",
            email = "user@boxlore.example",
            displayName = null,
            isEmailVerified = false,
        )
        val repo = TestAuthRepository(initialUser = initialUser)
        assertFalse(repo.currentUser.value!!.isEmailVerified)

        val reloadResult = repo.reloadUser()
        assertTrue(reloadResult.isSuccess)
        val updated = reloadResult.getOrNull()
        assertNotNull(updated)
        assertTrue(updated!!.isEmailVerified)
        assertTrue(repo.currentUser.value!!.isEmailVerified)
        assertEquals(1, repo.reloadCount)
    }

    @Test
    fun `reloadUser returns null when user is signed out`() = runTest {
        val repo = TestAuthRepository(initialUser = null)
        val reloadResult = repo.reloadUser()
        assertTrue(reloadResult.isSuccess)
        assertNull(reloadResult.getOrNull())
    }

    @Test
    fun `reloadUser failure returns failure result without updating user`() = runTest {
        val initialUser = BoxLoreUser(
            uid = "user-1",
            email = "user@boxlore.example",
            displayName = null,
            isEmailVerified = false,
        )
        val repo = TestAuthRepository(initialUser = initialUser)
        repo.shouldFailReload = true

        val reloadResult = repo.reloadUser()
        assertTrue(reloadResult.isFailure)
        assertFalse(repo.currentUser.value!!.isEmailVerified)
    }
}
