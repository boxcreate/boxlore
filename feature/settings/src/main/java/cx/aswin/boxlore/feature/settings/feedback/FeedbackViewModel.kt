package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.FeedbackDraft
import cx.aswin.boxlore.core.support.OutgoingFeedback
import cx.aswin.boxlore.core.support.PhotoPrep
import cx.aswin.boxlore.core.support.PhotoUpload
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportMeta
import cx.aswin.boxlore.core.support.SupportRepository
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's own list: shown when the server sends none, and the icons and hints for the ids it knows. */
enum class FeedbackCategory(
    val id: String,
    val label: String,
    val placeholder: String,
) {
    FEATURE("feature", "Feature idea", "Describe the feature or improvement you'd like to see..."),
    BUG("bug", "Bug report", "What happened and what did you expect to happen?"),
    AUDIO("audio", "Audio / Stream", "Describe the playback issue (buffering, cut-off, audio distortion)..."),
    OTHER("other", "General", "Tell us what's on your mind..."),
}

val FeedbackCategory.isBugReport: Boolean
    get() = this == FeedbackCategory.BUG || this == FeedbackCategory.AUDIO

/** One choice of what a message is about: the server's id and label, with the app's hint when it knows the id. */
data class FeedbackChoice(val id: String, val label: String) {
    val known: FeedbackCategory? get() = FeedbackCategory.entries.find { it.id == id }
    val placeholder: String get() = known?.placeholder ?: FeedbackCategory.OTHER.placeholder

    /** Bug reports start with diagnostics on. */
    val isBugReport: Boolean get() = known?.isBugReport == true
}

internal val DEFAULT_CHOICES = FeedbackCategory.entries.map { FeedbackChoice(it.id, it.label) }

/** A photo or screenshot picked for the message; uploaded only when the message is sent. */
data class PickedPhoto(val uri: Uri)

data class FeedbackUiState(
    /** The picked choice's id; [choice] falls back to the first one when the server no longer lists it. */
    val categoryId: String = FeedbackCategory.FEATURE.id,
    val message: String = "",
    val attachDiagnostics: Boolean = false,
    val photos: List<PickedPhoto> = emptyList(),
    val meta: SupportMeta = SupportMeta.Offline,
    val isDraftRestored: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    /** True when the message became a conversation, so the reply will show up in the app. */
    val repliesInApp: Boolean = false,
    /** Support wasn't available, so the message went without its photos. */
    val photosDropped: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticInfo: DiagnosticInfo? = null,
    val logsPreview: String? = null,
    val isLoadingLogs: Boolean = false,
) {
    val choices: List<FeedbackChoice> get() = meta.categories.map { FeedbackChoice(it.id, it.label) }.ifEmpty { DEFAULT_CHOICES }
    val choice: FeedbackChoice get() = choices.firstOrNull { it.id == categoryId } ?: choices.first()
    val canAddPhotos: Boolean get() = meta.photosEnabled && photos.size < meta.maxPhotos && !isSubmitting

    /** An older draft can be longer than the limit; it must be shortened before it can be sent. */
    val overLimit: Boolean get() = message.length > meta.maxBody
    val canSend: Boolean get() = message.isNotBlank() && !overLimit && !isSubmitting && meta.canStartNew
    val limitMessage: String? get() = if (overLimit) "This is over the ${"%,d".format(meta.maxBody)}-character limit. Shorten it to send." else null

    /** The server's notice, or a plain one when new messages are paused without one. */
    val shownNotice: String? get() = meta.notice ?: PAUSED_NOTICE.takeUnless { meta.canStartNew }
}

internal const val PAUSED_NOTICE = "Sending is paused for now. Your message stays saved here."

/**
 * The message after an edit: typing stops at [limit], and text already past it (an older, longer
 * draft) can only get shorter, so nothing of it is cut off without the listener choosing to.
 */
internal fun limitedMessage(current: String, next: String, limit: Int): String = when {
    next.length <= limit -> next
    next.length < current.length -> next
    current.length > limit -> current
    else -> next.take(limit)
}

/** Where a retry key outlives the screen: (fingerprint of the content, key). */
interface RetryKeyStore {
    fun load(): Pair<String, String>?

    fun save(fingerprint: String, key: String)

    fun clear()
}

