package cx.aswin.boxlore.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private object TooltipKeys {
    val HAS_SEEN_SWIPE_DISMISS_TIP =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_seen_swipe_dismiss_tip")
    val HAS_SEEN_TITLE_TAP_TIP =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_seen_title_tap_tip")
    val HAS_SEEN_SWIPE_MINIMIZE_TIP =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_seen_swipe_minimize_tip")
    val HAS_SEEN_MARK_PLAYED_TIP =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_seen_mark_played_tip")
    val HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_seen_listening_history_tracking_notice")
}

private object AnalyticsKeys {
    val HAS_LOGGED_FIRST_PLAY =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("has_logged_first_play")
    val REVIEW_LAST_PROMPT_AT =
        androidx.datastore.preferences.core
            .longPreferencesKey("review_last_prompt_at")
    val REVIEW_PROMPT_COUNT =
        androidx.datastore.preferences.core
            .intPreferencesKey("review_prompt_count")
    val REVIEW_HAS_REVIEWED =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("review_has_reviewed")
    val REVIEW_FIRST_LAUNCH_AT =
        androidx.datastore.preferences.core
            .longPreferencesKey("review_first_launch_at")

    // NPS survey: milestone marks eligibility (pending); the event fires on
    // the next app open so it never surfaces during background playback.
    val NPS_SURVEY_PENDING =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("nps_survey_pending")
    val NPS_SURVEY_FIRED =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("nps_survey_fired")
    val NPS_SURVEY_COMPLETED_COUNT =
        androidx.datastore.preferences.core
            .intPreferencesKey("nps_survey_completed_count")
    val ENGAGEMENT_LAST_PROMPT_AT =
        androidx.datastore.preferences.core
            .longPreferencesKey("engagement_last_prompt_at")
    val NPS_LAST_SCORE =
        androidx.datastore.preferences.core
            .intPreferencesKey("nps_last_score")
    val PROMOTER_REVIEW_PENDING =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("promoter_review_pending")
    val REVIEW_MILESTONE_PENDING =
        androidx.datastore.preferences.core
            .intPreferencesKey("review_milestone_pending")
}

private object FeatureKeys {
    val DISMISSED_FEATURE_VERSION = stringPreferencesKey("dismissed_feature_version")
}

internal class TooltipPreferences(private val dataStore: DataStore<Preferences>) {
    val hasSeenSwipeDismissTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_SWIPE_DISMISS_TIP] ?: false }
            .distinctUntilChanged()

    val hasSeenTitleTapTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] ?: false }
            .distinctUntilChanged()

    val hasSeenSwipeMinimizeTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_SWIPE_MINIMIZE_TIP] ?: false }
            .distinctUntilChanged()

    suspend fun markSwipeDismissTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_SWIPE_DISMISS_TIP] = true }
    }

    suspend fun markTitleTapTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] = true }
    }

    suspend fun markSwipeMinimizeTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_SWIPE_MINIMIZE_TIP] = true }
    }

    val hasSeenMarkPlayedTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] ?: false }
            .distinctUntilChanged()

    suspend fun markMarkPlayedTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] = true }
    }

    val hasSeenListeningHistoryTrackingNotice: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE] ?: false }
            .distinctUntilChanged()

    suspend fun markListeningHistoryTrackingNoticeSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE] = true }
    }
}

internal class ReviewPromptPreferences(private val dataStore: DataStore<Preferences>) {
    // --- APP REVIEW LOGIC ---
    val reviewHasReviewed: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[AnalyticsKeys.REVIEW_HAS_REVIEWED] ?: false }
            .distinctUntilChanged()

    suspend fun markReviewed() {
        dataStore.edit { it[AnalyticsKeys.REVIEW_HAS_REVIEWED] = true }
    }

    suspend fun markReviewPromptShown() {
        dataStore.edit { pref ->
            val count = pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
            pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] = count + 1
            pref[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] = System.currentTimeMillis()
            pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] = System.currentTimeMillis()
            pref.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING)
        }
    }

    /**
     * Rules to show milestone Play review:
     * - A milestone (5/15/30) was reached and stored as pending (survives playback gaps)
     * - NPS survey already fired; skip detractors (score &lt;= 7)
     * - Shared 14-day engagement cooldown
     * - User has NOT reviewed yet; app installed 2+ days; max 3 lifetime; 30-day review gap
     * - Never during playback
     */
    suspend fun shouldShowReviewPrompt(isPlaying: Boolean): Boolean {
        if (isPlaying) return false

        val prefs = dataStore.data.first()
        if (!hasEligibleReviewMilestone(prefs)) return false

        if (!isEngagementCooldownElapsed(prefs)) return false

        val promptCount = prefs[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
        if (promptCount >= 3) return false

        val firstLaunch = prefs[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT]
        if (firstLaunch == null) {
            dataStore.edit { it[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT] = System.currentTimeMillis() }
            return false
        }

        val daysSinceInstall = (System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)
        if (daysSinceInstall < 2) return false

        val lastPrompt = prefs[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] ?: 0L
        val daysSinceLastPrompt = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
        return lastPrompt == 0L || daysSinceLastPrompt >= 30
    }

    /** Remember the highest unreached milestone so prompts survive playback gaps. */
    suspend fun syncReviewMilestonePending(completedCount: Int) {
        val milestone =
            when {
                completedCount >= 30 -> 30
                completedCount >= 15 -> 15
                completedCount >= 5 -> 5
                else -> return
            }
        dataStore.edit { pref ->
            if (pref[AnalyticsKeys.REVIEW_HAS_REVIEWED] == true) return@edit
            val current = pref[AnalyticsKeys.REVIEW_MILESTONE_PENDING]
            if (current == null || milestone > current) {
                pref[AnalyticsKeys.REVIEW_MILESTONE_PENDING] = milestone
            }
        }
    }

    suspend fun reviewMilestonePending(): Int? = dataStore.data.first()[AnalyticsKeys.REVIEW_MILESTONE_PENDING]

    /** Clears a stored milestone after the review prompt is shown or dismissed. */
    suspend fun clearReviewMilestonePending() {
        dataStore.edit { it.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING) }
    }

    /** Synchronous read of whether the user has completed the Play Store review flow. */
    suspend fun hasReviewedSync(): Boolean = dataStore.data.first()[AnalyticsKeys.REVIEW_HAS_REVIEWED] ?: false

    private fun hasEligibleReviewMilestone(prefs: Preferences): Boolean {
        val milestone = prefs[AnalyticsKeys.REVIEW_MILESTONE_PENDING]
        val score = prefs[AnalyticsKeys.NPS_LAST_SCORE]
        return milestone in setOf(5, 15, 30) &&
            prefs[AnalyticsKeys.REVIEW_HAS_REVIEWED] != true &&
            prefs[AnalyticsKeys.NPS_SURVEY_FIRED] == true &&
            (score == null || score > EngagementPromptConstants.DETRACTOR_SCORE_MAX)
    }
}

