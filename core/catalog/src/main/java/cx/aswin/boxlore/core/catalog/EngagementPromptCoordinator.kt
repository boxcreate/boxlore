package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.prefs.NpsSurveyMoment
import cx.aswin.boxlore.core.prefs.NpsSurveySchedule
import cx.aswin.boxlore.core.prefs.NpsSurveyStore
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinates proactive engagement prompts (NPS survey, Play review) so only one surfaces per
 * session, and only at a finish moment: an episode finished since boxlore started
 * ([processStartedAtMs], when this coordinator is made at startup), never on an open alone.
 */
class EngagementPromptCoordinator(
    private val userPrefs: UserPreferencesRepository,
    private val surveyStore: NpsSurveyStore,
    private val processStartedAtMs: Long = System.currentTimeMillis(),
    private val scope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Engagement prompt side-effect failed", throwable)
                },
        ),
) {
    /** In-memory guard: at most one proactive modal per app process session. */
    @Volatile
    var sessionProactivePromptShown: Boolean = false
        private set

    /** Returns true when playback is idle and no proactive prompt has shown this session. */
    fun canShowProactivePrompt(isPlaying: Boolean): Boolean = !isPlaying && !sessionProactivePromptShown

    /** A prompt was decided and waits for its screen: no other proactive prompt this session; nothing saved yet. */
    fun reserveSessionForPrompt() {
        sessionProactivePromptShown = true
    }

    /** Marks the current session as having shown a proactive prompt and persists cooldown. */
    fun recordProactivePromptShown() {
        sessionProactivePromptShown = true
        launchSafely("Failed to record engagement prompt") { userPrefs.recordEngagementPromptShown() }
    }

    private fun launchSafely(failure: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, failure, e)
            }
        }
    }

    /** An episode finished in this run of boxlore, recently: the only time a prompt may appear. */
    fun isFinishMoment(lastFinishedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean =
        NpsSurveySchedule.isFinishMoment(lastFinishedAtMs, processStartedAtMs, nowMs)

    /**
     * Counts this start of boxlore and, once per install, carries over an older version's survey
     * ([NpsSurveyStore.carryOverLegacy]). Called once at startup.
     */
    suspend fun onAppLaunched(installedAtMs: Long, nowMs: Long = System.currentTimeMillis()) {
        surveyStore.recordLaunch()
        surveyStore.carryOverLegacy(userPrefs.hasNpsSurveyFired(), installedAtMs, nowMs)
    }

    /** Whether the NPS survey should appear now, by [NpsSurveySchedule] with nothing playing and no prompt yet this session. */
    suspend fun shouldShowNpsSurvey(
        isPlaying: Boolean,
        installedAtMs: Long,
        finishedSinceInstall: Int,
        lastFinishedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!canShowProactivePrompt(isPlaying)) return false
        if (!isFinishMoment(lastFinishedAtMs, nowMs)) return false
        return NpsSurveySchedule.isDue(
            NpsSurveyMoment(
                nowMs = nowMs,
                installedAtMs = installedAtMs,
                finishedSinceInstall = finishedSinceInstall,
                lastFinishedAtMs = lastFinishedAtMs,
                processStartedAtMs = processStartedAtMs,
                record = surveyStore.record(),
                inQuietDays = userPrefs.isInReviewQuietPeriod(installedAtMs, nowMs),
                cooldownElapsed = userPrefs.isEngagementCooldownElapsed(),
            ),
        )
    }

    /**
     * The NPS survey is on screen: it uses up this session's prompt, the shared cooldown and its own
     * 90 days, and lets the milestone review prompt follow later.
     */
    fun onSurveyDisplayed(nowMs: Long = System.currentTimeMillis()) {
        recordProactivePromptShown()
        launchSafely("Failed to record the NPS survey") {
            surveyStore.markShown(nowMs)
            userPrefs.markNpsSurveyFired()
        }
    }

    /** Keeps the latest score, so the milestone review prompt skips listeners who scored low. */
    fun onNpsRatingSubmitted(score: Int) {
        launchSafely("Failed to persist NPS score") { userPrefs.setNpsLastScore(score) }
    }

    /** Whether enough time has passed since the last proactive engagement prompt. */
    suspend fun isEngagementCooldownElapsed(): Boolean = userPrefs.isEngagementCooldownElapsed()

    companion object {
        private const val TAG = "EngagementPromptCoordinator"
    }
}
