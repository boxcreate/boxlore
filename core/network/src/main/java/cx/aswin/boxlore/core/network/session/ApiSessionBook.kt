package cx.aswin.boxlore.core.network.session

import java.security.SecureRandom
import java.util.Base64

/**
 * The manager's mutable state. Every member is called with the manager's lock held, so the class
 * itself carries no synchronization. Times: `*Ms` are local wall-clock milliseconds; session times
 * are API seconds, converted with [offsetSeconds] (API `iat` minus the local clock at issue).
 */
internal enum class RejectionStep { UseCurrent, Exchange, BackOff }

internal data class RejectionPlan(val step: RejectionStep, val refusedRenewal: ApiSessionToken?)

internal class ApiSessionBook(private val clock: () -> Long) {
    var token: ApiSessionToken? = null
    var offsetSeconds = 0L
    var installId: String? = null

    /** Consecutive App Check attempts that ended without an App Check session. */
    var appCheckFailures = 0
    var nextUpgradeAtMs = 0L

    var exchangeFailures = 0
    var nextExchangeAtMs = 0L
    var lastMaintenanceAtMs = 0L

    /** Local time the held token arrived; a rejection right after that is not worth another exchange. */
    var tokenReceivedAtMs = 0L

    private var proof: Pair<String, Pair<Long, String>>? = null
    private val reportedRenewals = mutableSetOf<String>()

    val serverNow: Long get() = clock() / 1_000L + offsetSeconds

    fun toLocalMs(serverSeconds: Long): Long = (serverSeconds - offsetSeconds) * 1_000L

    val usableToken: ApiSessionToken? get() = token?.takeIf { ApiSessionPolicy.isUsable(it, serverNow) }

    fun restore(stored: StoredApiSession) {
        token = ApiSessionToken.parse(stored.token)
        installId = stored.installId?.takeIf(INSTALL_ID::matches)
        offsetSeconds = stored.clockOffsetSeconds
        appCheckFailures = stored.appCheckFailures.coerceAtLeast(0)
        nextUpgradeAtMs = stored.nextUpgradeAtMs
    }

    fun snapshot(): StoredApiSession = StoredApiSession(token?.raw, installId, offsetSeconds, appCheckFailures, nextUpgradeAtMs)

    fun installIdOrCreate(): String = installId ?: newInstallId().also { installId = it }

    /** Holds [candidate] when it beats the current token; the API's `iat` also re-anchors the clock. */
    fun offer(candidate: ApiSessionToken): Boolean {
        offsetSeconds = candidate.issuedAt - clock() / 1_000L
        if (!ApiSessionPolicy.shouldReplace(token, candidate, serverNow)) return false
        hold(candidate)
        return true
    }

    fun hold(next: ApiSessionToken) {
        if (next.issuedAt > 0L) offsetSeconds = next.issuedAt - clock() / 1_000L
        token = next
        tokenReceivedAtMs = clock()
        proof = null
    }

    fun drop(raw: String?): Boolean {
        if (raw == null || token?.raw != raw) return false
        token = null
        proof = null
        return true
    }

    /**
     * The API refused [sentToken] with [code]. Drops it and says what to do next: retry with a token
     * another request already got, exchange for a new one, or back off because the refused token was
     * brand new (the API can't take sessions right now). Also names a refused renewal to report once.
     */
    fun planRejection(sentToken: String?, code: String, nowMs: Long): RejectionPlan {
        val held = token
        if (sentToken != null && held != null && held.raw != sentToken) return RejectionPlan(RejectionStep.UseCurrent, null)
        val refusedRenewal = held
            ?.takeIf { code == ApiErrorCodes.SESSION_EXPIRED && it.renewable }
            ?.takeIf { firstReport(it.sid, ApiSessionRenewOutcome.Rejected) }
        val justIssued = held != null && nowMs - tokenReceivedAtMs < ApiSessionPolicy.FRESH_TOKEN_SECONDS * 1_000L
        drop(sentToken)
        if (!justIssued) return RejectionPlan(RejectionStep.Exchange, refusedRenewal)
        recordExchangeFailure(null)
        return RejectionPlan(RejectionStep.BackOff, refusedRenewal)
    }

    fun recordAppCheck(attempted: Boolean, attestation: ApiAttestation) {
        if (!attempted) return
        if (attestation == ApiAttestation.AppCheck) {
            appCheckFailures = 0
            nextUpgradeAtMs = 0L
        } else {
            appCheckFailures += 1
            nextUpgradeAtMs = clock() + ApiSessionPolicy.upgradeDelayMs(appCheckFailures)
        }
    }

    fun recordExchangeSuccess() {
        exchangeFailures = 0
        nextExchangeAtMs = 0L
    }

    fun recordExchangeFailure(retryAfterSeconds: Long?) {
        exchangeFailures += 1
        nextExchangeAtMs = clock() + ApiSessionPolicy.exchangeRetryDelayMs(exchangeFailures, retryAfterSeconds)
    }

    /** A cached proof for [sid] signed at most [ApiSessionPolicy.PROOF_REUSE_SECONDS] ago. */
    fun cachedProof(sid: String, serverNow: Long): String? = proof
        ?.takeIf { (cachedSid, signed) -> cachedSid == sid && serverNow - signed.first in 0..ApiSessionPolicy.PROOF_REUSE_SECONDS }
        ?.second?.second

    fun cacheProof(sid: String, signedAt: Long, header: String) {
        proof = sid to (signedAt to header)
    }

    /** True the first time an outcome is seen for a session, so one failing token can't flood analytics. */
    fun firstReport(sid: String, outcome: ApiSessionRenewOutcome): Boolean = reportedRenewals.add("$sid:${outcome.wire}")

    companion object {
        /** Matches the API's `installId` rule. */
        val INSTALL_ID = Regex("^[A-Za-z0-9_-]{8,64}$")
        private val random = SecureRandom()

        fun newInstallId(): String {
            val bytes = ByteArray(16).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
