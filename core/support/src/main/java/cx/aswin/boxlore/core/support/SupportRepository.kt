package cx.aswin.boxlore.core.support

import cx.aswin.boxlore.core.network.SupportApi
import cx.aswin.boxlore.core.network.model.SupportErrorResponse
import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportEndingDto
import cx.aswin.boxlore.core.network.model.SupportMessageDto
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import cx.aswin.boxlore.core.network.model.SupportReadRequest
import cx.aswin.boxlore.core.network.model.SupportReplyRequest
import cx.aswin.boxlore.core.network.model.SupportReplyResponse
import cx.aswin.boxlore.core.network.model.SupportThreadDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Talks to the support API (`/support/...`). When the support API isn't available (an older
 * server, support switched off, or a session without a device key or proof) a message goes
 * through the old feedback form ([legacySubmit]) instead. Without any API session neither can
 * deliver, so that is a failure and the screen keeps the draft.
 */
class SupportRepository(
    private val api: SupportApi,
    private val legacySubmit: suspend (category: String, message: String, appVersion: String, diagnostics: String?, logs: String?) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val local: SupportLocalStore = SupportLocalStore.InMemory(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _meta = MutableStateFlow(SupportMeta.Offline)
    private val _threads = MutableStateFlow<List<SupportThread>?>(null)
    private val _replyWaiting = MutableStateFlow(local.replyWaiting)
    private val _refreshed = MutableSharedFlow<SupportMeta>(extraBufferCapacity = 4)
    private val _pushedReplies = MutableSharedFlow<String?>(extraBufferCapacity = 8)
    private val checking = AtomicBoolean(false)

    /** The last known switches and limits; [SupportMeta.Offline] until [refresh] succeeds. */
    val meta: StateFlow<SupportMeta> = _meta.asStateFlow()

    /** This phone's conversations, newest first; null until [refresh] succeeds. */
    val threads: StateFlow<List<SupportThread>?> = _threads.asStateFlow()

    /** A team reply hasn't been read yet: Home's Feedback button shows it. */
    val replyWaiting: StateFlow<Boolean> = _replyWaiting.asStateFlow()

    /** Each successful refresh's `meta`, even when nothing else changed: push topics follow it. */
    val refreshed: SharedFlow<SupportMeta> = _refreshed.asSharedFlow()

    /** The conversation a reply push was for (null when it didn't say): an open conversation or the list reloads. */
    val pushedReplies: SharedFlow<String?> = _pushedReplies.asSharedFlow()

    /** Whether this phone has (or had, last time it looked) conversations: Feedback then opens on the list. */
    val hasConversations: Boolean get() = _threads.value?.isNotEmpty() ?: local.hasConversations

    /** Reads the switches and this phone's conversations. */
    suspend fun refresh(): SupportMeta? = withContext(ioDispatcher) {
        val startedAt = clock()
        val response = call { api.threads() } ?: return@withContext null
        if (!response.isSuccessful) {
            // Support went away (or this phone can't use it): forget switches like a pause, so Send works and falls back.
            if (usesOldForm(response)) _meta.value = SupportMeta.Offline
            return@withContext null
        }
        val body = response.body() ?: return@withContext null
        val meta = body.toMeta() ?: return@withContext null
        _meta.value = meta
        val threads = body.threads.map { it.toThread() }
        _threads.value = threads
        local.hasConversations = threads.isNotEmpty()
        local.lastCheckedAt = clock()
        // A push that arrived while this list was on its way is newer than the list.
        setReplyWaiting(threads.any { it.unread } || local.lastPushAt > startedAt)
        _refreshed.tryEmit(meta)
        meta
    }

    /**
     * [refresh] for a phone that has conversations, at most once per [minIntervalMs], so the app
     * can look for replies a push missed without every phone asking the server on every launch.
     */
    suspend fun refreshIfDue(minIntervalMs: Long): SupportMeta? {
        if (!local.hasConversations || clock() - local.lastCheckedAt < minIntervalMs) return null
        // One check at a time, and the attempt counts even if it fails, so an outage isn't asked on every start.
        if (!checking.compareAndSet(false, true)) return null
        return try {
            local.lastCheckedAt = clock()
            refresh()
        } finally {
            checking.set(false)
        }
    }

    /** A reply push arrived for [threadId]: show it on Home until it's read, and let open screens reload. */
    fun onReplyPushed(threadId: String? = null) {
        local.lastPushAt = clock()
        local.hasConversations = true
        setReplyWaiting(true)
        _pushedReplies.tryEmit(threadId)
    }

    private fun setReplyWaiting(waiting: Boolean) {
        local.replyWaiting = waiting
        _replyWaiting.value = waiting
    }

    /** One conversation, newest messages first in view; [before] loads the page older than that message. */
    suspend fun conversation(id: String, before: String? = null): ConversationResult = withContext(ioDispatcher) {
        val response = call { api.conversation(id, before) } ?: return@withContext ConversationResult.Failed
        val body = response.body()
        val thread = body?.thread?.toThread()
        when {
            response.isSuccessful && thread != null -> ConversationResult.Loaded(
                SupportConversation(thread, body.messages.map { it.toMessage() }, body.earlier, body.ending?.toEnding()),
            )
            response.code() == HTTP_NOT_FOUND || usesOldForm(response) -> ConversationResult.Gone
            else -> ConversationResult.Failed
        }
    }

    /** A reply in a conversation; [requestId] is the retry key, as for new messages. */
    suspend fun reply(id: String, body: String, requestId: String?, photoIds: List<String> = emptyList()): ReplyResult = withContext(ioDispatcher) {
        val response = call { api.reply(id, SupportReplyRequest(body = body, photos = photoIds, requestId = requestId)) } ?: return@withContext ReplyResult.Failed
        val reply = response.body()
        when {
            response.isSuccessful -> ReplyResult.Sent(reply?.thread?.toThread(), reply?.message?.toMessage()).also { refresh() }
            response.code() == HTTP_CONFLICT -> ReplyResult.Closed(response.errorBody()?.ending()?.toEnding())
            response.code() == HTTP_TOO_MANY -> ReplyResult.Refused(response.errorBody()?.message() ?: DEFAULT_REFUSAL)
            else -> ReplyResult.Failed
        }
    }

    /**
     * The team's replies up to [upTo] (the newest message on screen) were seen: a reply that came
     * after it stays unread. The list then refreshes, so its labels and Home follow the server.
     */
    suspend fun markRead(id: String, upTo: Long?): Boolean = withContext(ioDispatcher) {
        val ok = call { api.markRead(id, SupportReadRequest(upTo)) }?.isSuccessful == true
        if (ok) {
            _threads.update { list -> list?.map { if (it.id == id) it.copy(unread = false) else it } }
            refresh()
        }
        ok
    }

    /** The listener marks an open conversation solved; the list then refreshes. */
    suspend fun solve(id: String): Boolean = withContext(ioDispatcher) {
        (call { api.solve(id) }?.isSuccessful == true).also { if (it) refresh() }
    }

    /** A photo's bytes from a conversation (owner only), or null. */
    suspend fun photo(id: String): ByteArray? = withContext(ioDispatcher) {
        val response = call { api.photo(id) } ?: return@withContext null
        if (!response.isSuccessful) return@withContext null
        runCatching { response.body()?.bytes() }.getOrNull()
    }

    /** Uploads one prepared photo. [PhotoUpload.Unavailable] when photos are off or support isn't there. */
    suspend fun uploadPhoto(bytes: ByteArray, mimeType: String): PhotoUpload = withContext(ioDispatcher) {
        if (!_meta.value.photosEnabled) return@withContext PhotoUpload.Unavailable
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val response = call { api.uploadPhoto(body) } ?: return@withContext PhotoUpload.Failed
        val id = response.body()?.id
        when {
            response.isSuccessful && id != null -> PhotoUpload.Uploaded(id)
            usesOldForm(response) || response.code() == HTTP_FORBIDDEN -> PhotoUpload.Unavailable
            else -> PhotoUpload.Failed
        }
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
        val response = call { api.startThread(request) } ?: return@withContext SendResult.Failed
        when {
            response.isSuccessful -> response.body()?.thread?.id?.let {
                local.hasConversations = true
                SendResult.Sent(it)
            } ?: SendResult.Failed
            usesOldForm(response) -> sendThroughOldForm(feedback)
            response.code() == HTTP_CONFLICT || response.code() == HTTP_TOO_MANY -> SendResult.Refused(
                response.errorBody()?.message() ?: DEFAULT_REFUSAL,
            )
            else -> SendResult.Failed
        }
    }

    private suspend fun sendThroughOldForm(feedback: OutgoingFeedback): SendResult {
        // The old form knows only these categories and has no photo field.
        val category = when (feedback.category) {
            "feature", "bug", "other" -> feedback.category
            "audio" -> "bug"
            else -> "other"
        }
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
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_UNAVAILABLE = 503
        private const val DEFAULT_REFUSAL = "This can't be sent right now. Please try again later."
        private val errorJson = Json { ignoreUnknownKeys = true }

        /** Errors meaning a session without a device key or proof: the old form, which needs only the session, still works. */
        private val PROOF_ERRORS = setOf("device_key_required", "proof_required")

        /**
         * Support isn't there but the old form can still deliver: not live yet (404), switched off
         * (503 `support_unavailable`), or a session without a device key or proof (401). A 401 for a
         * missing or refused session isn't one: the old form needs that session too.
         */
        internal fun usesOldForm(response: Response<*>): Boolean = when (response.code()) {
            HTTP_NOT_FOUND -> true
            HTTP_UNAUTHORIZED -> response.peekError()?.error in PROOF_ERRORS
            HTTP_UNAVAILABLE -> response.peekError()?.error == "support_unavailable"
            else -> false
        }

        private fun Response<*>.peekError(): SupportErrorResponse? = errorBody()?.let { body ->
            runCatching { errorJson.decodeFromString(SupportErrorResponse.serializer(), body.source().peek().readUtf8()) }.getOrNull()
        }

        private fun okhttp3.ResponseBody.ending(): SupportEndingDto? = runCatching {
            errorJson.decodeFromString(SupportReplyResponse.serializer(), string()).ending
        }.getOrNull()

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
        categories = m.categories
            .filter { CATEGORY_ID.matches(it.id) && it.label.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_CATEGORIES)
            .map { SupportCategory(it.id, it.label.trim().take(MAX_CATEGORY_LABEL)) },
    )
}

private const val MAX_TOPICS = 5
private const val MAX_CATEGORIES = 12
private const val MAX_CATEGORY_LABEL = 40
private val CATEGORY_ID = Regex("[a-z0-9_]{1,32}")

/** FCM topic names. */
private val TOPIC = Regex("[A-Za-z0-9\\-_.~%]{1,900}")
private const val MAX_PHOTOS_CEILING = 10
private const val MAX_BODY_CEILING = 10_000

internal fun SupportThreadDto.toThread(): SupportThread = SupportThread(
    id = id,
    category = category,
    categoryLabel = categoryLabel.ifBlank { "Feedback" },
    title = title.ifBlank { "Feedback" },
    status = SupportStatus(key = status?.key ?: "waiting", label = status?.label.orEmpty(), showTime = status?.showTime ?: true),
    updatedAt = updatedAt,
    unread = unread,
    canReply = canReply,
)

internal fun SupportMessageDto.toMessage(): SupportMessage = SupportMessage(
    id = id,
    fromTeam = from == "team",
    author = author,
    body = body,
    photoIds = photos.map { it.id }.filter { it.isNotBlank() },
    diagnostics = diagnostics,
    createdAt = createdAt,
)

internal fun SupportEndingDto.toEnding(): SupportEnding = SupportEnding(
    kind = kind,
    title = title,
    body = body,
    at = at,
    actionKind = action?.kind?.takeIf { it.isNotBlank() },
    actionLabel = action?.label?.takeIf { it.isNotBlank() },
)
