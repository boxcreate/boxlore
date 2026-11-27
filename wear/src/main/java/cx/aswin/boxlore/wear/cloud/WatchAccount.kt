package cx.aswin.boxlore.wear.cloud

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.network.session.PairedSessionAccount
import cx.aswin.boxlore.core.network.session.PairedTokenRefusal
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.confirmed
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether this watch is signed in to the listener's account, as Settings shows it. */
sealed interface WatchAccountState {
    data object SignedOut : WatchAccountState

    data object SigningIn : WatchAccountState

    /** [account] is the account's name as the phone gave it; null when the phone had none to give. */
    data class SignedIn(val account: String?) : WatchAccountState
}

/**
 * Why a tap on Sign in did not sign the watch in. [CheckOnPhone]: the phone has to pass boxlore's
 * check before it can lend its sign-in (it was asked for one, or the API refused a sign-in it lent
 * without one).
 */
enum class SignInProblem { PhoneAway, PhoneSignedOut, CheckOnPhone, Failed }

/** Why the sign-in screen waits for a tap on Sign in instead of asking the phone by itself. */
internal enum class SignInHold(val wire: String, val problem: SignInProblem?) {
    /** The listener signed out here. */
    SignedOut("signed_out", null),

    /** The API stopped taking the watch's token. */
    Refused("refused", null),

    /** The phone needs its check before it can lend its sign-in; asking it again at once won't help. */
    CheckOnPhone("check_on_phone", SignInProblem.CheckOnPhone),
    ;

    companion object {
        fun fromWire(value: String?): SignInHold? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The listener's account on the watch. Signing in needs the phone once: it lends its sign-in over
 * the phone link, so nothing is typed on the watch. After that the watch syncs with the account by
 * itself.
 */
interface WatchAccount {
    val state: StateFlow<WatchAccountState>

    /** Why the last try did not work; null before a try, during one and after one that worked. */
    val problem: StateFlow<SignInProblem?>

    /** The listener asked to sign in: asks the phone. */
    fun signIn()

    /**
     * The sign-in screen came to the front: asks the phone, unless the listener signed out here or
     * the API refused the last sign-in. Those wait for [signIn], so the phone never lends its sign-in
     * again by itself (each lending counts toward the account's daily limit).
     */
    fun signInBySelf()

    /** Signs this watch out. The shows and listening already on it stay. */
    fun signOut()
}

/** A watch build with no account: sample data, or no API address. */
object NoWatchAccount : WatchAccount {
    override val state: StateFlow<WatchAccountState> = MutableStateFlow(WatchAccountState.SignedOut)
    override val problem: StateFlow<SignInProblem?> = MutableStateFlow(null)

    override fun signIn() = Unit

    override fun signInBySelf() = Unit

    override fun signOut() = Unit
}

/** Where the watch keeps its account token and the account's name. */
internal interface WatchAccountStore {
    val token: String?
    val account: String?

    /** Tokens signed out here that the API has not confirmed giving up yet; sent again at the next start. */
    val unrevoked: Set<String>

    /** Why the sign-in screen waits for the listener; null when it may ask the phone by itself. Kept across restarts. */
    var hold: SignInHold?

    fun save(token: String, account: String?)

    /** Forgets the token and the account's name; [unrevoked] stays. */
    fun clear()

    fun keepUnrevoked(tokens: Set<String>)

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

        @get:Synchronized
        override var unrevoked: Set<String> = emptySet()
            private set

        @get:Synchronized
        @set:Synchronized
        override var hold: SignInHold? = null

        @Synchronized
        override fun keepUnrevoked(tokens: Set<String>) {
            unrevoked = tokens
        }

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
    override val unrevoked: Set<String> get() = synchronized(this) { preferences.getStringSet(KEY_UNREVOKED, null).orEmpty().toSet() }
    override var hold: SignInHold?
        get() = synchronized(this) { SignInHold.fromWire(preferences.getString(KEY_HOLD, null)) }
        set(value) = synchronized(this) { preferences.edit().apply { if (value == null) remove(KEY_HOLD) else putString(KEY_HOLD, value.wire) }.apply() }

    // Every change goes through this object's lock, so a swap and a sign-out never interleave.
    override fun save(token: String, account: String?) = synchronized(this) { preferences.edit().putString(KEY_TOKEN, token).putString(KEY_ACCOUNT, account).apply() }

    override fun clear() = synchronized(this) { preferences.edit().remove(KEY_TOKEN).remove(KEY_ACCOUNT).apply() }

    override fun keepUnrevoked(tokens: Set<String>) =
        synchronized(this) { preferences.edit().apply { if (tokens.isEmpty()) remove(KEY_UNREVOKED) else putStringSet(KEY_UNREVOKED, tokens) }.apply() }

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
        private const val KEY_UNREVOKED = "unrevoked"
        private const val KEY_HOLD = "sign_in_hold"

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
            reply.status == WatchSignInReply.CHECK_NEEDED -> SignInProblem.CheckOnPhone
            reply.status == WatchSignInReply.OK && !reply.code.isNullOrBlank() -> null
            else -> SignInProblem.Failed
        }
}

/**
 * The watch's account as its API session sees it ([PairedSessionAccount]): the stored token, traded
 * for a paired session, and a trade's refusal, which signs the watch out ([LiveWatchAccount.refused]).
 */
internal class WatchPairing(private val tokens: WatchAccountStore) : PairedSessionAccount {
    @Volatile
    var onRefused: (token: String, reason: PairedTokenRefusal) -> Unit = { _, _ -> }

