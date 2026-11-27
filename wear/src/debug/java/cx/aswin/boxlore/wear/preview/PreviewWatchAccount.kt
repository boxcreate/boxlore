package cx.aswin.boxlore.wear.preview

import cx.aswin.boxlore.wear.cloud.SignInProblem
import cx.aswin.boxlore.wear.cloud.WatchAccount
import cx.aswin.boxlore.wear.cloud.WatchAccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sample shows' account: signed in from the start, so every screen can be looked at. After Sign
 * out the sign-in screen waits for a tap, as a real watch does, and Sign in works at once.
 */
internal class PreviewWatchAccount : WatchAccount {
    private val current = MutableStateFlow<WatchAccountState>(WatchAccountState.SignedIn(SAMPLE_ACCOUNT))
    override val state: StateFlow<WatchAccountState> = current.asStateFlow()
    override val problem: StateFlow<SignInProblem?> = MutableStateFlow(null)

    override fun signIn() {
        current.value = WatchAccountState.SignedIn(SAMPLE_ACCOUNT)
    }

    override fun signInBySelf() = Unit

    override fun signOut() {
        current.value = WatchAccountState.SignedOut
    }

    private companion object {
        const val SAMPLE_ACCOUNT = "listener@example.com" // i18n-ignore
    }
}
