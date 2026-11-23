package cx.aswin.boxlore.tv.preview

import cx.aswin.boxlore.tv.cloud.TvAccount
import cx.aswin.boxlore.tv.cloud.TvSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sign-in for the sample shows: it shows a made-up code that no phone can approve. */
internal class PreviewTvAccount : TvAccount {
    private val state = MutableStateFlow<TvSignIn>(TvSignIn.SignedOut)
    override val signIn: StateFlow<TvSignIn> = state.asStateFlow()

    override fun startSignIn() {
        state.value = TvSignIn.ShowingCode(SAMPLE_CODE)
    }

    override fun cancelSignIn() {
        state.value = TvSignIn.SignedOut
    }

    override fun signOut() {
        state.value = TvSignIn.SignedOut
    }

    private companion object {
        const val SAMPLE_CODE = "K7QX4M2P"
    }
}