    override fun accountToken(): String? = tokens.token

    override fun onTokenRefused(token: String, reason: PairedTokenRefusal) = onRefused(token, reason)
}

/**
 * Signs the watch in through its phone and out again. Nothing on the watch reaches boxlore's API
 * until it is signed in: [onAccountChanged] tells the API session each time the token comes or goes.
 * After a sign-out here, a refused token or a phone that needs its check, the watch asks the phone
 * again only when the listener taps Sign in ([WatchAccountStore.hold]).
 */
internal class LiveWatchAccount(
    /** Asks the phone to sign this watch in; null when the phone is out of reach. */
    private val askPhone: suspend () -> WatchSignInReply?,
    private val api: WatchCloudApi,
    private val tokens: WatchAccountStore,
    private val store: WatchLibraryStore,
    /** Runs once the watch is signed in, to bring the account's library over. */
    private val onSignedIn: () -> Unit,
    private val scope: CoroutineScope,
    /** Runs after the watch signed in or out, once the token store says so. */
    private val onAccountChanged: () -> Unit = {},
) : WatchAccount {
    private val current = MutableStateFlow(if (tokens.token == null) WatchAccountState.SignedOut else WatchAccountState.SignedIn(tokens.account))
    private val lastProblem = MutableStateFlow(tokens.hold?.problem)

    // Counts sign-in tries and sign-outs, so a try that was overtaken by a sign-out changes nothing.
    private val turn = AtomicInteger()
    private val revoking = Mutex()

    override val state: StateFlow<WatchAccountState> = current.asStateFlow()
    override val problem: StateFlow<SignInProblem?> = lastProblem.asStateFlow()

    override fun signIn() = ask(byListener = true)

    override fun signInBySelf() {
        if (tokens.hold == null) ask(byListener = false)
    }

    private fun ask(byListener: Boolean) {
        if (!current.compareAndSet(WatchAccountState.SignedOut, WatchAccountState.SigningIn)) return
        if (byListener) tokens.hold = null
        lastProblem.value = null
        val mine = turn.incrementAndGet()
        scope.launch {
            val reply = askPhone()
            val token = if (WatchSignInRules.problem(reply) == null) api.redeem(reply?.code.orEmpty()) else null
            // Signed out while this was on its way: the listener's last word stands.
            if (turn.get() != mine) return@launch
            if (token == null) {
                val problem = WatchSignInRules.problem(reply) ?: SignInProblem.Failed
                if (problem == SignInProblem.CheckOnPhone) tokens.hold = SignInHold.CheckOnPhone
                lastProblem.value = problem
                current.value = WatchAccountState.SignedOut
                return@launch
            }
            tokens.save(token, reply?.account)
            tokens.hold = null
            // Everything still waiting here goes up to the account, and the first pull takes it all down.
            store.update { it.copy(cloudLinked = true, cloudAcked = 0L, cloudSince = 0L) }
            current.value = WatchAccountState.SignedIn(reply?.account)
            onAccountChanged()
            onSignedIn()
        }
    }

    override fun signOut() {
        val token = tokens.token
        if (token != null) tokens.keepUnrevoked(tokens.unrevoked + token)
        forget(SignInHold.SignedOut)
        if (token != null) scope.launch { giveUp() }
    }

    /** Gives up every token signed out here that the API has not confirmed yet: at start, and after each sign-out. */
    fun giveUpUnrevoked() {
        if (tokens.unrevoked.isNotEmpty()) scope.launch { giveUp() }
    }

    private suspend fun giveUp() =
        revoking.withLock {
            for (token in tokens.unrevoked) {
                if (api.revoke(token)) tokens.keepUnrevoked(tokens.unrevoked - token)
            }
        }

    /**
     * The API stopped taking [refused], at a sync or a trade ([reason]): signed out, with nothing to
     * give up, and waiting for the listener. A refusal that arrives after the watch signed in
     * afresh is about the old token and is ignored.
     */
    fun refused(refused: String, reason: PairedTokenRefusal = PairedTokenRefusal.Invalid) {
        if (tokens.token != refused) return
        forget(if (reason == PairedTokenRefusal.UnattestedLender) SignInHold.CheckOnPhone else SignInHold.Refused)
    }

    private fun forget(hold: SignInHold) {
        turn.incrementAndGet()
        tokens.clear()
        // Kept before the sign-in screen comes up, so it doesn't ask the phone by itself.
        tokens.hold = hold
        store.update { it.copy(cloudLinked = false, cloudAcked = 0L, cloudSince = 0L).confirmed() }
        lastProblem.value = hold.problem
        current.value = WatchAccountState.SignedOut
        onAccountChanged()
    }
}