internal class EngagementPreferences(private val dataStore: DataStore<Preferences>) {
    val hasLoggedFirstPlay: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[AnalyticsKeys.HAS_LOGGED_FIRST_PLAY] ?: false }
            .distinctUntilChanged()

    suspend fun markFirstPlayLogged() {
        dataStore.edit { it[AnalyticsKeys.HAS_LOGGED_FIRST_PLAY] = true }
    }

    // --- FEATURE ANNOUNCEMENT (version-specific one-time dialog) ---
    val dismissedFeatureVersion: Flow<String> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[FeatureKeys.DISMISSED_FEATURE_VERSION] ?: "" }
            .distinctUntilChanged()

    suspend fun dismissFeatureAnnouncement(version: String) {
        dataStore.edit { it[FeatureKeys.DISMISSED_FEATURE_VERSION] = version }
    }
    suspend fun recordEngagementPromptShown() {
        dataStore.edit { pref ->
            pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] = System.currentTimeMillis()
        }
    }

    /** True when at least [EngagementPromptConstants.ENGAGEMENT_COOLDOWN_DAYS] have passed since the last prompt. */
    suspend fun isEngagementCooldownElapsed(): Boolean = isEngagementCooldownElapsed(dataStore.data.first())

    /** Persists the most recent NPS score for milestone gating and promoter handoff. */
    suspend fun setNpsLastScore(score: Int) {
        dataStore.edit { it[AnalyticsKeys.NPS_LAST_SCORE] = score }
    }

    suspend fun npsLastScore(): Int? = dataStore.data.first()[AnalyticsKeys.NPS_LAST_SCORE]

    /** Sets whether a promoter Play review should show on the next eligible app open. */
    suspend fun setPromoterReviewPending(pending: Boolean) {
        dataStore.edit { it[AnalyticsKeys.PROMOTER_REVIEW_PENDING] = pending }
    }

    suspend fun isPromoterReviewPending(): Boolean = dataStore.data.first()[AnalyticsKeys.PROMOTER_REVIEW_PENDING] ?: false

    // --- NPS SURVEY (PostHog) TRIGGER STATE ---
    // The eligibility milestone (e.g. 3rd completed episode) can be reached
    // while playback runs in the background. Rather than fire immediately, we
    // mark the survey "pending" and let MainActivity fire the trigger event on
    // the next app open. Firing happens at most once (guarded by the fired flag).

    /** Mark the NPS survey pending (no-op if it has already fired). */
    suspend fun markNpsSurveyPending(completedCount: Int) {
        dataStore.edit { pref ->
            if (pref[AnalyticsKeys.NPS_SURVEY_FIRED] == true) return@edit
            pref[AnalyticsKeys.NPS_SURVEY_PENDING] = true
            pref[AnalyticsKeys.NPS_SURVEY_COMPLETED_COUNT] = completedCount
        }
    }

    suspend fun isNpsSurveyPending(): Boolean = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_PENDING] ?: false

    /** Whether the NPS trigger event has already fired for this install. */
    suspend fun hasNpsSurveyFired(): Boolean = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_FIRED] ?: false

    /** Completed-episode count captured when the survey became pending. */
    suspend fun npsSurveyCompletedCount(): Int? = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_COMPLETED_COUNT]

    /** Mark the NPS survey as fired and clear the pending flag. */
    suspend fun markNpsSurveyFired() {
        dataStore.edit { pref ->
            pref[AnalyticsKeys.NPS_SURVEY_FIRED] = true
            pref[AnalyticsKeys.NPS_SURVEY_PENDING] = false
        }
    }
}

private fun isEngagementCooldownElapsed(pref: Preferences): Boolean {
    val last = pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] ?: 0L
    if (last == 0L) return true
    val days = (System.currentTimeMillis() - last) / (1000 * 60 * 60 * 24)
    return days >= EngagementPromptConstants.ENGAGEMENT_COOLDOWN_DAYS
}
