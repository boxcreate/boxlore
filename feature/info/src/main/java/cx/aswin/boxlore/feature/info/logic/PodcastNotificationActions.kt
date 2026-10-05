package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Notification consent must not restore an older auto-download choice after persistence. */
internal suspend fun enablePodcastNotifications(
    uiState: MutableStateFlow<PodcastInfoUiState>,
    setNotificationsEnabled: suspend (Podcast, Boolean) -> Boolean,
    trackShowNotificationToggled: (String, Boolean) -> Unit,
) {
    val current = uiState.value as? PodcastInfoUiState.Success ?: return
    val enabled = setNotificationsEnabled(current.podcast, true)
    uiState.update { latest ->
        if (latest is PodcastInfoUiState.Success && latest.podcast.id == current.podcast.id) {
            latest.copy(podcast = latest.podcast.copy(notificationsEnabled = enabled))
        } else {
            latest
        }
    }
    trackShowNotificationToggled(current.podcast.id, enabled)
}

/** A completed download save must not restore an older notification choice. */
internal suspend fun togglePodcastAutoDownload(
    uiState: MutableStateFlow<PodcastInfoUiState>,
    setAutoDownloadEnabled: suspend (String, Boolean) -> Unit,
) {
    val current = uiState.value as? PodcastInfoUiState.Success ?: return
    val enabled = !current.podcast.autoDownloadEnabled
    setAutoDownloadEnabled(current.podcast.id, enabled)
    uiState.update { latest ->
        if (latest is PodcastInfoUiState.Success && latest.podcast.id == current.podcast.id) {
            latest.copy(podcast = latest.podcast.copy(autoDownloadEnabled = enabled))
        } else {
            latest
        }
    }
}
