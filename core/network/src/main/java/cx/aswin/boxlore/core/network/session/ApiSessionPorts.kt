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
}

/** Supplied by the app's Turnstile host; it shows UI only if Turnstile asks for interaction. */
fun interface TurnstileTokenSource {
    suspend fun solve(): TurnstileResult
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
)

/** Renewal outcomes: `renewed`, `proof_unavailable` (signing failed) or `rejected` (the API refused it). */
enum class ApiSessionRenewOutcome(val wire: String) {
    Renewed("renewed"),
    ProofUnavailable("proof_unavailable"),
    Rejected("rejected"),
}

/** Step-up outcomes for `api_session_status`; a disabled step-up records nothing. */
enum class ApiSessionStepUpOutcome(val wire: String) {
    Passed("passed"),
    ChallengeFailed("challenge_failed"),
    NotAccepted("not_accepted"),
}

/** Analytics hooks; the app forwards them to `:core:analytics`. Called off the main thread. */
interface ApiSessionListener {
    fun onExchange(event: ApiSessionExchangeEvent) {}

    fun onRenewal(outcome: ApiSessionRenewOutcome, attestation: ApiAttestation) {}

    fun onStepUp(outcome: ApiSessionStepUpOutcome, reason: String?, durationMs: Long) {}

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

    /** [reason] is `network`, `http_<status>` or `http_<status>_<error code>`. */
    data class Failed(val reason: String, val retryAfterSeconds: Long? = null) : SessionExchangeResult
}

fun interface SessionExchanger {
    suspend fun exchange(request: SessionExchangeRequest): SessionExchangeResult
}
