package cx.aswin.boxlore.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the signed-in account's Firebase ID token (`Authorization: Bearer`) to requests that don't
 * carry one, so routes that link data to an account (support) see it from the first request
 * instead of only after a 401. Signed out, [token] returns null and nothing is added.
 */
class AccountTokenInterceptor(private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val idToken = runCatching { token() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $idToken").build())
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
