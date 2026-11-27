package cx.aswin.boxlore.core.network.session

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `POST /auth/watch/session` over a plain client (no session interceptor): the session, its proof
 * and the account token go only on this request, set by [ApiSessionManager].
 */
class OkHttpPairedSessionTrader(
    apiBaseUrl: String,
    private val client: OkHttpClient,
    private val appVersion: () -> String?,
    private val channel: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PairedSessionTrader {
    private val url: HttpUrl = (apiBaseUrl.trimEnd('/') + ApiSessionManager.PAIRED_SESSION_PATH).toHttpUrl()

    override suspend fun trade(request: PairedSessionRequest): PairedSessionResult = withContext(ioDispatcher) {
        val http = Request.Builder()
            .url(url)
            .post(EMPTY_BODY.toRequestBody(JSON))
            .header(ApiSessionInterceptor.SESSION_HEADER, request.session)
            .header(RequestProofInterceptor.PROOF_HEADER, request.proof)
            .header("Authorization", "Bearer ${request.accountToken}")
            .apply {
                appVersion()?.let { header("X-App-Version", it) }
                channel()?.let { header("X-App-Channel", it) }
            }
            .build()
        try {
            client.newCall(http).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val session = runCatching { json.decodeFromString(PairedSessionBody.serializer(), text).session }.getOrNull()
                    return@withContext if (session.isNullOrBlank()) PairedSessionResult.Failed("bad_response") else PairedSessionResult.Issued(session)
                }
                resultOf(response.code, ApiErrorCodes.parse(text), response.header("Retry-After")?.trim()?.toLongOrNull())
            }
        } catch (_: IOException) {
            PairedSessionResult.Failed("network")
        }
    }

    internal companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val EMPTY_BODY = "{}"
        private val json = Json { ignoreUnknownKeys = true }

        /** A refusal by status and `error` code; only a refused account token signs the device out. */
        fun resultOf(status: Int, code: String?, retryAfterSeconds: Long?): PairedSessionResult = when {
            status == UNAUTHORIZED && code == ApiErrorCodes.WATCH_TOKEN_INVALID -> PairedSessionResult.TokenRefused
            status == FORBIDDEN && code == ApiErrorCodes.ATTESTED_SESSION_REQUIRED -> PairedSessionResult.TokenRefused
            status == UNAUTHORIZED && code != null && code in CARRIER_REFUSALS -> PairedSessionResult.SessionRefused(code)
            else -> PairedSessionResult.Failed(listOfNotNull("http_$status", code).joinToString("_"), retryAfterSeconds)
        }

        /** Refusals of the session the trade carried, not of the account token. */
        private val CARRIER_REFUSALS = ApiErrorCodes.SESSION_REJECTIONS + ApiErrorCodes.SESSION_REQUIRED
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
    }
}

@Serializable
private data class PairedSessionBody(val session: String? = null)
