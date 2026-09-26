package cx.aswin.boxlore.core.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import cx.aswin.boxlore.core.model.BoxLoreUser
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val pendingEmailStore: PendingEmailStore? = null,
    private val magicLinkUrl: String = MAGIC_LINK_DEFAULT_URL,
    private val packageName: String = PACKAGE_NAME_DEFAULT,
    private val onPreDeleteAccount: (suspend () -> Unit)? = null,
) : AuthRepository {

    private val _currentUser = MutableStateFlow(auth.currentUser.toBoxLoreUser())
    override val currentUser: StateFlow<BoxLoreUser?> = _currentUser.asStateFlow()

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser.toBoxLoreUser()
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<BoxLoreUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).awaitTask()
        authResult.user.toBoxLoreUser() ?: error("User was null after Google sign-in")
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = runCatching {
        val authResult = auth.signInWithEmailAndPassword(email, password).awaitTask()
        authResult.user.toBoxLoreUser() ?: error("User was null after email sign-in")
    }

    override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).awaitTask()
        authResult.user.toBoxLoreUser() ?: error("User was null after sign-up")
    }

    override suspend fun sendMagicLink(email: String): Result<Unit> = runCatching {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl(magicLinkUrl)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(packageName, true, null)
            .build()
        auth.sendSignInLinkToEmail(email, actionCodeSettings).awaitTask()
        pendingEmailStore?.setPendingEmail(email)
        Unit
    }.onFailure {
        pendingEmailStore?.setPendingEmail(null)
    }

    override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> = runCatching {
        try {
            val authResult = auth.signInWithEmailLink(email, emailLink).awaitTask()
            authResult.user.toBoxLoreUser() ?: error("User was null after email link sign-in")
        } finally {
            pendingEmailStore?.setPendingEmail(null)
        }
    }

    override fun isSignInWithEmailLink(link: String): Boolean = auth.isSignInWithEmailLink(link)

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).awaitTask()
    }

    override suspend fun sendEmailVerification(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user to verify")
        user.sendEmailVerification().awaitTask()
    }.map { }

    override suspend fun reloadUser(): Result<BoxLoreUser?> = runCatching {
        val user = auth.currentUser ?: return@runCatching null
        user.reload().awaitTask()
        val updatedUser = auth.currentUser.toBoxLoreUser()
        _currentUser.value = updatedUser
        updatedUser
    }

    override fun signOut() {
        auth.signOut()
        pendingEmailStore?.setPendingEmail(null)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user to delete")
        try {
            onPreDeleteAccount?.invoke()
            user.delete().awaitTask()
            pendingEmailStore?.setPendingEmail(null)
        } catch (e: Exception) {
            if (isRecentLoginError(e)) {
                throw RecentLoginRequiredException(e.message, e)
            }
            throw e
        }
    }

    private fun isRecentLoginError(e: Exception): Boolean {
        if (e is FirebaseAuthRecentLoginRequiredException) return true
        val msg = e.message ?: return false
        return RECENT_LOGIN_KEYWORDS.any { msg.contains(it, ignoreCase = true) }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = runCatching {
        val user = auth.currentUser ?: return null
        user.getIdToken(forceRefresh).awaitTask().token
    }.getOrNull()

    companion object {
        const val MAGIC_LINK_DEFAULT_URL = "https://aswin.cx/boxlore/auth"
        const val PACKAGE_NAME_DEFAULT = "cx.aswin.boxlore"
        private val RECENT_LOGIN_KEYWORDS = listOf(
            "recent-login",
            "requires-recent-login",
            "credential_too_old",
            "401",
        )
    }
}

private fun FirebaseUser?.toBoxLoreUser(): BoxLoreUser? =
    this?.let {
        val provider = it.providerData.firstOrNull { p -> p.providerId != "firebase" }?.providerId
            ?: it.providerId
        BoxLoreUser(
            uid = it.uid,
            email = it.email,
            displayName = it.displayName,
            isEmailVerified = it.isEmailVerified,
            isAnonymous = it.isAnonymous,
            providerId = provider,
        )
    }

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result)
        } else {
            cont.resumeWithException(
                task.exception ?: RuntimeException("Task failed with an unknown error"),
            )
        }
    }
}
