package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for authentication operations (Google, Email/Password, and Magic Link).
 */
interface AuthRepository {
    val currentUser: StateFlow<BoxLoreUser?>
    val currentUserId: String?

    suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser>
    suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser>
    suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser>
    suspend fun sendMagicLink(email: String): Result<Unit>
    suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser>
    fun isSignInWithEmailLink(link: String): Boolean
    suspend fun sendPasswordReset(email: String): Result<Unit>
    fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}
