package cx.aswin.boxlore.tv.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cx.aswin.boxlore.core.network.model.TvSignInStartResponse
import cx.aswin.boxlore.core.network.session.PairedSessionAccount
import cx.aswin.boxlore.core.network.session.PairedTokenRefusal
import cx.aswin.boxlore.tv.data.TvLibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where signing this TV in stands. Every state but [SignedIn] is the sign-in screen. */
sealed interface TvSignIn {
    /** Not signed in, and not asking for a code (the sign-in screen is not on screen). */
    data object SignedOut : TvSignIn

    /** Asking the API for a code. */
    data object Starting : TvSignIn

    /** Showing [code] for a phone to approve; a new code replaces it when it runs out. */
    data class ShowingCode(val code: String) : TvSignIn

    /** Several codes ran out with nobody entering one: the TV stopped asking until the listener wants a new one. */
    data object CodeRanOut : TvSignIn

    /** Signed in to the account named [account] (its email), or to an account the TV cannot name. */
    data class SignedIn(val account: String?) : TvSignIn

    /** The API could not be reached for a code. */
    data object NoConnection : TvSignIn
}

/** Signing this TV in to the listener's account and out again. The TV uses boxlore only once signed in. */
interface TvAccount {
    val signIn: StateFlow<TvSignIn>

    /**
     * Shows a code and waits for a phone to approve it, until signed in or [cancelSignIn]. A code
     * that runs out gives way to a new one, a few times, then [TvSignIn.CodeRanOut]; calling this
     * again starts over.
     */
    fun startSignIn()

    /** Stops waiting for a code (the sign-in screen left the screen); a TV signed in stays signed in. */
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

    /** Tokens signed out here that the API has not confirmed giving up yet; sent again at the next start. */
    var unrevoked: Set<String>

    /** Keeps a renewed token, unless the TV signed out or in again since [used] was sent. */
    fun replace(used: String, renewed: String)
}

/** The TV's account token, kept in its own preferences file. */
class TvAccountStore(private val prefs: SharedPreferences) : TvTokenStore {
    // The setter takes the same lock as replace: a sign-out can never land between its check and its write.
    override var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = synchronized(this) { prefs.edit { if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value) } }

    override var account: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_ACCOUNT) else putString(KEY_ACCOUNT, value) }

    override var unrevoked: Set<String>
        get() = prefs.getStringSet(KEY_UNREVOKED, null).orEmpty().toSet()
        set(value) = synchronized(this) { prefs.edit { if (value.isEmpty()) remove(KEY_UNREVOKED) else putStringSet(KEY_UNREVOKED, value) } }

    @Synchronized
    override fun replace(used: String, renewed: String) {
        if (token == used) token = renewed
    }

    companion object {
        /** Stable across releases: renaming it signs every TV out. */
        const val FILE_NAME = "boxlore_tv_account"
        private const val KEY_TOKEN = "token"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_UNREVOKED = "unrevoked"

        fun create(context: Context): TvAccountStore = TvAccountStore(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}

/**
 * The TV's account as its API session sees it ([PairedSessionAccount]): the stored token, traded for
 * a paired session, and a trade's refusal, which signs the TV out ([LiveTvAccount.refused]).
 */
internal class TvPairing(private val tokens: TvTokenStore) : PairedSessionAccount {
    @Volatile
    var onRefused: (token: String, reason: PairedTokenRefusal) -> Unit = { _, _ -> }

    override fun accountToken(): String? = tokens.token

    override fun onTokenRefused(token: String, reason: PairedTokenRefusal) = onRefused(token, reason)
}

/**
 * Signs the TV in the way TVs usually do: it shows a short code, the listener enters it in boxlore
 * on a phone signed in to their account, and the TV gets its own account token. Then the cloud
 * sync keeps the TV's library and the account the same. Nothing else on the TV reaches boxlore's
 * API until then: [onAccountChanged] tells the API session each time the token comes or goes.
 */
internal class LiveTvAccount(
    private val api: TvCloudApi,
    private val tokens: TvTokenStore,
    private val store: TvLibraryStore,
    /** Asks for a sync soon. */
    private val requestSync: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs after the TV signed in or out, once the token store says so. */
    private val onAccountChanged: () -> Unit = {},
) : TvAccount {
    private val state = MutableStateFlow(if (tokens.token != null) TvSignIn.SignedIn(tokens.account) else TvSignIn.SignedOut)
    private var waiting: Job? = null
    private val revoking = Mutex()

    override val signIn: StateFlow<TvSignIn> = state.asStateFlow()

    override fun startSignIn() {
        if (tokens.token != null) return
        waiting?.cancel()
        waiting =
            scope.launch {
                // A code that runs out with nobody approving it gives way to a fresh one, a few times.
                repeat(MAX_CODES) {
                    state.value = TvSignIn.Starting
                    val started = code()
                    if (started == null) {
                        state.value = TvSignIn.NoConnection
                        return@launch
                    }
                    state.value = TvSignIn.ShowingCode(started.code)
                    val approved = approval(started)
                    if (approved != null) return@launch signedIn(approved.token, approved.account)
                }
                state.value = TvSignIn.CodeRanOut
            }
    }

    /** A code to show, waiting out the API's limit once when too many were asked for lately; null when it can't be had. */
    private suspend fun code(): TvSignInStartResponse? {
        repeat(START_TRIES) {
            when (val start = api.start()) {
                is CodeStart.Code -> return start.code
                is CodeStart.Wait -> delay(start.seconds * MILLIS)
                CodeStart.Unreachable -> return null
            }
        }
        return null
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
                // Asked too often from this address: the code still stands, so wait as long as the API says.
                is CodeCheck.Wait -> delay((check.seconds * MILLIS - everyMs).coerceAtLeast(0L))
                else -> Unit
            }
        }
        return null
    }

    override fun cancelSignIn() {
        waiting?.cancel()
        waiting = null
        // One step, so an approval landing meanwhile is never overwritten.
        state.update { if (it is TvSignIn.SignedIn) it else TvSignIn.SignedOut }
    }

    override fun signOut() {
        val token = tokens.token ?: return
        tokens.unrevoked = tokens.unrevoked + token
        signedOut()
        scope.launch { giveUp() }
    }

    /** Gives up every token signed out here that the API has not confirmed yet: at start, and after each sign-out. */
    fun giveUpUnrevoked() {
        if (tokens.unrevoked.isNotEmpty()) scope.launch { giveUp() }
    }

    private suspend fun giveUp() =
        revoking.withLock {
            for (token in tokens.unrevoked) {
                if (api.revoke(token)) tokens.unrevoked = tokens.unrevoked - token
            }
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
        onAccountChanged()
        requestSync()
    }

    private fun signedOut() {
        tokens.token = null
        tokens.account = null
        store.update(TvCloudRules::unlinked)
        state.value = TvSignIn.SignedOut
        onAccountChanged()
    }

    internal companion object {
        /** Codes shown in a row before the TV stops asking: about half an hour of 10-minute codes. */
        const val MAX_CODES = 3
        private const val START_TRIES = 2
        private const val MILLIS = 1_000L
        private const val MIN_LIFE_SECONDS = 60L
        private const val MIN_POLL_SECONDS = 3L
        private const val MAX_POLL_SECONDS = 15L
    }
}
