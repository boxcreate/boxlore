package cx.aswin.boxlore.core.network.session

/** Result of asking Firebase App Check for a token. [reason] is a short, non-identifying category. */
sealed interface AppCheckResult {
    data class Token(val value: String) : AppCheckResult

    data class Failed(val reason: String) : AppCheckResult
}

/** Supplied by the app (this module has no Firebase dependency). Only `/auth/session` uses it. */
fun interface AppCheckTokenSource {
    suspend fun fetch(): AppCheckResult
}

/** Result of an invisible Turnstile run. [reason] is a short category such as `timeout` or `cancelled`. */
sealed interface TurnstileResult {
    data class Token(val value: String) : TurnstileResult

    data class Failed(val reason: String) : TurnstileResult

    companion object {
        /** [Failed.reason] when the listener skipped the check (Skip, Back, a swipe or a tap outside). */
        const val SKIPPED = "cancelled"
    }
}

/**
 * Supplied by the app's Turnstile host; it shows UI only if Turnstile asks for interaction. Sign-ins
 * use it when App Check can't vouch for the install, and routes that answer `verification_required`
 * use it to step a limited session up.
 */
fun interface TurnstileTokenSource {
    suspend fun solve(): TurnstileResult

    /**
     * Whether [solve] can run now: a host is on screen to run it (the app is in the foreground).
     * Waits up to [waitMs] for a host that is still coming up, as on a cold start. A source that
     * needs no host is always ready.
     */
    suspend fun ready(waitMs: Long): Boolean = true
}

/** A non-exportable hardware-backed P-256 key that binds renewals to this phone. */
interface DeviceKeySigner {
    /** Base64url SPKI of the public key, creating the key when needed; null when the phone can't. */
    fun publicKey(): String?

    /** Base64url DER SHA256withECDSA signature of [message]; null when the key is unavailable. */
    fun sign(message: String): String?
}

/** What survives a restart. [nextUpgradeAtMs] is local wall-clock time. */
data class StoredApiSession(
    val token: String? = null,
    val installId: String? = null,
    val clockOffsetSeconds: Long = 0L,
    val appCheckFailures: Int = 0,
    val nextUpgradeAtMs: Long = 0L,
)

interface ApiSessionStore {
    suspend fun load(): StoredApiSession

    suspend fun save(state: StoredApiSession)
}

/** Why an exchange ran; sent as the `trigger` analytics property. */
enum class ApiSessionTrigger(val wire: String) {
    Launch("launch"),
    Expired("expired"),
    Rejected("rejected"),
    Refresh("refresh"),
    Upgrade("upgrade"),
    StepUp("step_up"),
    Retry("retry"),
}

/** One `/auth/session` attempt, for `api_session_status` and `app_check_status`. */
data class ApiSessionExchangeEvent(
    val trigger: ApiSessionTrigger,
    val success: Boolean,
    val durationMs: Long,
    val attestation: ApiAttestation?,
    val limited: Boolean?,
    val appCheckAttempted: Boolean,
    val appCheckObtained: Boolean,
    val appCheckFailure: String?,
    val failureReason: String?,
    val serverReason: String?,
    /**
     * Turnstile in this exchange: `token` when one was sent, else why none was (a failure reason
     * such as `cancelled` or `timeout`, `no_host` in the background, `cooldown` after a recent
     * failure); null when the exchange didn't need one.
     */
    val turnstile: String? = null,
)

/** Renewal outcomes: `renewed`, `proof_unavailable` (signing failed) or `rejected` (the API refused it). */
enum class ApiSessionRenewOutcome(val wire: String) {
    Renewed("renewed"),
    ProofUnavailable("proof_unavailable"),
    Rejected("rejected"),
}

/** Step-up outcomes for `api_session_status`; a disabled step-up, or one with no host on screen, records nothing. */
enum class ApiSessionStepUpOutcome(val wire: String) {
    Passed("passed"),
    ChallengeFailed("challenge_failed"),
    NotAccepted("not_accepted"),
}

/** Analytics hooks; the app forwards them to `:core:analytics`. Called off the main thread. */
interface ApiSessionListener {
    fun onExchange(event: ApiSessionExchangeEvent) {
        // Optional hook: a listener overrides only the events it reports.
    }

