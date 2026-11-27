package cx.aswin.boxlore.core.network.session

/**
 * A watch's or TV's paired session for [ApiSessionManager]: once signed in, the device trades its
 * account token ([PairedSessionAccount]) at `POST /auth/watch/session` for a session that reaches
 * the catalogue whatever the API's limited-session mode. The request carries the session the
 * device already holds (a paired one about to run out, else a limited one) and its device-key proof.
 *
 * - A carrier session the API refuses is replaced with a fresh limited one, once.
 * - A refused account token (`watch_token_invalid`, or 403 `attested_session_required`) signs the
 *   device out ([PairedSessionAccount.onTokenRefused]) and drops the paired session; the limited
 *   one stays, for showing or redeeming a code again.
 * - 429, 503 and network failures back off with the exchange backoff and keep the device signed in.
 *
 * Book state is read and written under [lock].
 */
internal class ApiSessionPairing(
    private val trader: PairedSessionTrader,
    private val lock: Any,
    private val book: ApiSessionBook,
    private val proofs: RequestProofSigner,
    /** The trade's path as the API sees it, which the proof signs. */
    private val proofPath: String,
) {
    @Volatile
    var account: PairedSessionAccount? = null

    /** The device's account token; null while it is signed out (or the store can't be read). */
    fun accountToken(): String? = runCatching { account?.accountToken() }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Trades the account token for a paired session and holds it. [limited] gets a limited session
     * when no usable one is held to carry the trade. Returns the paired session, or null.
     */
    suspend fun trade(limited: suspend () -> ApiSessionToken?): ApiSessionToken? {
        val accountToken = accountToken() ?: return null
        var carrier = synchronized(lock) { book.usableToken }
        repeat(MAX_TRADES) {
            val session = carrier ?: limited() ?: return null
            // Without the device key's proof the API reads the token as unsigned and refuses it: never send one unsigned.
            val proof = runCatching { proofs.proof(session.raw, "POST", proofPath) }.getOrNull() ?: return backOff(null)
            val result: PairedSessionResult = nonCancellationFailure<PairedSessionResult>({ PairedSessionResult.Failed("error") }) {
                trader.trade(PairedSessionRequest(accountToken, session.raw, proof))
            }
            when (result) {
                is PairedSessionResult.Issued -> return hold(result.session, accountToken)
                PairedSessionResult.TokenRefused -> return refused(accountToken)
                is PairedSessionResult.SessionRefused -> {
                    synchronized(lock) { book.drop(session.raw) }
                    carrier = null
                }
                is PairedSessionResult.Failed -> return backOff(result.retryAfterSeconds)
            }
        }
        return backOff(null)
    }

    private fun hold(raw: String, accountToken: String): ApiSessionToken? {
        val token = ApiSessionToken.parse(raw)?.takeIf { it.attestation == ApiAttestation.Paired } ?: return backOff(null)
        synchronized(lock) {
            // Signed out, or in to another account, while the trade was on its way: the session is nobody's now.
            if (accountToken() != accountToken) return null
            book.recordExchangeSuccess()
            book.offer(token)
        }
        return token
    }

    private fun refused(accountToken: String): ApiSessionToken? {
        // No backoff: the device signs out, and the limited session it keeps stays free for a new code at once.
        synchronized(lock) { book.dropPaired() }
        runCatching { account?.onTokenRefused(accountToken) }
        return null
    }

    private fun backOff(retryAfterSeconds: Long?): ApiSessionToken? {
        synchronized(lock) { book.recordExchangeFailure(retryAfterSeconds) }
        return null
    }

    private companion object {
        /** The first trade, and one more with a fresh carrier when the API refused the first one's session. */
        const val MAX_TRADES = 2
    }
}

/** Drops a paired session (its watch or TV signed out); any other session stays. Call with the manager's lock held. */
internal fun ApiSessionBook.dropPaired(): Boolean = token?.takeIf { it.attestation == ApiAttestation.Paired }?.let { drop(it.raw) } ?: false