/** [RetryKeyStore] in the feature's own preferences file, so a send retried after the app was closed keeps its key. */
class SharedPrefsRetryKeyStore(context: Context) : RetryKeyStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Pair<String, String>? {
        val fingerprint = prefs.getString(KEY_FINGERPRINT, null) ?: return null
        val key = prefs.getString(KEY_REQUEST, null) ?: return null
        return fingerprint to key
    }

    override fun save(fingerprint: String, key: String) {
        prefs.edit().putString(KEY_FINGERPRINT, fingerprint).putString(KEY_REQUEST, key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "boxlore_feedback_retry"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_REQUEST = "request_id"
    }
}

/**
 * One retry key per message: sending the same content again (after a failure whose answer may
 * have been lost) reuses the key, so the API saves it once; changed content gets a new key. With a
 * [store] the key also survives the screen closing or the app being killed.
 */
internal class RetryKeys(
    private val newKey: () -> String = { UUID.randomUUID().toString() },
    private val store: RetryKeyStore? = null,
) {
    private var last: Pair<Any, String>? = null
    private var lastFingerprint: String? = null

    fun keyFor(content: Any): String {
        last?.let { (seen, key) -> if (seen == content) return key }
        val fingerprint = fingerprintOf(content)
        lastFingerprint = fingerprint
        store?.load()?.let { (saved, key) ->
            if (saved == fingerprint) return key.also { last = content to it }
        }
        return newKey().also {
            last = content to it
            store?.save(fingerprint, it)
        }
    }

    /** Forgets this message's key; a key another message saved since (another form) stays. */
    fun clear() {
        last = null
        if (store != null && store.load()?.first == lastFingerprint) store.clear()
        lastFingerprint = null
    }

    private fun fingerprintOf(content: Any): String =
        MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray()).joinToString("") { "%02x".format(it) }
}

/** What the screen needs from support; the factory wires [SupportRepository], tests pass fakes. */
class FeedbackSupport(
    val meta: StateFlow<SupportMeta>,
    val refresh: suspend () -> Unit,
    val uploadPhoto: suspend (bytes: ByteArray, mimeType: String) -> PhotoUpload,
    val send: suspend (OutgoingFeedback) -> SendResult,
)

private sealed interface PhotoBatch {
    data class Uploaded(val ids: List<String>) : PhotoBatch

    /** Support isn't there for photos; the message goes without them. */
    data object Unavailable : PhotoBatch

    data object Failed : PhotoBatch
}

