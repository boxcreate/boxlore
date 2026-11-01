package cx.aswin.boxlore.core.support

import cx.aswin.boxlore.core.network.SupportApi
import cx.aswin.boxlore.core.network.model.SupportErrorResponse
import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Talks to the support API (`/support/...`). Sending never loses a message: when the support API
 * isn't available yet (an older server, or no API session) it falls back to the old feedback form
 * through [legacySubmit].
 */
class SupportRepository(
    private val api: SupportApi,
    private val legacySubmit: suspend (category: String, message: String, appVersion: String, diagnostics: String?, logs: String?) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _meta = MutableStateFlow(SupportMeta.Offline)

    /** The last known switches and limits; [SupportMeta.Offline] until [refresh] succeeds. */
    val meta: StateFlow<SupportMeta> = _meta.asStateFlow()

    /** Reads the switches (and the conversation list, used from the next app update on). */
    suspend fun refresh(): SupportMeta? = withContext(ioDispatcher) {
        val response = call { api.threads().execute() } ?: return@withContext null
        if (!response.isSuccessful) return@withContext null
        val meta = response.body()?.toMeta() ?: return@withContext null
        _meta.value = meta
        meta
    }

    /** Uploads one prepared photo; null when photos are off or the upload fails. */
    suspend fun uploadPhoto(bytes: ByteArray, mimeType: String): String? = withContext(ioDispatcher) {
        if (!_meta.value.photosEnabled) return@withContext null
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val response = call { api.uploadPhoto(body).execute() } ?: return@withContext null
        response.body()?.id?.takeIf { response.isSuccessful }
    }

    suspend fun send(feedback: OutgoingFeedback): SendResult = withContext(ioDispatcher) {
        val request = SupportNewThreadRequest(
            category = feedback.category,
            body = feedback.body,
            photos = feedback.photoIds,
            appVersion = feedback.appVersion,
            device = feedback.device,
            diagnostics = feedback.diagnostics,
            logs = feedback.logs,
            requestId = feedback.requestId,
        )
        val response = call { api.startThread(request).execute() } ?: return@withContext SendResult.Failed
        when {
            response.isSuccessful -> response.body()?.thread?.id?.let { SendResult.Sent(it) } ?: SendResult.Failed
            usesOldForm(response) -> sendThroughOldForm(feedback)
            response.code() == HTTP_CONFLICT || response.code() == HTTP_TOO_MANY -> SendResult.Refused(
                response.errorBody()?.message() ?: DEFAULT_REFUSAL,
            )
            else -> SendResult.Failed
        }
    }

    private suspend fun sendThroughOldForm(feedback: OutgoingFeedback): SendResult {
        // The old form has no audio category and no photo field.
        val category = if (feedback.category == "audio") "bug" else feedback.category
        val ok = legacySubmit(category, feedback.body, feedback.appVersion ?: "unknown", feedback.diagnostics, feedback.logs)
        return if (ok) SendResult.SentByEmailForm else SendResult.Failed
    }

    private inline fun <T> call(block: () -> Response<T>): Response<T>? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503
        private const val DEFAULT_REFUSAL = "This can't be sent right now. Please try again later."
        private val errorJson = Json { ignoreUnknownKeys = true }

        /** Not live yet (404), no API session (401) or switched off (503 `support_unavailable`). */
        internal fun usesOldForm(response: Response<*>): Boolean {
            val code = response.code()
            if (code == HTTP_NOT_FOUND || code == HTTP_UNAUTHORIZED) return true
            return code == HTTP_UNAVAILABLE && response.peekError()?.error == "support_unavailable"
        }

        private fun Response<*>.peekError(): SupportErrorResponse? = errorBody()?.let { body ->
            runCatching { errorJson.decodeFromString(SupportErrorResponse.serializer(), body.source().peek().readUtf8()) }.getOrNull()
        }

        private fun okhttp3.ResponseBody.message(): String? = runCatching {
            errorJson.decodeFromString(SupportErrorResponse.serializer(), string()).message?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

internal fun SupportListResponse.toMeta(): SupportMeta? {
    val m = meta ?: return null
    return SupportMeta(
        canStartNew = m.canStartNew,
        notice = m.notice?.takeIf { it.isNotBlank() },
        photosEnabled = m.photos.enabled && m.photos.max > 0,
        maxPhotos = m.photos.max.coerceIn(0, MAX_PHOTOS_CEILING),
        maxPhotoBytes = m.photos.maxBytes,
        maxBody = m.maxBody.coerceIn(1, MAX_BODY_CEILING),
        pushTopics = m.pushTopics.filter { TOPIC.matches(it) }.distinct().take(MAX_TOPICS),
    )
}

private const val MAX_TOPICS = 5

/** FCM topic names. */
private val TOPIC = Regex("[A-Za-z0-9\\-_.~%]{1,900}")
private const val MAX_PHOTOS_CEILING = 10
private const val MAX_BODY_CEILING = 10_000
