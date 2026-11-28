package cx.aswin.boxlore.core.network.session

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Sessions-only credentials for the boxlore API:
 * - strips the bundled `X-App-Key` from every request (this build never sends it);
 * - attaches the stored `X-Boxlore-Session` (and `X-Session-Proof` once past `exp`) without
 *   waiting, unless there is no usable session at all;
 * - stores `X-Session-Renew` from network responses;
 * - on a 401 `session_expired` / `session_invalid` / `session_revoked` / `attestation_required`, gets
 *   one new session (which may run the Turnstile check) and retries once (a 403 is never recovered);
 * - on `verification_required`, runs the step-up and retries once, or returns the 401 so the caller
 *   falls back (always, in the background, where no check can run);
 * - lends this phone's sign-in to a watch or TV ([ApiSessionManager.LENDING_ROUTES]) only from a
 *   session better than limited when a step-up can give one ([ApiSessionManager.lendsUnchecked]):
 *   it steps up first, and without a better session answers the request itself with 401
 *   `verification_required` (the API's own ask) instead of sending it; a limited session's lending
 *   refused with 403 `attested_session_required` steps up and retries once the same way;
 * - on a watch or TV, sends nothing but the pairing routes until it holds a paired session
 *   ([ApiSessionManager.needsPairedSession]): it answers anything else itself with 401
 *   `attestation_required`, as the API would a limited session, and never sends it.
 */
class ApiSessionInterceptor(private val manager: () -> ApiSessionManager?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request().newBuilder().removeHeader(APP_KEY_HEADER).build()
        val sessions = manager()
        if (sessions == null || !sessions.covers(original.url)) return chain.proceed(original)

        val pairedOnly = sessions.needsPairedSession(original.url)
        var attached = sessions.headersForRequest(pairedOnly)
        if (pairedOnly && attached == null) return answeredHere(original, ApiErrorCodes.ATTESTATION_REQUIRED, "Sign in first")
        // Each recovery runs at most once: a refused session first, then a step-up for its replacement.
        val recoveries = mutableSetOf<String>()
        if (sessions.lendsUnchecked(original.url, attached)) {
            recoveries += ApiErrorCodes.VERIFICATION_REQUIRED
            attached = sessions.stepUpForRoute(original.url) ?: return answeredHere(original, ApiErrorCodes.VERIFICATION_REQUIRED, "Check first")
        }
        var response = chain.proceed(original.withSession(attached))
        captureRenewal(sessions, response)
        while (true) {
            val (kind, code) = recoveryFor(sessions, original, attached, response) ?: return response
            if (!recoveries.add(kind)) return response
            val retry = when (kind) {
                REJECTED -> sessions.recoverFromRejection(attached?.token, code, pairedOnly)
                ApiErrorCodes.VERIFICATION_REQUIRED -> sessions.stepUpForRoute(original.url)
                else -> null
            } ?: return response
            response.close()
            attached = retry
            response = chain.proceed(original.withSession(retry))
            captureRenewal(sessions, response)
        }
    }

    /**
     * What [response] asks to recover from, as (kind, its code): a refused session, a route that wants
     * verification, or a limited session's lending refused for want of a better-attested phone;
     * null for anything else. Only a 401 is about this session otherwise: a 403 answers the bundled
     * app key of old builds, or a route this session may not use.
     */
    private fun recoveryFor(sessions: ApiSessionManager, request: Request, attached: SessionHeaders?, response: Response): Pair<String, String>? {
        val lendingRefused = response.code == FORBIDDEN && sessions.lendsUnchecked(request.url, attached)
        if (response.code != UNAUTHORIZED && !lendingRefused) return null
        val code = ApiErrorCodes.of(response) ?: return null
        return when {
            lendingRefused -> if (code == ApiErrorCodes.ATTESTED_SESSION_REQUIRED) ApiErrorCodes.VERIFICATION_REQUIRED to code else null
            code in ApiErrorCodes.SESSION_REJECTIONS -> REJECTED to code
            else -> code to code
        }
    }

    /**
     * An answer given on the device, never sent: 401 with [code], as the API would answer. A watch or
     * TV gets `attestation_required` for a route it may not call before it holds a paired session; a
     * phone gets `verification_required` for lending from a limited session it couldn't step up.
     */
    private fun answeredHere(request: Request, code: String, message: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(UNAUTHORIZED)
            .message(message)
            .body("{\"error\":\"$code\"}".toResponseBody(JSON))
            .build()

    private fun captureRenewal(sessions: ApiSessionManager, response: Response) {
        // A cached response replays an old header; only the network's answer can renew.
        if (response.networkResponse == null) return
        response.header(RENEW_HEADER)?.let(sessions::captureRenewal)
    }

    private fun Request.withSession(headers: SessionHeaders?): Request {
        if (headers == null) return newBuilder().removeHeader(SESSION_HEADER).removeHeader(PROOF_HEADER).build()
        return newBuilder()
            .header(SESSION_HEADER, headers.token)
            .apply { if (headers.proof != null) header(PROOF_HEADER, headers.proof) else removeHeader(PROOF_HEADER) }
            .build()
    }

    companion object {
        const val APP_KEY_HEADER = "X-App-Key"
        const val SESSION_HEADER = "X-Boxlore-Session"
        const val RENEW_HEADER = "X-Session-Renew"
        const val PROOF_HEADER = "X-Session-Proof"
        private const val REJECTED = "rejected"
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private val JSON = "application/json".toMediaType()

        /**
         * Whether the request that got [response] carried a session better than limited (App Check or
         * challenge): a lending refused with 403 `attested_session_required` from such a session needs
         * App Check, which no check on screen gives.
         */
        fun sentAttestedSession(response: Response): Boolean = ApiSessionToken.parse(response.request.header(SESSION_HEADER))?.limited == false
    }
}
