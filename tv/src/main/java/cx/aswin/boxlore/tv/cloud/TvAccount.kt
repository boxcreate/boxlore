package cx.aswin.boxlore.tv.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cx.aswin.boxlore.core.network.model.TvSignInStartResponse
import cx.aswin.boxlore.tv.data.TvLibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where signing this TV in stands, as Settings shows it. */
sealed interface TvSignIn {
    data object SignedOut : TvSignIn

    /** Asking the API for a code. */
    data object Starting : TvSignIn

    /** Showing [code] for a phone to approve; a new code replaces it when it runs out. */
    data class ShowingCode(val code: String) : TvSignIn

    /** Signed in to the account named [account] (its email), or to an account the TV cannot name. */
    data class SignedIn(val account: String?) : TvSignIn

    /** The API could not be reached for a code. */
    data object NoConnection : TvSignIn
}

/** Signing this TV in to the listener's account and out again. */
interface TvAccount {
    val signIn: StateFlow<TvSignIn>

    /** Shows a code and waits for a phone to approve it, until signed in or [cancelSignIn]. */
    fun startSignIn()

    /** Stops waiting for a code; a TV signed in stays signed in. */
    fun cancelSignIn()

    fun signOut()
}

/** A TV with no API: it can never sign in. */
class NoTvAccount : TvAccount {
    override val signIn: StateFlow<TvSignIn> = MutableStateFlow(TvSignIn.NoConnection)

    override fun startSignIn() = Unit

    override fun cancelSignIn() = Unit

    override fun signOut() = Unit
}

/** Where the TV keeps its account token. */
interface TvTokenStore {
    var token: String?

    /** The signed-in account's email, as the API named it at sign-in. */
    var account: String?

    /** Keeps a renewed token, unless the TV signed out or in again since [used] was sent. */
    fun replace(used: String, renewed: String)
}

/** The TV's account token, kept in its own preferences file. */
class TvAccountStore(private val prefs: SharedPreferences) : TvTokenStore {
    override var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value) }

    override var account: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, value) }

    @Synchronized
    override fun replace(used: String, renewed: String) {
        if (token == used) token = renewed
    }

    companion object {
        /** Stable across releases: renaming it signs every TV out. */
        const val FILE_NAME = "boxlore_tv_account"
        private const val KEY_TOKEN = "token"
        private const val KEY_ACCOUNT = "account"

        fun create(context: Context): TvAccountStore = TvAccountStore(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}

/**
 * Signs the TV in the way TVs usually do: it shows a short code, the listener enters it in boxlore
 * on a phone signed in to their account, and the TV gets its own account token. Then the cloud
 * sync keeps the TV's library and the account the same.
 */
internal class LiveTvAccount(
    private val api: TvCloudApi,
    private val tokens: TvTokenStore,
    private val store: TvLibraryStore,
    /** Asks for a sync soon. */
    private val requestSync: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : TvAccount {
    private val state = MutableStateFlow(if (tokens.token != null) TvSignIn.SignedIn(tokens.account) else TvSignIn.SignedOut)
    private var waiting: Job? = null

    override val signIn: StateFlow<TvSignIn> = state.asStateFlow()

    override fun startSignIn() {
        if (tokens.token != null) return
        waiting?.cancel()
        waiting =
            scope.launch {
                while (isActive) {
                    state.value = TvSignIn.Starting
                    val started = api.start()
                    if (started == null) {
                        state.value = TvSignIn.NoConnection
                        return@launch
                    }
                    state.value = TvSignIn.ShowingCode(started.code)
                    // A code that runs out with nobody approving it gives way to a fresh one.
                    val approved = approval(started) ?: continue
                    return@launch signedIn(approved.token, approved.account)
                }
            }
    }

    /** Asks about [started] until a phone approves it, or null once it runs out. */
    private suspend fun approval(started: TvSignInStartResponse): CodeCheck.Approved? {
        val runsOutAt = clock() + started.expiresInSeconds.coerceAtLeast(MIN_LIFE_SECONDS) * MILLIS
        val everyMs = started.intervalSeconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS) * MILLIS
        while (clock() < runsOutAt) {
            delay(everyMs)
            when (val check = api.poll(started.code, started.pair)) {
                is CodeCheck.Approved -> return check
                CodeCheck.Expired -> return null
                else -> Unit
            }
        }
        return null
    }

    override fun cancelSignIn() {
        waiting?.cancel()
        waiting = null
        if (state.value !is TvSignIn.SignedIn) state.value = TvSignIn.SignedOut
    }

    override fun signOut() {
        val token = tokens.token ?: return
        scope.launch { api.revoke(token) }
        signedOut()
    }

    /** The API stopped taking [refused]: the TV is signed out, unless it signed in again since. */
    fun refused(refused: String) {
        if (tokens.token == refused) signedOut()
    }

    private fun signedIn(token: String, account: String?) {
        tokens.token = token
        tokens.account = account
        store.update(TvCloudRules::linked)
        state.value = TvSignIn.SignedIn(account)
        requestSync()
    }

    private fun signedOut() {
        tokens.token = null
        tokens.account = null
        store.update(TvCloudRules::unlinked)
        state.value = TvSignIn.SignedOut
    }

    private companion object {
        const val MILLIS = 1_000L
        const val MIN_LIFE_SECONDS = 60L
        const val MIN_POLL_SECONDS = 3L
        const val MAX_POLL_SECONDS = 15L
    }
}
