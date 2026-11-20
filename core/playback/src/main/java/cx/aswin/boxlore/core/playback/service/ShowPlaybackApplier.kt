package cx.aswin.boxlore.core.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.playback.PlaybackPowerPolicy
import cx.aswin.boxlore.core.playback.RoomShowPlaybackSettingsStore
import cx.aswin.boxlore.core.playback.ShowPlaybackOverrides
import cx.aswin.boxlore.core.playback.ShowPlaybackPolicy
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one place the service sets speed and trim silence: the playing show's own value, else the app's.
 * The app settings arrive from preferences; the show's arrive from the guarded activation job
 * ([cx.aswin.boxlore.core.playback.PlaybackIntroOutroController]), so an app change never undoes a
 * show's own speed and a late lookup for an earlier episode never applies.
 */
internal class ShowPlaybackApplier(
    private val applySpeed: (Float) -> Unit,
    private val applyTrimSilence: (Boolean) -> Unit,
) {
    private var appSpeed = 1f
    private var appTrimSilence = false
    private val activeShow = MutableStateFlow<ShowPlaybackOverrides?>(null)

    /** The show whose settings playback uses now; null for a briefing or before anything resolves. */
    val activePodcastId: String?
        get() = activeShow.value?.podcastId

    val effectiveSpeed: Float
        get() = ShowPlaybackPolicy.effectiveSpeed(appSpeed, activeShow.value?.speed)

    val effectiveTrimSilence: Boolean
        get() = ShowPlaybackPolicy.effectiveTrimSilence(appTrimSilence, activeShow.value?.trimSilence)

    fun onAppSpeed(speed: Float) {
        appSpeed = speed
        applySpeed(effectiveSpeed)
    }

    fun onAppTrimSilence(enabled: Boolean) {
        appTrimSilence = enabled
        applyTrimSilence(effectiveTrimSilence)
    }

    /** The activation job resolved the playing episode's show (null: use the app settings). */
    fun onShowResolved(show: ShowPlaybackOverrides?) {
        activeShow.value = show
        applySpeed(effectiveSpeed)
        applyTrimSilence(effectiveTrimSilence)
    }

    /** Whether [stored] is the playing show with settings other than the ones playback uses. */
    fun isChangeToActiveShow(stored: ShowPlaybackOverrides): Boolean {
        val active = activeShow.value ?: return false
        return stored.podcastId == active.podcastId && stored != active
    }

    /**
     * Follows the app settings, and the playing show's stored settings: when the show's change
     * (from its show page, or the player) reaches storage, [onActiveShowChanged] re-runs the guarded
     * resolution so the episode playing now picks it up.
     */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    fun start(
        scope: CoroutineScope,
        appSettings: AppPlaybackSettings,
        observeShow: (String) -> Flow<ShowPlaybackOverrides>,
        onActiveShowChanged: () -> Unit,
    ) {
        scope.launch { appSettings.speed.collectLatest(::onAppSpeed) }
        scope.launch { appSettings.trimSilence.collectLatest(::onAppTrimSilence) }
        scope.launch {
            activeShow
                .map { it?.podcastId }
                .distinctUntilChanged()
                .flatMapLatest { podcastId -> podcastId?.let(observeShow) ?: emptyFlow() }
                .collect { stored -> if (isChangeToActiveShow(stored)) onActiveShowChanged() }
        }
    }
}

/** The service's applier: speed goes to the session's player (Cast included), trim silence to the ExoPlayer. */
internal fun serviceShowPlaybackApplier(player: () -> Player?, exoPlayer: () -> ExoPlayer?): ShowPlaybackApplier = ShowPlaybackApplier(
    applySpeed = { speed -> player()?.setPlaybackSpeed(speed) },
    applyTrimSilence = { enabled -> exoPlayer()?.applyTrimSilence(enabled) },
)

/** Follows the app settings in [preferences] and the playing show's row in [podcastDao]. */
internal fun ShowPlaybackApplier.start(
    scope: CoroutineScope,
    preferences: UserPreferencesRepository,
    podcastDao: PodcastDao,
    onActiveShowChanged: () -> Unit,
) = start(scope, AppPlaybackSettings(preferences), RoomShowPlaybackSettingsStore(podcastDao)::observe, onActiveShowChanged)

/** The app's speed and trim silence settings, as [ShowPlaybackApplier.start] follows them. */
internal data class AppPlaybackSettings(val speed: Flow<Float>, val trimSilence: Flow<Boolean>) {
    constructor(preferences: UserPreferencesRepository) : this(preferences.playbackSpeedStream, preferences.trimSilenceEnabledStream)
}

/**
 * Trim silence is an ExoPlayer setting (a Cast receiver can't trim). Offload skips the trimming, so it
 * is switched off while trimming and back on afterwards.
 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.applyTrimSilence(enabled: Boolean) {
    if (skipSilenceEnabled == enabled) return
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(PlaybackPowerPolicy.audioOffloadPreferences(trimSilence = enabled))
            .build()
    skipSilenceEnabled = enabled
}
