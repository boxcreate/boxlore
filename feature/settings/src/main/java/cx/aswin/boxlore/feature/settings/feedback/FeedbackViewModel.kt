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
import cx.aswin.boxlore.core.support.SendResult
import cx.aswin.boxlore.core.support.SupportMeta
import cx.aswin.boxlore.core.support.SupportRepository
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** A photo or screenshot picked for the message; uploaded only when the message is sent. */
data class PickedPhoto(val uri: Uri)

data class FeedbackUiState(
    val category: FeedbackCategory = FeedbackCategory.FEATURE,
    val message: String = "",
    val attachDiagnostics: Boolean = false,
    val photos: List<PickedPhoto> = emptyList(),
    val meta: SupportMeta = SupportMeta.Offline,
    val isDraftRestored: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    /** True when the message became a conversation, so the reply will show up in the app. */
    val repliesInApp: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticInfo: DiagnosticInfo? = null,
    val logsPreview: String? = null,
    val isLoadingLogs: Boolean = false,
) {
    val canAddPhotos: Boolean get() = meta.photosEnabled && photos.size < meta.maxPhotos
    val canSend: Boolean get() = message.isNotBlank() && !isSubmitting && meta.canStartNew

    /** The server's notice, or a plain one when new messages are paused without one. */
    val shownNotice: String? get() = meta.notice ?: PAUSED_NOTICE.takeUnless { meta.canStartNew }
}

internal const val PAUSED_NOTICE = "Sending is paused for now. Your message stays saved here."

/** What the screen needs from support; the factory wires [SupportRepository], tests pass fakes. */
class FeedbackSupport(
    val meta: StateFlow<SupportMeta>,
    val refresh: suspend () -> Unit,
    val uploadPhoto: suspend (bytes: ByteArray, mimeType: String) -> String?,
    val send: suspend (OutgoingFeedback) -> SendResult,
)

