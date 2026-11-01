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

    // NPS survey: whether it has appeared, which the milestone review prompt waits for.
    val NPS_SURVEY_FIRED =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("nps_survey_fired")
    val ENGAGEMENT_LAST_PROMPT_AT =
        androidx.datastore.preferences.core
            .longPreferencesKey("engagement_last_prompt_at")
    val NPS_LAST_SCORE =
        androidx.datastore.preferences.core
            .intPreferencesKey("nps_last_score")
    val REVIEW_MILESTONE_PENDING =
        androidx.datastore.preferences.core
            .intPreferencesKey("review_milestone_pending")

    /** The highest milestone a review prompt already used; a milestone counts once. */
    val REVIEW_MILESTONE_CONSUMED =
        androidx.datastore.preferences.core
            .intPreferencesKey("review_milestone_consumed")

    /** When a library import or backup restore last finished, for the quiet days after it. */
    val LIBRARY_IMPORTED_AT =
        androidx.datastore.preferences.core
            .longPreferencesKey("library_imported_at")
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
            pref[AnalyticsKeys.REVIEW_MILESTONE_PENDING]?.let { used ->
                pref[AnalyticsKeys.REVIEW_MILESTONE_CONSUMED] = maxOf(used, pref[AnalyticsKeys.REVIEW_MILESTONE_CONSUMED] ?: 0)
            }
            pref.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING)
        }
    }

    /** A library import or backup restore finished: no review prompt for the quiet days after it. */
    suspend fun markLibraryImported(nowMs: Long = System.currentTimeMillis()) {
        dataStore.edit { it[AnalyticsKeys.LIBRARY_IMPORTED_AT] = nowMs }
    }

    /**
     * True within [EngagementPromptConstants.REVIEW_QUIET_DAYS] of installing boxlore ([installedAtMs],
     * Android's own install time, which a backup doesn't carry) or of the last library import.
     */
    suspend fun isInReviewQuietPeriod(installedAtMs: Long, nowMs: Long = System.currentTimeMillis(),): Boolean =
        isInReviewQuietPeriod(dataStore.data.first(), installedAtMs, nowMs)

    private fun isInReviewQuietPeriod(prefs: Preferences, installedAtMs: Long, nowMs: Long,): Boolean {
        val quietFrom = maxOf(installedAtMs, prefs[AnalyticsKeys.LIBRARY_IMPORTED_AT] ?: 0L)
        return nowMs - quietFrom < EngagementPromptConstants.REVIEW_QUIET_DAYS * DAY_MS
    }

    /**
     * Rules to show milestone Play review:
     * - A milestone (5/15/30) was reached and stored as pending (survives playback gaps)
     * - NPS survey already fired; skip detractors (score &lt;= 7)
     * - Shared 14-day engagement cooldown
     * - User has NOT reviewed yet; 2+ days since install ([installedAtMs]) and since the last import;
     *   max 3 lifetime; 30-day review gap
     * - Never during playback
     */
    suspend fun shouldShowReviewPrompt(isPlaying: Boolean, installedAtMs: Long, nowMs: Long = System.currentTimeMillis(),): Boolean {
        if (isPlaying) return false

        val prefs = dataStore.data.first()
        if (!hasEligibleReviewMilestone(prefs)) return false

        if (!isEngagementCooldownElapsed(prefs)) return false

        val promptCount = prefs[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
        if (promptCount >= 3) return false

        if (isInReviewQuietPeriod(prefs, installedAtMs, nowMs)) return false

        val lastPrompt = prefs[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] ?: 0L
        val daysSinceLastPrompt = (nowMs - lastPrompt) / DAY_MS
        return lastPrompt == 0L || daysSinceLastPrompt >= 30
    }

    /**
     * Stores the milestone [completedCount] reaches (5, 15 or 30) so a prompt can show once playback
     * stops; a milestone a prompt already used never comes back. [completedCount] counts episodes
     * finished since boxlore was installed here, so a pending milestone a backup brought in with no such
     * listening behind it is dropped. On the first run after updating, an install that was already
     * prompted counts at least milestone 5, and the milestone it has reached now, as used.
     */
    suspend fun syncReviewMilestonePending(completedCount: Int) {
        val milestone =
            when {
                completedCount >= 30 -> 30
                completedCount >= 15 -> 15
                completedCount >= 5 -> 5
                else -> null
            }
        dataStore.edit { pref ->
            if (pref[AnalyticsKeys.REVIEW_HAS_REVIEWED] == true) return@edit
            if (pref[AnalyticsKeys.REVIEW_MILESTONE_CONSUMED] == null && (pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0) > 0) {
                pref[AnalyticsKeys.REVIEW_MILESTONE_CONSUMED] = maxOf(FIRST_MILESTONE, milestone ?: 0)
            }
            if (milestone == null || milestone <= (pref[AnalyticsKeys.REVIEW_MILESTONE_CONSUMED] ?: 0)) {
                pref.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING)
            } else {
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
    /**
     * Set by versions that sent `first_episode_played` from the screen. The player now claims it once
     * per install (core:playback `FirstPlayClaim`) and reads this only so an update doesn't send it again.
     */
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

    /** The latest NPS score; the milestone review prompt skips listeners who scored [EngagementPromptConstants.DETRACTOR_SCORE_MAX] or lower. */
    suspend fun setNpsLastScore(score: Int) {
        dataStore.edit { it[AnalyticsKeys.NPS_LAST_SCORE] = score }
    }

    suspend fun npsLastScore(): Int? = dataStore.data.first()[AnalyticsKeys.NPS_LAST_SCORE]

    /** Whether the NPS survey has appeared for this listener; the milestone review prompt waits for it. */
    suspend fun hasNpsSurveyFired(): Boolean = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_FIRED] ?: false

    /** The NPS survey appeared. Its own schedule lives in [NpsSurveyStore], which backups leave out. */
    suspend fun markNpsSurveyFired() {
        dataStore.edit { pref -> pref[AnalyticsKeys.NPS_SURVEY_FIRED] = true }
    }
}

private fun isEngagementCooldownElapsed(pref: Preferences): Boolean {
    val last = pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] ?: 0L
    if (last == 0L) return true
    val days = (System.currentTimeMillis() - last) / (1000 * 60 * 60 * 24)
    return days >= EngagementPromptConstants.ENGAGEMENT_COOLDOWN_DAYS
}

private const val DAY_MS = 24L * 60 * 60 * 1000

private const val FIRST_MILESTONE = 5
