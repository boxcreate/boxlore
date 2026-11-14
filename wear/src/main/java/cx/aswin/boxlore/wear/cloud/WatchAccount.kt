package cx.aswin.boxlore.wear.cloud

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.confirmed
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether this watch is signed in to the listener's account, as Settings shows it. */
sealed interface WatchAccountState {
    data object SignedOut : WatchAccountState

    data object SigningIn : WatchAccountState

    /** [account] is the account's name as the phone gave it; null when the phone had none to give. */
    data class SignedIn(val account: String?) : WatchAccountState
}

/** Why a tap on Sign in did not sign the watch in. */
enum class SignInProblem { PhoneAway, PhoneSignedOut, Failed }

/**
 * The listener's account on the watch. Signing in needs the phone once: it lends its sign-in over
 * the phone link, so nothing is typed on the watch. After that the watch syncs with the account by
 * itself.
 */
interface WatchAccount {
    val state: StateFlow<WatchAccountState>

    /** Why the last try did not work; null before a try, during one and after one that worked. */
    val problem: StateFlow<SignInProblem?>

    fun signIn()

    /** Signs this watch out. The shows and listening already on it stay. */
    fun signOut()
}

/** A watch build with no account: sample data, or no API address. */
object NoWatchAccount : WatchAccount {
    override val state: StateFlow<WatchAccountState> = MutableStateFlow(WatchAccountState.SignedOut)
    override val problem: StateFlow<SignInProblem?> = MutableStateFlow(null)

    override fun signIn() = Unit

    override fun signOut() = Unit
}

/** Where the watch keeps its account token and the account's name. */
internal interface WatchAccountStore {
    val token: String?
    val account: String?

    fun save(token: String, account: String?)

    fun clear()

    /**
     * Puts [renewed] in place of [used], keeping the account's name, and says whether it did. It
     * does nothing when [used] is no longer the stored token: the check and the write are one
     * step, so a sign-out in between cannot be undone by a late renewal.
     */
    fun replace(used: String, renewed: String): Boolean

    class InMemory(token: String? = null, account: String? = null) : WatchAccountStore {
        @get:Synchronized
        override var token: String? = token
            private set

        @get:Synchronized
        override var account: String? = account
            private set

        @Synchronized
        override fun save(token: String, account: String?) {
            this.token = token
            this.account = account
        }

        @Synchronized
        override fun clear() {
            token = null
            account = null
        }

        @Synchronized
        override fun replace(used: String, renewed: String): Boolean {
            if (token != used) return false
            token = renewed
            return true
        }
    }
}

internal class SharedPreferencesWatchAccountStore(private val preferences: SharedPreferences) : WatchAccountStore {
    override val token: String? get() = synchronized(this) { preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } }
    override val account: String? get() = synchronized(this) { preferences.getString(KEY_ACCOUNT, null) }

    // Every change goes through this object's lock, so a swap and a sign-out never interleave.
    override fun save(token: String, account: String?) = synchronized(this) { preferences.edit().putString(KEY_TOKEN, token).putString(KEY_ACCOUNT, account).apply() }

    override fun clear() = synchronized(this) { preferences.edit().clear().apply() }

    override fun replace(used: String, renewed: String): Boolean =
        synchronized(this) {
            if (preferences.getString(KEY_TOKEN, null) != used) return@synchronized false
            preferences.edit().putString(KEY_TOKEN, renewed).apply()
            true
        }

    companion object {
        const val FILE_NAME = "boxlore_watch_account"
        private const val KEY_TOKEN = "token"
        private const val KEY_ACCOUNT = "account"

        fun create(context: Context) = SharedPreferencesWatchAccountStore(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}

/** What a sign-in try leads to, from the phone's answer and the API's. */
internal object WatchSignInRules {
    /** The problem to show for the phone's [reply]; null when it carries a code to redeem. */
    fun problem(reply: WatchSignInReply?): SignInProblem? =
        when {
            reply == null -> SignInProblem.PhoneAway
            reply.status == WatchSignInReply.SIGNED_OUT -> SignInProblem.PhoneSignedOut
            reply.status == WatchSignInReply.OK && !reply.code.isNullOrBlank() -> null
            else -> SignInProblem.Failed
        }
}

internal class LiveWatchAccount(
    /** Asks the phone to sign this watch in; null when the phone is out of reach. */
    private val askPhone: suspend () -> WatchSignInReply?,
    private val api: WatchCloudApi,
    private val tokens: WatchAccountStore,
    private val store: WatchLibraryStore,
    /** Runs once the watch is signed in, to bring the account's library over. */
    private val onSignedIn: () -> Unit,
    private val scope: CoroutineScope,
) : WatchAccount {
    private val current = MutableStateFlow(if (tokens.token == null) WatchAccountState.SignedOut else WatchAccountState.SignedIn(tokens.account))
    private val lastProblem = MutableStateFlow<SignInProblem?>(null)

    // Counts sign-in tries and sign-outs, so a try that was overtaken by a sign-out changes nothing.
    private val turn = AtomicInteger()

    override val state: StateFlow<WatchAccountState> = current.asStateFlow()
    override val problem: StateFlow<SignInProblem?> = lastProblem.asStateFlow()

    override fun signIn() {
        if (!current.compareAndSet(WatchAccountState.SignedOut, WatchAccountState.SigningIn)) return
        lastProblem.value = null
        val mine = turn.incrementAndGet()
        scope.launch {
            val reply = askPhone()
            val token = if (WatchSignInRules.problem(reply) == null) api.redeem(reply?.code.orEmpty()) else null
            // Signed out while this was on its way: the listener's last word stands.
            if (turn.get() != mine) return@launch
            if (token == null) {
                lastProblem.value = WatchSignInRules.problem(reply) ?: SignInProblem.Failed
                current.value = WatchAccountState.SignedOut
                return@launch
            }
            tokens.save(token, reply?.account)
            // Everything still waiting here goes up to the account, and the first pull takes it all down.
            store.update { it.copy(cloudLinked = true, cloudAcked = 0L, cloudSince = 0L) }
            current.value = WatchAccountState.SignedIn(reply?.account)
            onSignedIn()
        }
    }

    override fun signOut() {
        val token = tokens.token
        forget()
        if (token != null) scope.launch { api.revoke(token) }
    }

    /**
     * Signed out, with nothing to give up. [refused] is the token the API stopped taking; a
     * refusal that arrives after the watch signed in afresh is about the old one and is ignored.
     */
    fun forget(refused: String? = null) {
        if (refused != null && tokens.token != refused) return
        turn.incrementAndGet()
        tokens.clear()
        store.update { it.copy(cloudLinked = false, cloudAcked = 0L, cloudSince = 0L).confirmed() }
        lastProblem.value = null
        current.value = WatchAccountState.SignedOut
    }
}
