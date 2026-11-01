package cx.aswin.boxlore.core.network.session

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds [PROOF_HEADER] to requests that carry a session: the device key's signature over the
 * session, method and path ([RequestProofSigner]). A session can be asked for with
 * any install id, so routes holding an install's private data (support) also need this proof that
 * the request comes from the phone that registered the key. Install it as a network interceptor,
 * so it signs the session header [ApiSessionInterceptor] attached, retries included.
 */
class RequestProofInterceptor(private val sign: (token: String, method: String, path: String) -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = request.header(ApiSessionInterceptor.SESSION_HEADER) ?: return chain.proceed(request)
        val proof = runCatching { sign(token, request.method, request.url.encodedPath) }.getOrNull() ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(PROOF_HEADER, proof).build())
    }

    companion object {
        const val PROOF_HEADER = "X-Request-Proof"
    }
}
