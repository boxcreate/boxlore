package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.flow.MutableStateFlow

/** An [AuthRepository] whose signed-in user a test can change; every network call fails. */
internal class SignedInAuthRepository(user: BoxLoreUser?) : AuthRepository {
    override val currentUser = MutableStateFlow(user)
    override val currentUserId: String? get() = currentUser.value?.uid

    override suspend fun sendMagicLink(email: String) = unsupported<Unit>()
    override suspend fun signInWithGoogle(idToken: String) = unsupported<BoxLoreUser>()
    override suspend fun signInWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
    override suspend fun signUpWithEmailPassword(email: String, password: String) = unsupported<BoxLoreUser>()
    override suspend fun signInWithEmailLink(email: String, emailLink: String) = unsupported<BoxLoreUser>()
    override fun isSignInWithEmailLink(link: String): Boolean = false
    override suspend fun sendPasswordReset(email: String) = unsupported<Unit>()
    override suspend fun sendEmailVerification() = unsupported<Unit>()
    override suspend fun reloadUser() = unsupported<BoxLoreUser?>()
    override fun signOut() = Unit
    override suspend fun deleteAccount() = unsupported<Unit>()
    override suspend fun getIdToken(forceRefresh: Boolean): String? = null

    private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
}
