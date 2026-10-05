package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Notification consent must not restore an older auto-download choice after persistence. */
internal suspend fun enablePodcastNotifications(
    uiState: MutableStateFlow<PodcastInfoUiState>,
    setNotificationsEnabled: suspend (Podcast, Boolean) -> Unit,
    trackShowNotificationToggled: (String, Boolean) -> Unit,
) {
    val current = uiState.value as? PodcastInfoUiState.Success ?: return
    setNotificationsEnabled(current.podcast, true)
    uiState.update { latest ->
        if (latest is PodcastInfoUiState.Success && latest.podcast.id == current.podcast.id) {
            latest.copy(podcast = latest.podcast.copy(notificationsEnabled = true))
        } else {
            latest
        }
    }
    trackShowNotificationToggled(current.podcast.id, true)
}
