package cx.aswin.boxlore.core.playback

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Sleep timer + late-night nudge ownership for [PlaybackRepository].
 */
internal class PlaybackSleepController(
    private val scope: CoroutineScope,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val prefs: SharedPreferences,
    private val mediaHandle: PlaybackMediaControllerHandle,
    private val stopProgressTicker: () -> Unit,
    private val lastSleepPromptWindowIdKey: String,
    private val debugSkipSleepWindowKey: String,
) {
    private var sleepTimerJob: Job? = null

    /** Cancels the countdown job only (MediaController bridge path). */
    fun cancelSleepTimerJob() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
    }

    /** Cancels the job and clears [SleepTimerHolder] (session clear / stop-and-clear). */
    fun cancelTimer() {
        cancelSleepTimerJob()
        SleepTimerHolder.activeSleepTimerEndMs = null
        SleepTimerHolder.sleepAtEndOfEpisode = false
    }

    /**
     * Returns a stable id for the current "night window" (10:30 PM - 4:00 AM), or null if
     * the current time is outside that window. Nights that cross midnight share the id of the
     * calendar day the window started on, so a single window is never split in two.
     */
    private fun currentNightWindowId(): String? = NightWindowLogic.currentNightWindowId()

    fun isDebugSkipSleepWindowEnabled(): Boolean = prefs.getBoolean(debugSkipSleepWindowKey, false)

    fun setDebugSkipSleepWindow(enabled: Boolean) {
        prefs.edit().putBoolean(debugSkipSleepWindowKey, enabled).apply()
    }

    /**
     * Single chokepoint for the late-night sleep prompt. Called whenever playback transitions
     * from paused/stopped to playing, from any entry point (new episode, resume, skip,
     * notification, Bluetooth, auto-advance). Shows the prompt once per night window.
     */
    fun onPlaybackStarted() {
        if (isDebugSkipSleepWindowEnabled()) {
            playerStateFlow.value = playerStateFlow.value.copy(showLateNightNudge = true)
            return
        }
        val windowId = currentNightWindowId() ?: return
        val stored = prefs.getString(lastSleepPromptWindowIdKey, null)
        if (windowId != stored) {
            prefs.edit().putString(lastSleepPromptWindowIdKey, windowId).apply()
            playerStateFlow.value = playerStateFlow.value.copy(showLateNightNudge = true)
        }
    }

    fun setSleepTimer(
        durationMinutes: Int,
        dismissNudge: Boolean = true,
    ) {
        Log.d("PlaybackRepo", "setSleepTimer called: $durationMinutes minutes, dismissNudge=$dismissNudge")
        sleepTimerJob?.cancel()

        when {
            durationMinutes <= 0 -> clearSleepTimer(dismissNudge)
            durationMinutes == cx.aswin.boxlore.core.model.SleepTimerConstants.END_OF_EPISODE_MINUTES ->
                startEndOfEpisodeSleep(dismissNudge)
            else -> startFixedSleepTimer(durationMinutes, dismissNudge)
        }
    }

    private fun clearSleepTimer(dismissNudge: Boolean) {
        Log.d("PlaybackRepo", "Sleep timer: OFF")
        SleepTimerHolder.activeSleepTimerEndMs = null
        SleepTimerHolder.sleepAtEndOfEpisode = false
        playerStateFlow.value =
            playerStateFlow.value.copy(
                sleepTimerEnd = null,
                sleepAtEndOfEpisode = false,
                showLateNightNudge = if (dismissNudge) false else playerStateFlow.value.showLateNightNudge,
            )
    }

    private fun startEndOfEpisodeSleep(dismissNudge: Boolean) {
        Log.d("PlaybackRepo", "Sleep timer: End of Episode mode ENABLED")
        SleepTimerHolder.activeSleepTimerEndMs = null
        SleepTimerHolder.sleepAtEndOfEpisode = true
        playerStateFlow.value =
            playerStateFlow.value.copy(
                sleepAtEndOfEpisode = true,
                sleepTimerEnd = null,
                showLateNightNudge = if (dismissNudge) false else playerStateFlow.value.showLateNightNudge,
            )

        sleepTimerJob =
            scope.launch {
                while (playerStateFlow.value.sleepAtEndOfEpisode) {
                    val state = playerStateFlow.value
                    if (state.duration > 0 && state.position > 0) {
                        val remaining = (state.duration - state.position).coerceAtLeast(0)
                        val dynamicEndTime = System.currentTimeMillis() + remaining
                        playerStateFlow.value = playerStateFlow.value.copy(sleepTimerEnd = dynamicEndTime)
                    }
                    delay(1000)
                }
            }
    }

    private fun startFixedSleepTimer(
        durationMinutes: Int,
        dismissNudge: Boolean,
    ) {
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        Log.d("PlaybackRepo", "Sleep timer: Fixed ${durationMinutes}m, endTime=$endTime")
        SleepTimerHolder.activeSleepTimerEndMs = endTime
        SleepTimerHolder.sleepAtEndOfEpisode = false
        playerStateFlow.value =
            playerStateFlow.value.copy(
                sleepTimerEnd = endTime,
                sleepAtEndOfEpisode = false,
                showLateNightNudge = if (dismissNudge) false else playerStateFlow.value.showLateNightNudge,
            )

        sleepTimerJob =
            scope.launch {
                while (true) {
                    val currentEnd = SleepTimerHolder.activeSleepTimerEndMs
                    if (currentEnd == null) return@launch
                    if (System.currentTimeMillis() < currentEnd) {
                        delay(1000)
                    } else {
                        Log.d("PlaybackRepo", "Sleep timer: FIRING! Pausing playback.")
                        SleepTimerHolder.activeSleepTimerEndMs = null
                        mediaHandle.controller?.pause()
                        stopProgressTicker()
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                sleepTimerEnd = null,
                                isPlaying = false,
                                showLateNightNudge = false,
                            )
                        return@launch
                    }
                }
            }
    }

    /** True when the current time falls inside the 10:30 PM - 4:00 AM night window. */
    fun isInNightWindow(): Boolean = currentNightWindowId() != null

    fun dismissLateNightNudge() {
        Log.d(
            "PlaybackRepo",
            "dismissLateNightNudge() called, current showLateNightNudge=${playerStateFlow.value.showLateNightNudge}",
        )
        playerStateFlow.value = playerStateFlow.value.copy(showLateNightNudge = false)
        // Snooze for the rest of this window even if it wasn't stamped on show
        // (e.g. a debug-forced prompt outside the normal trigger).
        currentNightWindowId()?.let { windowId ->
            prefs.edit().putString(lastSleepPromptWindowIdKey, windowId).apply()
        }
    }

    /** Clears the once-per-night guard so the prompt can be re-triggered for testing. */
    fun resetSleepNudgeForTesting() {
        prefs.edit().remove(lastSleepPromptWindowIdKey).apply()
        setSleepTimer(0)
    }

    /** Debug-only: force the prompt to show immediately, bypassing all cadence checks. */
    fun forceShowSleepPromptForTesting() {
        playerStateFlow.value = playerStateFlow.value.copy(showLateNightNudge = true)
    }
}
