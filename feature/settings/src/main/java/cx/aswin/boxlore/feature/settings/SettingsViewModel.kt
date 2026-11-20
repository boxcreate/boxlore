package cx.aswin.boxlore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort
import cx.aswin.boxlore.core.model.text.UiText
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** RSS "add feed" and Podcast Index match-confirmation state owned by [SettingsViewModel]. */
data class SettingsRssUiState(
    val showAddRssDialog: Boolean = false,
    val rssUrl: String = "",
    val rssError: UiText? = null,
    val isAddingRss: Boolean = false,
    val pendingRssMatch: RssSubscriptionResult? = null,
    val isLinkingRssMatch: Boolean = false,
)

/** One-off UI events (e.g. toasts) that [SettingsScreen] surfaces on behalf of the ViewModel. */
sealed interface SettingsEvent {
    data class ShowToast(
        val message: UiText,
    ) : SettingsEvent
}

/**
 * Owns the RSS add-feed and Podcast Index match-confirmation flows for [SettingsScreen],
 * running the subscription work on [viewModelScope] instead of a composable-scoped
 * [kotlinx.coroutines.CoroutineScope].
 *
 * Depends on narrow ports so unit tests can inject fakes (no full repositories).
 */
class SettingsViewModel(
    private val rssRepository: RssSubscriptionPort,
    private val rankingFeedbackRepository: RankingResetPort,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsRssUiState())
    val uiState: StateFlow<SettingsRssUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events

    fun openAddRssDialog() {
        _uiState.value = _uiState.value.copy(showAddRssDialog = true)
    }

    fun resetRecommendations() {
        viewModelScope.launch {
            val reset = rankingFeedbackRepository.reset()
            _events.emit(
                SettingsEvent.ShowToast(
                    UiText.res(if (reset) R.string.settings_recommendations_reset_done else R.string.settings_recommendations_reset_failed),
                ),
            )
        }
    }

    fun dismissAddRssDialog() {
        val state = _uiState.value
        if (state.isAddingRss) return
        _uiState.value = state.copy(showAddRssDialog = false, rssError = null)
    }

    fun onRssUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(rssUrl = url, rssError = null)
    }

    fun addSubscription() {
        val state = _uiState.value
        if (state.isAddingRss) return
        val url = state.rssUrl
        _uiState.value = state.copy(isAddingRss = true, rssError = null)
        viewModelScope.launch {
            try {
                val subscription = rssRepository.addSubscription(url)
                if (subscription.potentialPodcastIndexMatch != null) {
                    _uiState.value =
                        _uiState.value.copy(
                            showAddRssDialog = false,
                            rssUrl = "",
                            pendingRssMatch = subscription,
                        )
                } else {
                    _uiState.value = _uiState.value.copy(showAddRssDialog = false, rssUrl = "")
                    _events.emit(SettingsEvent.ShowToast(subscriptionAddedMessage(subscription)))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(rssError = error.toRssErrorMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isAddingRss = false)
            }
        }
    }

    fun confirmPodcastIndexLink() {
        val state = _uiState.value
        if (state.isLinkingRssMatch) return
        val subscription = state.pendingRssMatch ?: return
        val podcastIndexMatch = subscription.potentialPodcastIndexMatch ?: return
        _uiState.value = state.copy(isLinkingRssMatch = true)
        viewModelScope.launch {
            try {
                rssRepository.confirmPodcastIndexLink(
                    rssPodcastId = subscription.podcast.id,
                    podcastIndexId = podcastIndexMatch.id,
                )
                _uiState.value = _uiState.value.copy(pendingRssMatch = null)
                _events.emit(
                    SettingsEvent.ShowToast(
                        UiText.res(R.string.settings_rss_using_rss_source, subscription.podcast.title),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.emit(SettingsEvent.ShowToast(error.toRssErrorMessage()))
            } finally {
                _uiState.value = _uiState.value.copy(isLinkingRssMatch = false)
            }
        }
    }

    fun keepRssMatchSeparate() {
        if (_uiState.value.pendingRssMatch == null) return
        _uiState.value = _uiState.value.copy(pendingRssMatch = null)
        _events.tryEmit(SettingsEvent.ShowToast(UiText.res(R.string.settings_rss_kept_separate)))
    }
}

private fun subscriptionAddedMessage(subscription: RssSubscriptionResult): UiText = when {
    subscription.linkedPodcastIndexId != null ->
        UiText.res(R.string.settings_rss_switched_to_rss, subscription.podcast.title)
    subscription.automaticUpdateChecksSupported ->
        UiText.plural(R.plurals.settings_rss_added_with_episodes, subscription.episodeCount, subscription.podcast.title, subscription.episodeCount)
    else ->
        UiText.res(R.string.settings_rss_added_refresh, subscription.podcast.title)
}

private fun Throwable.toRssErrorMessage(): UiText = when (this) {
    is IllegalArgumentException ->
        UiText.res(R.string.settings_rss_error_invalid)
    is IOException ->
        UiText.res(R.string.settings_rss_error_download)
    else ->
        UiText.res(R.string.settings_rss_error_generic)
}
