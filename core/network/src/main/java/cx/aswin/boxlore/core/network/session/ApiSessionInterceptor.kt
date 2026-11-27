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
        if (pairedOnly && attached == null) return signInFirst(original)
        var response = chain.proceed(original.withSession(attached))
        captureRenewal(sessions, response)
        // Each recovery runs at most once: a refused session first, then a step-up for its replacement.
        val recoveries = mutableSetOf<String>()
        while (true) {
            // Only a 401 is about this session; a 403 answers the bundled app key of old builds, or a route this session may not use.
            if (response.code != UNAUTHORIZED) return response
            val code = ApiErrorCodes.of(response) ?: return response
            val kind = if (code in ApiErrorCodes.SESSION_REJECTIONS) REJECTED else code
            if (!recoveries.add(kind)) return response
            val retry = when (kind) {
                REJECTED -> sessions.recoverFromRejection(attached?.token, code, pairedOnly)
                ApiErrorCodes.VERIFICATION_REQUIRED -> sessions.stepUpForRoute()
                else -> null
            } ?: return response
            response.close()
            attached = retry
            response = chain.proceed(original.withSession(retry))
            captureRenewal(sessions, response)
        }
    }

    /** The answer a watch or TV gives itself for a route it may not call before it holds a paired session. */
    private fun signInFirst(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(UNAUTHORIZED)
            .message("Sign in first")
            .body(SIGN_IN_FIRST_BODY.toResponseBody(JSON))
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
        private val JSON = "application/json".toMediaType()
        private val SIGN_IN_FIRST_BODY = "{\"error\":\"${ApiErrorCodes.ATTESTATION_REQUIRED}\"}"
    }
}