class FeedbackViewModel internal constructor(
    private val support: FeedbackSupport,
    private val boxcastPrefs: BoxcastPrefs,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readPhoto: (Uri) -> ByteArray? = { uri -> PhotoPrep.prepare(context.contentResolver, uri) },
    private val retryKeys: RetryKeys = RetryKeys(),
) : ViewModel() {

    /** Photos already uploaded for this message, so a retry doesn't upload (and count) them again. */
    private val uploadedPhotos = mutableMapOf<Uri, String>()
    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    /** While a message is going out its draft is fixed, so what was sent is what gets cleared. */
    private val sending: Boolean get() = _uiState.value.isSubmitting

    init {
        loadInitialState()
        viewModelScope.launch { support.meta.collect { meta -> _uiState.update { it.copy(meta = meta) } } }
        viewModelScope.launch { runCatching { support.refresh() } }
    }

    private fun loadInitialState() {
        val diagnostics = DiagnosticCollector.collect(context)
        val draft = boxcastPrefs.getFeedbackDraft()
        // Older drafts kept "steps to reproduce" apart; they now join the message, in full.
        val draftMessage = draft?.let {
            listOf(it.message.trim(), it.stepsToReproduce.trim()).filter(String::isNotBlank).joinToString("\n\n")
        }.orEmpty()
        if (draft != null && draftMessage.isNotBlank()) {
            _uiState.update {
                it.copy(
                    categoryId = draft.category.ifBlank { FeedbackCategory.FEATURE.id },
                    message = draftMessage,
                    attachDiagnostics = draft.attachDiagnostics,
                    isDraftRestored = true,
                    diagnosticInfo = diagnostics,
                )
            }
        } else {
            _uiState.update { it.copy(diagnosticInfo = diagnostics) }
        }
    }

    fun onCategorySelected(id: String) {
        if (sending) return
        _uiState.update { state ->
            val choice = state.choices.firstOrNull { it.id == id } ?: return@update state
            state.copy(categoryId = choice.id, attachDiagnostics = choice.isBugReport, errorMessage = null)
        }
        saveCurrentDraft()
    }

    fun onMessageChanged(message: String) {
        if (sending) return
        _uiState.update { it.copy(message = limitedMessage(it.message, message, it.meta.maxBody), errorMessage = null) }
        saveCurrentDraft()
    }

    fun onAttachDiagnosticsChanged(attach: Boolean) {
        if (sending) return
        _uiState.update { it.copy(attachDiagnostics = attach) }
        saveCurrentDraft()
    }

    fun onPhotosPicked(uris: List<Uri>) {
        if (sending) return
        _uiState.update { state ->
            if (!state.meta.photosEnabled) return@update state
            val room = (state.meta.maxPhotos - state.photos.size).coerceAtLeast(0)
            val added = uris.distinct().filter { uri -> state.photos.none { it.uri == uri } }.take(room).map(::PickedPhoto)
            state.copy(photos = state.photos + added, errorMessage = null)
        }
    }

    fun onRemovePhoto(photo: PickedPhoto) {
        if (sending) return
        _uiState.update { it.copy(photos = it.photos - photo) }
    }

    fun onDiscardDraft() {
        if (sending) return
        boxcastPrefs.clearFeedbackDraft()
        retryKeys.clear()
        _uiState.update {
            it.copy(
                categoryId = FeedbackCategory.FEATURE.id,
                message = "",
                attachDiagnostics = false,
                photos = emptyList(),
                isDraftRestored = false,
                errorMessage = null,
            )
        }
    }

    private fun saveCurrentDraft() {
        val current = _uiState.value
        if (current.message.isBlank()) {
            boxcastPrefs.clearFeedbackDraft()
            return
        }
        boxcastPrefs.saveFeedbackDraft(
            FeedbackDraft(
                category = current.categoryId,
                message = current.message,
                email = "",
                stepsToReproduce = "",
                attachDiagnostics = current.attachDiagnostics,
            ),
        )
    }

    fun loadLogsForPreview() {
        _uiState.update { it.copy(isLoadingLogs = true) }
        viewModelScope.launch {
            val logs = withContext(ioDispatcher) { LogcatCollector.collectSanitizedLogcat(context = context, maxLines = 150) }
            _uiState.update { it.copy(logsPreview = logs, isLoadingLogs = false) }
        }
    }

    fun clearLogsPreview() {
        _uiState.update { it.copy(logsPreview = null, isLoadingLogs = false) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isSubmitting || state.overLimit) return
        val body = state.message.trim()
        if (body.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Write a message first.") }
            return
        }
        val choice = state.choice
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        val requestId = retryKeys.keyFor(listOf(choice.id, body, state.photos, state.attachDiagnostics))

        viewModelScope.launch {
            val diagnosticsInfo = state.diagnosticInfo ?: DiagnosticCollector.collect(context)
            val (diagnosticsReport, sanitizedLogs) = prepareDiagnosticPayload(state, diagnosticsInfo)
            val photos = uploadPhotos(state)
            if (photos is PhotoBatch.Failed) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = "Couldn't attach your photos. Check your connection and try again.") }
                return@launch
            }
            val result = support.send(
                OutgoingFeedback(
                    category = choice.id,
                    body = body,
                    appVersion = diagnosticsInfo.appVersion,
                    // The phone model is part of the diagnostics the listener can turn off.
                    device = diagnosticsInfo.deviceLabel().takeIf { state.attachDiagnostics },
                    diagnostics = diagnosticsReport,
                    logs = sanitizedLogs,
                    photoIds = (photos as? PhotoBatch.Uploaded)?.ids.orEmpty(),
                    requestId = requestId,
                ),
            )
            handleResult(choice, state, result, photosDropped = photos is PhotoBatch.Unavailable)
            // A first conversation: refresh now, so the phone joins its reply-push topic before a reply can arrive.
            if (result is SendResult.Sent) runCatching { support.refresh() }
        }
    }

    /** Photo ids in order; nothing is sent half-attached, and photos are left out only when support can't take them. */
    private suspend fun uploadPhotos(state: FeedbackUiState): PhotoBatch {
        if (state.photos.isEmpty()) return PhotoBatch.Uploaded(emptyList())
        if (!state.meta.photosEnabled) return PhotoBatch.Unavailable
        val ids = mutableListOf<String>()
        for (photo in state.photos) {
            val uploaded = uploadedPhotos[photo.uri]
            if (uploaded != null) {
                ids += uploaded
                continue
            }
            val bytes = withContext(ioDispatcher) { readPhoto(photo.uri) } ?: return PhotoBatch.Failed
            if (state.meta.maxPhotoBytes > 0 && bytes.size > state.meta.maxPhotoBytes) return PhotoBatch.Failed
            when (val upload = support.uploadPhoto(bytes, "image/jpeg")) {
                is PhotoUpload.Uploaded -> ids += upload.id.also { uploadedPhotos[photo.uri] = it }
                PhotoUpload.Unavailable -> return PhotoBatch.Unavailable
                PhotoUpload.Failed -> return PhotoBatch.Failed
            }
        }
        return PhotoBatch.Uploaded(ids)
    }

    private suspend fun prepareDiagnosticPayload(state: FeedbackUiState, diagnosticsInfo: DiagnosticInfo): Pair<String?, String?> {
        if (!state.attachDiagnostics) return null to null
        val sanitizedLogs = state.logsPreview ?: withContext(ioDispatcher) {
            LogcatCollector.collectSanitizedLogcat(context = context, maxLines = 100)
        }
        return diagnosticsInfo.toMarkdownReport() to sanitizedLogs
    }

    private fun handleResult(choice: FeedbackChoice, state: FeedbackUiState, result: SendResult, photosDropped: Boolean) {
        when (result) {
            is SendResult.Sent, SendResult.SentByEmailForm -> {
                AnalyticsHelper.trackFeedbackSent(
                    category = analyticsCategory(choice),
                    withEmail = false,
                    withDiagnostics = state.attachDiagnostics,
                )
                // Clear the saved draft only if it is still the one sent: another form may have saved a new one meanwhile.
                if (boxcastPrefs.getFeedbackDraft()?.message == state.message) boxcastPrefs.clearFeedbackDraft()
                retryKeys.clear()
                uploadedPhotos.clear()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSuccess = true,
                        repliesInApp = result is SendResult.Sent,
                        photosDropped = photosDropped,
                        isDraftRestored = false,
                        photos = emptyList(),
                    )
                }
            }
            is SendResult.Refused -> _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
            SendResult.Failed -> _uiState.update {
                it.copy(isSubmitting = false, errorMessage = "Couldn't send your feedback. Check your connection and try again.")
            }
        }
    }

    class Factory(
        private val supportRepository: SupportRepository,
        private val boxcastPrefs: BoxcastPrefs,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedbackViewModel(
            support = FeedbackSupport(
                meta = supportRepository.meta,
                refresh = { supportRepository.refresh() },
                uploadPhoto = supportRepository::uploadPhoto,
                send = supportRepository::send,
            ),
            boxcastPrefs = boxcastPrefs,
            context = context.applicationContext,
            retryKeys = RetryKeys(store = SharedPrefsRetryKeyStore(context.applicationContext)),
        ) as T
    }
}

