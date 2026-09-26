package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.FeedbackDraft
import java.net.URLEncoder
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

data class FeedbackUiState(
    val category: FeedbackCategory = FeedbackCategory.FEATURE,
    val message: String = "",
    val stepsToReproduce: String = "",
    val email: String = "",
    val attachDiagnostics: Boolean = true,
    val isDraftRestored: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val diagnosticInfo: DiagnosticInfo? = null,
    val logsPreview: String? = null,
    val isLoadingLogs: Boolean = false,
)

class FeedbackViewModel(
    private val podcastRepository: PodcastRepository,
    private val boxcastPrefs: BoxcastPrefs,
    private val context: Context,
    private val submitFeedbackAction: (suspend (category: String, message: String, appVersion: String, email: String?) -> Boolean)? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val diagnostics = DiagnosticCollector.collect(context)
        val draft = boxcastPrefs.getFeedbackDraft()

        if (draft != null && draft.message.isNotBlank()) {
            val matchingCategory = FeedbackCategory.entries.find { it.id == draft.category }
                ?: FeedbackCategory.FEATURE

            _uiState.update {
                it.copy(
                    category = matchingCategory,
                    message = draft.message,
                    stepsToReproduce = draft.stepsToReproduce,
                    email = draft.email,
                    attachDiagnostics = draft.attachDiagnostics,
                    isDraftRestored = true,
                    diagnosticInfo = diagnostics,
                )
            }
        } else {
            _uiState.update {
                it.copy(diagnosticInfo = diagnostics)
            }
        }
    }

    fun onCategorySelected(category: FeedbackCategory) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
        saveCurrentDraft()
    }

    fun onMessageChanged(message: String) {
        val trimmed = if (message.length > 2000) message.take(2000) else message
        _uiState.update { it.copy(message = trimmed, errorMessage = null) }
        saveCurrentDraft()
    }

    fun onStepsChanged(steps: String) {
        val trimmed = if (steps.length > 1000) steps.take(1000) else steps
        _uiState.update { it.copy(stepsToReproduce = trimmed) }
        saveCurrentDraft()
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
        saveCurrentDraft()
    }

    fun onAttachDiagnosticsChanged(attach: Boolean) {
        _uiState.update { it.copy(attachDiagnostics = attach) }
        saveCurrentDraft()
    }

    fun onDismissDraftBanner() {
        _uiState.update { it.copy(isDraftRestored = false) }
    }

    fun onDiscardDraft() {
        boxcastPrefs.clearFeedbackDraft()
        _uiState.update {
            it.copy(
                category = FeedbackCategory.FEATURE,
                message = "",
                stepsToReproduce = "",
                isDraftRestored = false,
                errorMessage = null,
            )
        }
    }

    private fun saveCurrentDraft() {
        val current = _uiState.value
        if (current.message.isBlank() && current.stepsToReproduce.isBlank()) {
            boxcastPrefs.clearFeedbackDraft()
            return
        }
        val draft = FeedbackDraft(
            category = current.category.id,
            message = current.message,
            email = current.email,
            stepsToReproduce = current.stepsToReproduce,
            attachDiagnostics = current.attachDiagnostics,
        )
        boxcastPrefs.saveFeedbackDraft(draft)
    }

    fun loadLogsForPreview() {
        _uiState.update { it.copy(isLoadingLogs = true) }
        viewModelScope.launch {
            val logs = withContext(Dispatchers.IO) {
                LogcatCollector.collectSanitizedLogcat(maxLines = 150)
            }
            _uiState.update {
                it.copy(
                    logsPreview = logs,
                    isLoadingLogs = false,
                )
            }
        }
    }

    fun clearLogsPreview() {
        _uiState.update { it.copy(logsPreview = null, isLoadingLogs = false) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val trimmedMessage = state.message.trim()
        if (trimmedMessage.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your feedback before submitting.") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            // Map category: proxy endpoint accepts ["feature", "bug", "other"]
            val wireCategory = when (state.category) {
                FeedbackCategory.FEATURE -> "feature"
                FeedbackCategory.BUG, FeedbackCategory.AUDIO -> "bug"
                FeedbackCategory.OTHER -> "other"
            }

            val appVersion = state.diagnosticInfo?.appVersion ?: "unknown"

            val fullMessage = buildString {
                if (state.category == FeedbackCategory.AUDIO) {
                    append("[Category: Audio / Stream Issue]\n\n")
                }
                append(trimmedMessage)
                if (state.stepsToReproduce.isNotBlank()) {
                    append("\n\nSteps to reproduce:\n")
                    append(state.stepsToReproduce.trim())
                }
                if (state.attachDiagnostics && state.diagnosticInfo != null) {
                    append("\n\n---\nDiagnostics: ")
                    append(state.diagnosticInfo.toCondensedSummary())
                }
            }.take(2000)

            val submit = submitFeedbackAction ?: { cat, msg, ver, mail ->
                podcastRepository.submitFeedback(cat, msg, ver, mail)
            }

            val success = submit(
                wireCategory,
                fullMessage,
                appVersion,
                state.email.trim().ifBlank { null },
            )

            if (success) {
                boxcastPrefs.clearFeedbackDraft()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSuccess = true,
                        isDraftRestored = false,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "Couldn't send feedback right now. Please check your connection and try again.",
                    )
                }
            }
        }
    }

    fun buildGitHubIssueUrl(): String {
        val state = _uiState.value
        val title = "[${state.category.label}]: " + state.message.take(60).replace("\n", " ").trim()
        val body = buildString {
            append("### Description\n")
            append(state.message.ifBlank { "Describe the issue or feature request here." })
            append("\n\n")
            if (state.stepsToReproduce.isNotBlank()) {
                append("### Steps to Reproduce\n")
                append(state.stepsToReproduce)
                append("\n\n")
            }
            if (state.diagnosticInfo != null) {
                append(state.diagnosticInfo.toMarkdownReport(state.logsPreview))
            }
        }

        return try {
            "https://github.com/boxcreate/boxlore/issues/new?title=" +
                URLEncoder.encode(title, "UTF-8") +
                "&body=" +
                URLEncoder.encode(body, "UTF-8")
        } catch (_: Exception) {
            "https://github.com/boxcreate/boxlore/issues/new"
        }
    }

    fun shareDiagnosticsReport(context: Context) {
        val state = _uiState.value
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

    class Factory(
        private val podcastRepository: PodcastRepository,
        private val boxcastPrefs: BoxcastPrefs,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedbackViewModel(podcastRepository, boxcastPrefs, context) as T
    }
}
