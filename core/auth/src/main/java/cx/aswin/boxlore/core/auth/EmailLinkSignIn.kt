package cx.aswin.boxlore.core.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where signing in from an emailed link stands, for the Account page. */
sealed interface EmailLinkSignInStatus {
    /** No link is being handled. */
    data object Idle : EmailLinkSignInStatus

    /** The link is being checked. */
    data object SigningIn : EmailLinkSignInStatus

    /** Signed in. The Account page shows the first sync from here. */
    data object SignedIn : EmailLinkSignInStatus

    /**
     * The link didn't sign the listener in. [expired]: it was already used or has expired.
     * [email] is the address it was sent to, for sending a new link.
     */
    data class Failed(val expired: Boolean, val email: String) : EmailLinkSignInStatus
}

/**
 * Signs in from emailed links and reports progress. App-scoped, so a sign-in in flight survives
 * activity recreation, rotation, folding and window resizes.
 */
class EmailLinkSignIn(
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Attempt(val email: String, val link: String)

    private val _status = MutableStateFlow<EmailLinkSignInStatus>(EmailLinkSignInStatus.Idle)
    val status: StateFlow<EmailLinkSignInStatus> = _status.asStateFlow()

    private val _accountPageRequests = MutableStateFlow(0L)

    /**
     * Rises with each sign-in link that arrives (0 = none yet). The UI opens the Account page when this
     * is above the last value it handled. Values are time-based, so a request after the process was
     * recreated is still newer than one the UI saved before.
     */
    val accountPageRequests: StateFlow<Long> = _accountPageRequests.asStateFlow()

    // Written on the caller's thread, read when the sign-in returns on [scope].
    @Volatile private var lastAttempt: Attempt? = null

    fun isSignInLink(link: String): Boolean = authRepository.isSignInWithEmailLink(link)

    /** A sign-in link arrived: the Account page should open to show what happens next. */
    fun requestAccountPage() {
        _accountPageRequests.update { maxOf(it + 1, clock()) }
    }

    /** Signs in with [link] for [email]. [onSignedIn] runs once, after the sign-in succeeds. */
    fun signIn(email: String, link: String, onSignedIn: () -> Unit = {}) {
        val attempt = Attempt(email, link)
        if (_status.value == EmailLinkSignInStatus.SigningIn && lastAttempt == attempt) return
        lastAttempt = attempt
        _status.value = EmailLinkSignInStatus.SigningIn
        scope.launch {
            val result = authRepository.signInWithEmailLink(email, link)
            if (lastAttempt != attempt) return@launch
            _status.value = result.fold(
                onSuccess = {
                    onSignedIn()
                    EmailLinkSignInStatus.SignedIn
                },
                onFailure = { EmailLinkSignInStatus.Failed(expired = it is SignInLinkExpiredException, email = email) },
            )
        }
    }

    /** Tries the last link again after a failure (for example, after the connection came back). */
    fun retry() {
        val attempt = lastAttempt ?: return
        if (_status.value is EmailLinkSignInStatus.Failed) signIn(attempt.email, attempt.link)
    }

    /** Clears a finished attempt so the Account page goes back to its usual content. */
    fun dismiss() {
        if (_status.value != EmailLinkSignInStatus.SigningIn) _status.value = EmailLinkSignInStatus.Idle
    }
}