/** Analytics keeps the categories it always had: audio counts as a bug report, anything new as other. */
private fun analyticsCategory(choice: FeedbackChoice): String = when (choice.known) {
    FeedbackCategory.FEATURE -> "feature"
    FeedbackCategory.BUG, FeedbackCategory.AUDIO -> "bug"
    FeedbackCategory.OTHER, null -> "other"
}

fun buildFeedbackGitHubIssueUrl(state: FeedbackUiState): String {
    val title = "[${state.choice.label}]: " + state.message.take(60).replace("\n", " ").trim()
    val body = buildString {
        append("### Description\n")
        append(state.message.ifBlank { "Describe the issue or feature request here." })
        append("\n\n")
        if (state.attachDiagnostics && state.diagnosticInfo != null) {
            append(state.diagnosticInfo.toMarkdownReport())
            append("\n*Note: To attach app logs, please copy them from the app's diagnostic preview and paste here.*\n")
        }
    }.take(2000)

    return try {
        "https://github.com/boxcreate/boxlore/issues/new?title=" +
            URLEncoder.encode(title, "UTF-8") +
            "&body=" +
            URLEncoder.encode(body, "UTF-8")
    } catch (_: Exception) {
        "https://github.com/boxcreate/boxlore/issues/new"
    }
}

fun shareFeedbackDiagnostics(context: Context, state: FeedbackUiState) {
    val report = state.diagnosticInfo?.toMarkdownReport(state.logsPreview)
        ?: "No diagnostics available."

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "boxlore Diagnostics & Logs")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Diagnostics & Logs")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}