class FeedbackViewModel(
    private val support: FeedbackSupport,
    private val boxcastPrefs: BoxcastPrefs,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readPhoto: (Uri) -> ByteArray? = { uri -> PhotoPrep.prepare(context.contentResolver, uri) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        viewModelScope.launch { support.meta.collect { meta -> _uiState.update { it.copy(meta = meta) } } }
        viewModelScope.launch { runCatching { support.refresh() } }
    }

    private fun loadInitialState() {
        val diagnostics = DiagnosticCollector.collect(context)
        val draft = boxcastPrefs.getFeedbackDraft()
        // Older drafts kept "steps to reproduce" apart; they now join the message.
        val draftMessage = draft?.let {
            listOf(it.message.trim(), it.stepsToReproduce.trim()).filter(String::isNotBlank).joinToString("\n\n")
        }.orEmpty()
        if (draft != null && draftMessage.isNotBlank()) {
            _uiState.update {
                it.copy(
                    category = FeedbackCategory.entries.find { c -> c.id == draft.category } ?: FeedbackCategory.FEATURE,
                    message = draftMessage.take(MAX_MESSAGE),
                    attachDiagnostics = draft.attachDiagnostics,
                    isDraftRestored = true,
                    diagnosticInfo = diagnostics,
                )
            }
        } else {
            _uiState.update { it.copy(diagnosticInfo = diagnostics) }
        }
    }

    fun onCategorySelected(category: FeedbackCategory) {
        _uiState.update { it.copy(category = category, attachDiagnostics = category.isBugReport, errorMessage = null) }
        saveCurrentDraft()
    }

    fun onMessageChanged(message: String) {
        val limit = _uiState.value.meta.maxBody.coerceAtMost(MAX_MESSAGE)
        _uiState.update { it.copy(message = message.take(limit), errorMessage = null) }
        saveCurrentDraft()
    }

    fun onAttachDiagnosticsChanged(attach: Boolean) {
        _uiState.update { it.copy(attachDiagnostics = attach) }
        saveCurrentDraft()
    }

    fun onPhotosPicked(uris: List<Uri>) {
        _uiState.update { state ->
            if (!state.meta.photosEnabled) return@update state
            val room = (state.meta.maxPhotos - state.photos.size).coerceAtLeast(0)
            val added = uris.distinct().filter { uri -> state.photos.none { it.uri == uri } }.take(room).map(::PickedPhoto)
            state.copy(photos = state.photos + added, errorMessage = null)
        }
    }

    fun onRemovePhoto(photo: PickedPhoto) {
        _uiState.update { it.copy(photos = it.photos - photo) }
    }

    fun onDiscardDraft() {
        boxcastPrefs.clearFeedbackDraft()
        _uiState.update {
            it.copy(
                category = FeedbackCategory.FEATURE,
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
                category = current.category.id,
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
        if (state.isSubmitting) return
        val body = state.message.trim()
        if (body.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Write a message first.") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val diagnosticsInfo = state.diagnosticInfo ?: DiagnosticCollector.collect(context)
            val (diagnosticsReport, sanitizedLogs) = prepareDiagnosticPayload(state, diagnosticsInfo)
            val photoIds = uploadPhotos(state)
            if (photoIds == null) {
                _uiState.update { it.copy(isSubmitting = false, errorMessage = "Couldn't attach your photos. Check your connection and try again.") }
                return@launch
            }
            val result = support.send(
                OutgoingFeedback(
                    category = state.category.id,
                    body = body,
                    appVersion = diagnosticsInfo.appVersion,
                    device = diagnosticsInfo.deviceLabel(),
                    diagnostics = diagnosticsReport,
                    logs = sanitizedLogs,
                    photoIds = photoIds,
                ),
            )
            handleResult(state, result)
        }
    }

    /** Photo ids in order, or null when any upload failed (nothing is sent half-attached). */
    private suspend fun uploadPhotos(state: FeedbackUiState): List<String>? {
        if (state.photos.isEmpty() || !state.meta.photosEnabled) return emptyList()
        val ids = mutableListOf<String>()
        for (photo in state.photos) {
            val bytes = withContext(ioDispatcher) { readPhoto(photo.uri) } ?: return null
            if (state.meta.maxPhotoBytes > 0 && bytes.size > state.meta.maxPhotoBytes) return null
            ids += support.uploadPhoto(bytes, "image/jpeg") ?: return null
        }
        return ids
    }

    private suspend fun prepareDiagnosticPayload(state: FeedbackUiState, diagnosticsInfo: DiagnosticInfo): Pair<String?, String?> {
        if (!state.attachDiagnostics) return null to null
        val sanitizedLogs = state.logsPreview ?: withContext(ioDispatcher) {
            LogcatCollector.collectSanitizedLogcat(context = context, maxLines = 100)
        }
        return diagnosticsInfo.toMarkdownReport() to sanitizedLogs
    }

    private fun handleResult(state: FeedbackUiState, result: SendResult) {
        when (result) {
            is SendResult.Sent, SendResult.SentByEmailForm -> {
                val repliesInApp = result is SendResult.Sent
                AnalyticsHelper.trackFeedbackSent(
                    category = analyticsCategory(state.category),
                    withEmail = false,
                    withDiagnostics = state.attachDiagnostics,
                )
                boxcastPrefs.clearFeedbackDraft()
                _uiState.update {
                    it.copy(isSubmitting = false, isSuccess = true, repliesInApp = repliesInApp, isDraftRestored = false, photos = emptyList())
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
        ) as T
    }

    companion object {
        const val MAX_MESSAGE = 2000
    }
}

/** Analytics keeps the categories it always had: audio counts as a bug report. */
private fun analyticsCategory(category: FeedbackCategory): String = when (category) {
    FeedbackCategory.FEATURE -> "feature"
    FeedbackCategory.BUG, FeedbackCategory.AUDIO -> "bug"
    FeedbackCategory.OTHER -> "other"
}

fun buildFeedbackGitHubIssueUrl(state: FeedbackUiState): String {
    val title = "[${state.category.label}]: " + state.message.take(60).replace("\n", " ").trim()
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
