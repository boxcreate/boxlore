package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.common.PlaybackParameters
import java.io.IOException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the player's speed and trim silence changes are saved ([PlayerAudioSettings]). */
internal fun PlaybackRepository.playerAudioSettings(): PlayerAudioSettings = PlayerAudioSettings(showPlaybackStore, PrefsAppAudioSettings(userPreferencesRepository))

/**
 * Turns trim silence on or off from the player. While the playing show has its own trim silence
 * setting the change is saved for that show; otherwise it changes the app setting. The service
 * applies it (a Cast receiver can't trim silence).
 */
fun PlaybackRepository.setTrimSilence(enabled: Boolean) {
    val before = playerStateFlow.value.audio
    val settings = playerAudioSettings()
    playerStateFlow.update { state -> state.copy(audio = settings.audioAfterTrimSilence(state.audio, enabled)) }
    repositoryScope.launch {
        try {
            settings.saveTrimSilence(before, enabled)
        } catch (exception: IOException) {
            Log.w("PlaybackRepo", "Unable to persist trim silence", exception)
        }
    }
}

/** Drops the playing show's own speed, so it plays at the app speed again from now on. */
fun PlaybackRepository.useAppSpeedForPlayingShow() {
    val before = playerStateFlow.value.audio
    val settings = playerAudioSettings()
    if (!settings.canUseAppSpeed(before)) return
    playerStateFlow.update { state -> state.copy(audio = state.audio.copy(showSpeed = null)) }
    repositoryScope.launch {
        val appSpeed = settings.useAppSpeed(before) ?: return@launch
        withContext(PlaybackThreadPolicy.mainDispatcher) {
            controller?.takeIf { it.isConnected }?.playbackParameters = PlaybackParameters(appSpeed)
            playerStateFlow.update { state -> state.copy(playbackSpeed = appSpeed) }
        }
    }
}

/** Keeps [PlayerState.audio] on the playing show's stored settings and the app's trim silence. */
internal fun PlaybackRepository.monitorPlayingShowAudio() {
    repositoryScope.launch {
        playingShowAudio(
            podcastIds = playerStateFlow.map { state -> state.currentPodcast?.id },
            showStore = showPlaybackStore,
            appTrimSilence = userPreferencesRepository.trimSilenceEnabledStream,
        ).collect { audio -> playerStateFlow.update { state -> state.copy(audio = audio) } }
    }
}
