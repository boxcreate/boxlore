package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The app's own speed and trim silence settings, as the player changes them. */
internal interface AppAudioSettings {
    suspend fun speed(): Float

    suspend fun setSpeed(speed: Float)

    suspend fun setTrimSilence(enabled: Boolean)
}

internal class PrefsAppAudioSettings(private val prefs: UserPreferencesRepository) : AppAudioSettings {
    override suspend fun speed(): Float = PlaybackControlSync.sanitizePlaybackSpeed(prefs.playbackSpeedStream.first())

    override suspend fun setSpeed(speed: Float) = prefs.setPlaybackSpeed(speed)

    override suspend fun setTrimSilence(enabled: Boolean) = prefs.setTrimSilenceEnabled(enabled)
}

/**
 * Where a speed or trim silence change from the player, a widget or the watch goes: to the playing
 * show while it has its own value, else to the app setting. Changes saved for a show are reported
 * like the show page reports them; an app trim silence change is reported as the app toggle.
 */
internal class PlayerAudioSettings(
    private val showStore: ShowPlaybackSettingsStore?,
    private val app: AppAudioSettings,
) {
    /** The show a speed change is saved for, or null for the app speed. */
    fun speedShow(audio: PlaybackAudioState): String? = audio.podcastId?.takeIf { audio.hasShowSpeed && showStore != null }

    /** The show a trim silence change is saved for, or null for the app setting. */
    fun trimSilenceShow(audio: PlaybackAudioState): String? = audio.podcastId?.takeIf { audio.hasShowTrimSilence && showStore != null }

    /** What the player shows right after picking [speed]. */
    fun audioAfterSpeed(audio: PlaybackAudioState, speed: Float): PlaybackAudioState = if (speedShow(audio) != null) audio.copy(showSpeed = speed) else audio

    /** What the player shows right after switching trim silence. */
    fun audioAfterTrimSilence(audio: PlaybackAudioState, enabled: Boolean): PlaybackAudioState = if (trimSilenceShow(audio) != null) {
        audio.copy(showTrimSilence = enabled)
    } else {
        audio.copy(appTrimSilence = enabled)
    }

    /** Saves [speed] picked while [before] was playing. Re-picking the show's own speed saves nothing. */
    suspend fun saveSpeed(before: PlaybackAudioState, speed: Float) {
        val showId = speedShow(before)
        when {
            showId == null -> app.setSpeed(speed)
            before.showSpeed != speed -> {
                showStore?.setSpeed(showId, speed)
                AnalyticsHelper.trackSettingsInteraction(SPEED_OVERRIDE_EVENT, "$showId:$speed")
            }
        }
    }

    /** Saves trim silence switched while [before] was playing. */
    suspend fun saveTrimSilence(before: PlaybackAudioState, enabled: Boolean) {
        val showId = trimSilenceShow(before)
        if (showId == null) {
            AnalyticsHelper.trackSettingsInteraction("trim_silence_toggled", enabled.toString())
            app.setTrimSilence(enabled)
        } else {
            showStore?.setTrimSilence(showId, enabled)
            AnalyticsHelper.trackSettingsInteraction("podcast_trim_silence_override_changed", "$showId:${if (enabled) "on" else "off"}")
        }
    }

    /** Whether "Use app speed" can clear a speed for what is playing. */
    fun canUseAppSpeed(audio: PlaybackAudioState): Boolean = audio.podcastId != null && showStore != null

    /** Clears the playing show's own speed and returns the app speed it plays at now, or null when nothing changed. */
    suspend fun useAppSpeed(before: PlaybackAudioState): Float? {
        val showId = before.podcastId ?: return null
        val store = showStore ?: return null
        store.setSpeed(showId, null)
        AnalyticsHelper.trackSettingsInteraction(SPEED_OVERRIDE_EVENT, "$showId:default")
        return app.speed()
    }

    private companion object {
        const val SPEED_OVERRIDE_EVENT = "podcast_speed_override_changed"
    }
}

/**
 * The player's audio state for [podcastIds] (the playing show, null while nothing plays): the show's
 * stored speed and trim silence as they change, and the app's trim silence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun playingShowAudio(
    podcastIds: Flow<String?>,
    showStore: ShowPlaybackSettingsStore?,
    appTrimSilence: Flow<Boolean>,
): Flow<PlaybackAudioState> {
    val playingShow =
        podcastIds
            .map { id -> id?.takeIf { it.isNotBlank() } }
            .distinctUntilChanged()
            .flatMapLatest { podcastId ->
                when {
                    podcastId == null -> flowOf(null)
                    showStore == null -> flowOf(ShowPlaybackOverrides(podcastId))
                    else -> showStore.observe(podcastId)
                }
            }
    return combine(playingShow, appTrimSilence) { show, appTrim ->
        PlaybackAudioState(
            podcastId = show?.podcastId,
            showSpeed = show?.speed,
            showTrimSilence = show?.trimSilence,
            appTrimSilence = appTrim,
        )
    }
}
