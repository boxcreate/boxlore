package cx.aswin.boxlore.core.network

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
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
        authResult.user.toBoxLoreUser() ?: throw IllegalStateException("User was null after Google sign-in")
    }

    override suspend fun signInWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = runCatching {
        val authResult = auth.signInWithEmailAndPassword(email, password).awaitTask()
        authResult.user.toBoxLoreUser() ?: throw IllegalStateException("User was null after email sign-in")
    }

    override suspend fun signUpWithEmailPassword(email: String, password: String): Result<BoxLoreUser> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).awaitTask()
        authResult.user.toBoxLoreUser() ?: throw IllegalStateException("User was null after sign-up")
    }

    override suspend fun sendMagicLink(email: String): Result<Unit> = runCatching {
        pendingEmailStore?.setPendingEmail(email)
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl(magicLinkUrl)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(packageName, true, null)
            .build()
        auth.sendSignInLinkToEmail(email, actionCodeSettings).awaitTask()
    }

    override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<BoxLoreUser> = runCatching {
        val authResult = auth.signInWithEmailLink(email, emailLink).awaitTask()
        pendingEmailStore?.setPendingEmail(null)
        authResult.user.toBoxLoreUser() ?: throw IllegalStateException("User was null after email link sign-in")
    }

    override fun isSignInWithEmailLink(link: String): Boolean = auth.isSignInWithEmailLink(link)

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).awaitTask()
    }

    override fun signOut() {
        auth.signOut()
        pendingEmailStore?.setPendingEmail(null)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("No authenticated user to delete")
        user.delete().awaitTask()
        pendingEmailStore?.setPendingEmail(null)
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? = runCatching {
        val user = auth.currentUser ?: return null
        user.getIdToken(forceRefresh).awaitTask().token
    }.getOrNull()

    companion object {
        const val MAGIC_LINK_DEFAULT_URL = "https://aswin.cx/boxlore/auth"
        const val PACKAGE_NAME_DEFAULT = "cx.aswin.boxlore"
    }
}

private fun FirebaseUser?.toBoxLoreUser(): BoxLoreUser? =
    this?.let {
        BoxLoreUser(
            uid = it.uid,
            email = it.email,
            displayName = it.displayName,
            isEmailVerified = it.isEmailVerified,
            isAnonymous = it.isAnonymous,
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
