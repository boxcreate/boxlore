package cx.aswin.boxlore.core.playback

import androidx.media3.common.TrackSelectionParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure decisions shared by the UI controller and playback service so background playback keeps
 * working without retaining UI-only polling or an indefinitely paused local session.
 */
object PlaybackPowerPolicy {
    const val UI_POSITION_POLL_INTERVAL_MS = 500L
    const val OUTRO_POLL_INTERVAL_MS = 500L
    const val PAUSED_IDLE_TIMEOUT_MS = 15 * 60 * 1_000L

    fun shouldRunUiPositionTicker(
        isUiForeground: Boolean,
        isPlaying: Boolean,
        isLoading: Boolean,
    ): Boolean = isUiForeground && (isPlaying || isLoading)

    fun shouldSchedulePausedLocalTeardown(
        isUiForeground: Boolean,
        isRemote: Boolean,
        isPlaying: Boolean,
        playWhenReady: Boolean,
    ): Boolean = !isUiForeground && !isRemote && !isPlaying && !playWhenReady

    fun shouldMonitorOutro(
        isPlaying: Boolean,
        effectiveSkipEndingMs: Long,
    ): Boolean = isPlaying && effectiveSkipEndingMs > 0L

    suspend fun persistThenTearDownIfStillIdle(
        persistProgress: suspend () -> Unit,
        isStillIdle: () -> Boolean,
        tearDown: () -> Unit,
    ) {
        persistProgress()
        if (isStillIdle()) {
            tearDown()
        }
    }

    fun audioOffloadPreferences(): TrackSelectionParameters.AudioOffloadPreferences =
        TrackSelectionParameters.AudioOffloadPreferences
            .Builder()
            .setAudioOffloadMode(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            ).setIsSpeedChangeSupportRequired(true)
            .build()
}

/**
 * Process-local Activity visibility. The application-scoped repository writes it and the
 * service observes it; no persisted identity or second dependency graph is introduced.
 */
internal object PlaybackUiVisibility {
    private val mutableForeground = MutableStateFlow(false)
    val isForeground = mutableForeground.asStateFlow()

    fun setForeground(isForeground: Boolean) {
        mutableForeground.value = isForeground
    }
}
