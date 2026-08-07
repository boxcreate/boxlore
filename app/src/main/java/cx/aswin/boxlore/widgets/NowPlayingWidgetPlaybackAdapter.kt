package cx.aswin.boxlore.widgets

import android.os.Bundle
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.isTransportReady
import cx.aswin.boxlore.core.playback.skipBackward
import cx.aswin.boxlore.core.playback.skipForward
import cx.aswin.boxlore.core.playback.skipToNextEpisode
import cx.aswin.boxlore.core.playback.skipToPreviousEpisode
import cx.aswin.boxlore.core.playback.togglePlayPause
import cx.aswin.boxlore.feature.widgets.WidgetPlaybackSource
import cx.aswin.boxlore.feature.widgets.WidgetPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class NowPlayingWidgetPlaybackAdapter(
    private val playbackRepository: PlaybackRepository,
    scope: CoroutineScope,
) : WidgetPlaybackSource {
    override val state: StateFlow<WidgetPlaybackState> =
        playbackRepository.playerState
            .map(PlayerState::toWidgetPlaybackState)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = playbackRepository.playerState.value.toWidgetPlaybackState(),
            )

    override suspend fun restoreBeforeCollect() {
        restoreIfNeeded()
    }

    override suspend fun restoreBeforeAction() {
        restoreIfNeeded()
        awaitController()
    }

    override suspend fun togglePlayPause() {
        // MediaController APIs must run on the application/main thread.
        withContext(Dispatchers.Main) {
            playbackRepository.togglePlayPause(
                Bundle().apply { putString("entry_point", WIDGET_ENTRY_POINT) },
            )
        }
    }

    override suspend fun previous() {
        withContext(Dispatchers.Main) {
            playbackRepository.skipToPreviousEpisode()
        }
    }

    override suspend fun next() {
        withContext(Dispatchers.Main) {
            playbackRepository.skipToNextEpisode()
        }
    }

    override suspend fun skipForward() {
        withContext(Dispatchers.Main) {
            playbackRepository.skipForward()
        }
    }

    override suspend fun skipBackward() {
        withContext(Dispatchers.Main) {
            playbackRepository.skipBackward()
        }
    }

    private suspend fun restoreIfNeeded() {
        if (playbackRepository.playerState.value.currentEpisode == null) {
            playbackRepository.restoreLastSession()
        }
    }

    private suspend fun awaitController() {
        repeat(CONTROLLER_WAIT_ATTEMPTS) {
            if (playbackRepository.isTransportReady()) return
            delay(CONTROLLER_WAIT_MS)
        }
    }

    private companion object {
        const val WIDGET_ENTRY_POINT = "widget_now_playing"
        const val CONTROLLER_WAIT_ATTEMPTS = 40
        const val CONTROLLER_WAIT_MS = 50L
    }
}

internal fun PlayerState.toWidgetPlaybackState(): WidgetPlaybackState {
    val episode = currentEpisode
    val podcast = currentPodcast
    return WidgetPlaybackState(
        episodeId = episode?.id,
        episodeTitle = episode?.title,
        podcastTitle = podcast?.title ?: episode?.podcastTitle,
        artworkUrl =
            episode?.imageUrl?.takeIf { it.isNotBlank() }
                ?: episode?.podcastImageUrl?.takeIf { it.isNotBlank() }
                ?: podcast?.imageUrl?.takeIf { it.isNotBlank() },
        isPlaying = isPlaying,
        isLoading = isLoading,
        positionMs = position,
        durationMs = duration,
        seekForwardMs = seekForwardMs,
        seekBackwardMs = seekBackwardMs,
    )
}
