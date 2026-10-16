package cx.aswin.boxlore.core.network.session

/**
 * Pure timing rules for API sessions. Session times are API epoch seconds (the app corrects its own
 * clock with the offset learned from each token's `iat`); retry schedules are local milliseconds.
 */
object ApiSessionPolicy {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    private const val HOUR_S = 3_600L
    private const val DAY_S = 24 * HOUR_S

    /** A request sent this close to `exp` signs a proof, so it still renews if it lands after `exp`. */
    const val PROOF_LEAD_SECONDS = 30L

    /** The API accepts a proof within 5 minutes; reuse one signature for at most this long. */
    const val PROOF_REUSE_SECONDS = 120L

    /** A token this close to the end of its attestation window is not worth sending. */
    const val USABLE_MARGIN_SECONDS = 60L

    /** A token rejected this soon after it was issued means the API can't use our sessions right now. */
    const val FRESH_TOKEN_SECONDS = 60L

    private val exchangeRetryMs = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L, MINUTE_MS)
    private const val EXCHANGE_RETRY_CEILING_MS = 5 * MINUTE_MS

    fun isUsable(token: ApiSessionToken, serverNow: Long): Boolean = serverNow < token.attestedUntil - USABLE_MARGIN_SECONDS

    /** Proofs go only on renewable tokens at (or just before) their expiry; earlier requests carry none. */
    fun needsProof(token: ApiSessionToken, serverNow: Long): Boolean = token.renewable && serverNow >= token.expiresAt - PROOF_LEAD_SECONDS

    /**
     * When to get a fresh session in the background before this one runs out: a day early for
     * App Check sessions (their 30-day window), an hour early for everything else.
     */
    fun refreshAt(token: ApiSessionToken): Long {
        val lead = if (token.attestation == ApiAttestation.AppCheck && token.renewable) DAY_S else HOUR_S
        return maxOf(token.issuedAt, token.attestedUntil - lead)
    }

    /**
     * Whether [candidate] should replace [current]: anything beats nothing, better attestation wins,
     * and a weaker session takes over only once the current one is due for a refresh.
     */
    fun shouldReplace(current: ApiSessionToken?, candidate: ApiSessionToken, serverNow: Long): Boolean {
        if (current == null || !isUsable(current, serverNow)) return true
        val rank = candidate.attestation.rank.compareTo(current.attestation.rank)
        return when {
            rank > 0 -> true
            rank == 0 -> candidate.attestedUntil > current.attestedUntil
            else -> serverNow >= refreshAt(current) && candidate.attestedUntil > current.attestedUntil
        }
    }

    /** `X-Session-Renew` only extends the session we already hold. */
    fun acceptsRenewal(current: ApiSessionToken?, renewed: ApiSessionToken): Boolean =
        current != null && renewed.sid == current.sid && renewed.expiresAt > current.expiresAt

    /**
     * Delay before App Check is tried again after the [failures]th failed attempt on a phone that
     * holds a weaker session: about a minute, then hourly, then daily.
     */
    fun upgradeDelayMs(failures: Int): Long = when {
        failures <= 1 -> MINUTE_MS
        failures <= 4 -> HOUR_MS
        else -> DAY_MS
    }

    /** Backoff after the [failures]th failed exchange; a server `Retry-After` wins when it is longer. */
    fun exchangeRetryDelayMs(failures: Int, retryAfterSeconds: Long? = null): Long {
        val base = exchangeRetryMs.getOrElse((failures - 1).coerceAtLeast(0)) { EXCHANGE_RETRY_CEILING_MS }
        return maxOf(base, (retryAfterSeconds ?: 0L) * 1_000L)
    }
}

/** Runs [block], turning any failure except cancellation into [fallback]. */
internal inline fun <T> nonCancellationFailure(fallback: () -> T, block: () -> T): T = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    fallback()
}
