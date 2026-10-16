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
 * `POST /auth/session` over a plain client (no session interceptor). The App Check token travels
 * only here, as `X-Firebase-AppCheck`.
 */
class OkHttpSessionExchanger(
    apiBaseUrl: String,
    private val client: OkHttpClient,
    private val appVersion: () -> String?,
    private val channel: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionExchanger {
    private val url: HttpUrl = (apiBaseUrl.trimEnd('/') + ApiSessionManager.AUTH_SESSION_PATH).toHttpUrl()

    override suspend fun exchange(request: SessionExchangeRequest): SessionExchangeResult = withContext(ioDispatcher) {
        val body = SessionRequestBody(
            installId = request.installId,
            channel = channel(),
            devicePublicKey = request.devicePublicKey,
            turnstileToken = request.turnstileToken,
        )
        val http = Request.Builder()
            .url(url)
            .post(json.encodeToString(SessionRequestBody.serializer(), body).toRequestBody(JSON))
            .apply {
                request.appCheckToken?.let { header("X-Firebase-AppCheck", it) }
                appVersion()?.let { header("X-App-Version", it) }
                channel()?.let { header("X-App-Channel", it) }
            }
            .build()
        try {
            client.newCall(http).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val code = ApiErrorCodes.parse(text)
                    val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
                        ?: if (response.code == 403) REVOKED_RETRY_SECONDS else null
                    return@withContext SessionExchangeResult.Failed(
                        reason = listOfNotNull("http_${response.code}", code).joinToString("_"),
                        retryAfterSeconds = retryAfter,
                    )
                }
                val parsed = runCatching { json.decodeFromString(SessionResponseBody.serializer(), text) }.getOrNull()
                    ?: return@withContext SessionExchangeResult.Failed("bad_response")
                SessionExchangeResult.Issued(
                    session = parsed.session,
                    attestation = ApiAttestation.fromWire(parsed.attestation),
                    limited = parsed.limited,
                    reason = parsed.reason?.take(MAX_REASON_LENGTH),
                )
            }
        } catch (_: IOException) {
            SessionExchangeResult.Failed("network")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        // `platform` is a default, so defaults must be encoded; absent optional fields are left out.
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        /** A revoked install asks again at most hourly. */
        const val REVOKED_RETRY_SECONDS = 3_600L
        const val MAX_REASON_LENGTH = 60
    }
}

@Serializable
private data class SessionRequestBody(
    val installId: String,
    val platform: String = "android",
    val channel: String? = null,
    val devicePublicKey: String? = null,
    val turnstileToken: String? = null,
)

@Serializable
private data class SessionResponseBody(
    val session: String,
    val attestation: String? = null,
    val limited: Boolean = false,
    val reason: String? = null,
)
