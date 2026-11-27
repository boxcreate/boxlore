package cx.aswin.boxlore.tv.preview

import cx.aswin.boxlore.tv.cloud.TvAccount
import cx.aswin.boxlore.tv.cloud.TvSignIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sign-in for the sample shows: signed in from the start, so every screen can be looked at. After
 * Sign out it shows a made-up code, which approves itself after a few seconds.
 */
internal class PreviewTvAccount : TvAccount {
    private val state = MutableStateFlow<TvSignIn>(TvSignIn.SignedIn(SAMPLE_ACCOUNT))
    private val scope = MainScope()
    private var approving: Job? = null
    override val signIn: StateFlow<TvSignIn> = state.asStateFlow()

    override fun startSignIn() {
        if (state.value is TvSignIn.SignedIn) return
        state.value = TvSignIn.ShowingCode(SAMPLE_CODE)
        approving?.cancel()
        approving =
            scope.launch {
                delay(APPROVE_AFTER_MS)
                state.value = TvSignIn.SignedIn(SAMPLE_ACCOUNT)
            }
    }

    override fun cancelSignIn() {
        approving?.cancel()
        if (state.value !is TvSignIn.SignedIn) state.value = TvSignIn.SignedOut
    }

    override fun signOut() {
        state.value = TvSignIn.SignedOut
    }

    private companion object {
        const val SAMPLE_CODE = "K7QX4M2P"
        const val SAMPLE_ACCOUNT = "listener@example.com" // i18n-ignore
        const val APPROVE_AFTER_MS = 8_000L
    }
}
