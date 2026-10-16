package cx.aswin.boxlore.core.network.session

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Sessions-only credentials for the boxlore API:
 * - strips the bundled `X-App-Key` from every request (this build never sends it);
 * - attaches the stored `X-Boxlore-Session` (and `X-Session-Proof` once past `exp`) without
 *   waiting, unless there is no usable session at all;
 * - stores `X-Session-Renew` from network responses;
 * - on `session_expired` / `session_invalid` / `session_revoked`, gets one new session and retries once;
 * - on `verification_required` from an AI route, runs the step-up and retries once, or returns the
 *   401 so the caller falls back.
 */
class ApiSessionInterceptor(private val manager: () -> ApiSessionManager?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request().newBuilder().removeHeader(APP_KEY_HEADER).build()
        val sessions = manager()
        if (sessions == null || !sessions.covers(original.url)) return chain.proceed(original)

        var attached = sessions.headersForRequest()
        var response = chain.proceed(original.withSession(attached))
        captureRenewal(sessions, response)
        // Each recovery runs at most once: a refused session first, then a step-up for its replacement.
        val recoveries = mutableSetOf<String>()
        while (true) {
            val code = ApiErrorCodes.of(response) ?: return response
            val kind = if (code in ApiErrorCodes.SESSION_REJECTIONS) REJECTED else code
            if (!recoveries.add(kind)) return response
            val retry = when (kind) {
                REJECTED -> sessions.recoverFromRejection(attached?.token, code)
                ApiErrorCodes.VERIFICATION_REQUIRED -> sessions.stepUpForAiRoute()
                else -> null
            } ?: return response
            response.close()
            attached = retry
            response = chain.proceed(original.withSession(retry))
            captureRenewal(sessions, response)
        }
    }

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
    }
}