    /**
     * One App Check attempt, whether or not an exchange then carried its token: [obtained], or the
     * short [failure] category. Never the token itself.
     */
    fun onAppCheck(obtained: Boolean, failure: String?) {
        // Optional hook: a listener overrides only the events it reports.
    }

    fun onRenewal(outcome: ApiSessionRenewOutcome, attestation: ApiAttestation) {
        // Optional hook: a listener overrides only the events it reports.
    }

    fun onStepUp(outcome: ApiSessionStepUpOutcome, reason: String?, durationMs: Long) {
        // Optional hook: a listener overrides only the events it reports.
    }

    companion object {
        val None = object : ApiSessionListener {}
    }
}

/** Analytics must never break a request or the app scope. */
internal inline fun ApiSessionListener.safely(event: ApiSessionListener.() -> Unit) {
    runCatching { event() }
}

/** The body and headers of one `POST /auth/session`. */
data class SessionExchangeRequest(
    val installId: String,
    val devicePublicKey: String?,
    val appCheckToken: String?,
    val turnstileToken: String?,
)

sealed interface SessionExchangeResult {
    data class Issued(val session: String, val attestation: ApiAttestation, val limited: Boolean, val reason: String?) : SessionExchangeResult

    /**
     * [reason] is `network`, `http_<status>` or `http_<status>_<error code>`; [code] is the API's
     * error code alone (such as [ApiErrorCodes.ATTESTATION_REQUIRED]) and [serverReason] the short
     * reason some refusals carry.
     */
    data class Failed(
        val reason: String,
        val retryAfterSeconds: Long? = null,
        val code: String? = null,
        val serverReason: String? = null,
    ) : SessionExchangeResult
}

fun interface SessionExchanger {
    suspend fun exchange(request: SessionExchangeRequest): SessionExchangeResult
}

/**
 * A watch or TV, which uses the API only once signed in: the account token a signed-in phone lent
 * it (`bwt1…`), traded for a paired session at `POST /auth/watch/session` ([PairedSessionTrader]).
 */
interface PairedSessionAccount {
    /** The account token the device holds, or null while it is signed out. */
    fun accountToken(): String?

    /** The API no longer takes [token], for [reason]: sign the device out. Called off the main thread. */
    fun onTokenRefused(token: String, reason: PairedTokenRefusal)
}

/** Why the API stopped taking a watch's or TV's account token when it was traded. */
enum class PairedTokenRefusal {
    /** 401 `watch_token_invalid`: given up, reset, another device's or unsigned. */
    Invalid,

    /**
     * 403 `attested_session_required`: the phone that lent it had passed no check (App Check or
     * Turnstile) then. Signing in again works once that phone has; asking it again at once doesn't.
     */
    UnattestedLender,
}

/** One `POST /auth/watch/session`: the [session] the request carries and its `X-Request-Proof` ([proof]). */
data class PairedSessionRequest(val accountToken: String, val session: String, val proof: String) {
    override fun toString(): String = "PairedSessionRequest(…)"
}

sealed interface PairedSessionResult {
    data class Issued(val session: String) : PairedSessionResult

    /** 401 `watch_token_invalid` or 403 `attested_session_required` ([reason]): the device is signed out. */
    data class TokenRefused(val reason: PairedTokenRefusal) : PairedSessionResult

    /**
     * The session the request carried was refused ([code], one of [ApiErrorCodes.SESSION_REJECTIONS],
     * a 400 [ApiErrorCodes.DEVICE_KEY_REQUIRED] or a 401 [ApiErrorCodes.PROOF_REQUIRED]): try once
     * more with another.
     */
    data class SessionRefused(val code: String) : PairedSessionResult

    /** 429, 503, no network or anything else: back off ([retryAfterSeconds] when the API said) and stay signed in. */
    data class Failed(val reason: String, val retryAfterSeconds: Long? = null) : PairedSessionResult
}

fun interface PairedSessionTrader {
    suspend fun trade(request: PairedSessionRequest): PairedSessionResult
}
