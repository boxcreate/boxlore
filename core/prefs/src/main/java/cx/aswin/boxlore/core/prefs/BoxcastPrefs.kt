package cx.aswin.boxlore.core.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed façade over SharedPreferences file [PREFS_NAME] (`boxlore_prefs`).
 *
 * Features and core helpers must use this API instead of calling
 * `context.getSharedPreferences(…)` directly. Key strings stay identity-stable;
 * the file name migrates from `boxcast_prefs` via [PrefsFileMigrator].
 */
class BoxcastPrefs(context: Context) {

    private val prefs: SharedPreferences =
        PrefsFileMigrator.open(
            context.applicationContext,
            newName = PREFS_NAME,
            oldName = PrefsFileMigrator.LegacyFiles.PREFS,
        )

    // ── Onboarding ──────────────────────────────────────────────────────────

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    // ── User genres / interests ──────────────────────────────────────────────

    fun getUserGenres(): Set<String> = prefs.getStringSet(KEY_USER_GENRES, emptySet()) ?: emptySet()

    fun setUserGenres(genres: Set<String>) {
        prefs.edit().putStringSet(KEY_USER_GENRES, genres).apply()
    }

    // ── Home / explore recommendation cache ─────────────────────────────────

    fun getCachedRecommendationsJson(): String? = prefs.getString(KEY_CACHED_RECOMMENDATIONS, null)

    fun isRecommendationsFallback(default: Boolean = true): Boolean = prefs.getBoolean(KEY_IS_RECOMMENDATIONS_FALLBACK, default)

    fun saveRecommendationsCache(serializedJson: String, isFallback: Boolean) {
        prefs.edit()
            .putString(KEY_CACHED_RECOMMENDATIONS, serializedJson)
            .putBoolean(KEY_IS_RECOMMENDATIONS_FALLBACK, isFallback)
            .apply()
    }

    fun setCachedRecommendationsJson(serializedJson: String) {
        prefs.edit().putString(KEY_CACHED_RECOMMENDATIONS, serializedJson).apply()
    }

    // ── Home featured video showcase ────────────────────────────────────────

    fun isFeaturedVideoShowcaseDismissed(): Boolean = prefs.getBoolean(KEY_FEATURED_VIDEO_SHOWCASE_DISMISSED, false)

    fun dismissFeaturedVideoShowcaseForever() {
        prefs.edit().putBoolean(KEY_FEATURED_VIDEO_SHOWCASE_DISMISSED, true).apply()
    }

    // ── Because-you-like cache ──────────────────────────────────────────────

    fun getCachedBylRecommendationsJson(): String? = prefs.getString(KEY_CACHED_BYL_RECOMMENDATIONS, null)

    fun getCachedBylPodcastsJson(): String? = prefs.getString(KEY_CACHED_BYL_PODCASTS, null)

    fun getCachedBylPodcastId(): String? = prefs.getString(KEY_CACHED_BYL_PODCAST_ID, null)

    fun getCachedBylSlot(): String? = prefs.getString(KEY_CACHED_BYL_SLOT, null)

    fun saveBylCache(
        episodesJson: String,
        podcastsJson: String,
        podcastId: String,
        slotKey: String? = null,
    ) {
        prefs.edit().apply {
            putString(KEY_CACHED_BYL_RECOMMENDATIONS, episodesJson)
            putString(KEY_CACHED_BYL_PODCASTS, podcastsJson)
            putString(KEY_CACHED_BYL_PODCAST_ID, podcastId)
            if (slotKey != null) {
                putString(KEY_CACHED_BYL_SLOT, slotKey)
            } else {
                remove(KEY_CACHED_BYL_SLOT)
            }
            apply()
        }
    }

    /** Clears a recommendation cache whose seed show moved to a different catalog identity. */
    fun clearBylCacheIfPodcastId(podcastId: String) {
        if (getCachedBylPodcastId() != podcastId) return
        prefs
            .edit()
            .remove(KEY_CACHED_BYL_RECOMMENDATIONS)
            .remove(KEY_CACHED_BYL_PODCASTS)
            .remove(KEY_CACHED_BYL_PODCAST_ID)
            .remove(KEY_CACHED_BYL_SLOT)
            .apply()
    }

    // ── Learn curiosity history ─────────────────────────────────────────────

    fun getLearnCuriosityHistoryJson(): String? = prefs.getString(KEY_LEARN_CURIOSITY_HISTORY, null)

    fun setLearnCuriosityHistoryJson(json: String?) {
        prefs.edit().apply {
            if (json == null) {
                remove(KEY_LEARN_CURIOSITY_HISTORY)
            } else {
                putString(KEY_LEARN_CURIOSITY_HISTORY, json)
            }
            apply()
        }
    }

    fun getDismissedCuriosityIds(): Set<String> = prefs.getStringSet(KEY_DISMISSED_CURIOSITIES, emptySet()) ?: emptySet()

    fun setDismissedCuriosityIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_DISMISSED_CURIOSITIES, ids).apply()
    }

    fun clearLearnCuriosity() {
        prefs.edit()
            .remove(KEY_DISMISSED_CURIOSITIES)
            .remove(KEY_LEARN_CURIOSITY_HISTORY)
            .apply()
    }

    // ── Learner / ranking debug log gate ────────────────────────────────────

    fun isLearnerLogEnabled(default: Boolean = false): Boolean = prefs.getBoolean(KEY_LEARNER_LOG_ENABLED, default)

    fun setLearnerLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LEARNER_LOG_ENABLED, enabled).apply()
    }

    /**
     * Startup gate for [cx.aswin.boxlore.core.ranking.LearningEventLog].
     *
     * - Debug: on when the pref is unset; a persisted toggle always wins.
     * - Release: **always off** unless the user has explicitly persisted `true`
     *   (debug-screen toggle via [setLearnerLogEnabled]). Never defaults on.
     */
    fun resolveLearnerLogEnabled(isDebugBuild: Boolean): Boolean {
        if (!isDebugBuild) {
            return prefs.contains(KEY_LEARNER_LOG_ENABLED) &&
                prefs.getBoolean(KEY_LEARNER_LOG_ENABLED, false)
        }
        return isLearnerLogEnabled(default = true)
    }

    companion object {
        private val syncDeviceIdLock = Any()

        /** Canonical SharedPreferences file name (migrated from `boxcast_prefs`). */
        const val PREFS_NAME = PrefsFileMigrator.Files.PREFS

        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_USER_GENRES = "user_genres"
        const val KEY_CACHED_RECOMMENDATIONS = "cached_recommendations"
        const val KEY_IS_RECOMMENDATIONS_FALLBACK = "is_recommendations_fallback"
        const val KEY_FEATURED_VIDEO_SHOWCASE_DISMISSED = "featured_video_showcase_dismissed"
        const val KEY_CACHED_BYL_RECOMMENDATIONS = "cached_byl_recommendations"
        const val KEY_CACHED_BYL_PODCASTS = "cached_byl_podcasts"
        const val KEY_CACHED_BYL_PODCAST_ID = "cached_byl_podcast_id"
        const val KEY_CACHED_BYL_SLOT = "cached_byl_slot"
        const val KEY_DISMISSED_CURIOSITIES = "dismissed_curiosities"
        const val KEY_LEARN_CURIOSITY_HISTORY = "learn_curiosity_history"
        const val KEY_LEARNER_LOG_ENABLED = "learner_log_enabled"
        const val KEY_PENDING_AUTH_EMAIL = "pending_auth_email"
        const val KEY_AWAITING_EMAIL_VERIFICATION = "awaiting_email_verification"
        const val KEY_SYNC_DEVICE_ID = "sync_device_id"
        const val KEY_LAST_SYNC_TIMESTAMP = "sync_last_timestamp"
        const val KEY_LAST_SYNCED_USER_ID = "sync_last_user_id"
        const val KEY_SYNC_METADATA_VERSION = "sync_metadata_version"
        const val KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION = "has_requested_notification_permission"
        const val KEY_FEEDBACK_DRAFT_CATEGORY = "feedback_draft_category"
        const val KEY_FEEDBACK_DRAFT_MESSAGE = "feedback_draft_message"
        const val KEY_FEEDBACK_DRAFT_EMAIL = "feedback_draft_email"
        const val KEY_FEEDBACK_DRAFT_STEPS = "feedback_draft_steps"
        const val KEY_FEEDBACK_DRAFT_ATTACH_DIAGNOSTICS = "feedback_draft_attach_diagnostics"
    }

    // ── Auth / Magic Link ───────────────────────────────────────────────────

    fun getPendingAuthEmail(): String? = prefs.getString(KEY_PENDING_AUTH_EMAIL, null)

    fun setPendingAuthEmail(email: String?) {
        if (email == null) {
            prefs.edit().remove(KEY_PENDING_AUTH_EMAIL).apply()
        } else {
            prefs.edit().putString(KEY_PENDING_AUTH_EMAIL, email).apply()
        }
    }

    fun isAwaitingEmailVerification(): Boolean = prefs.getBoolean(KEY_AWAITING_EMAIL_VERIFICATION, false)

    fun setAwaitingEmailVerification(awaiting: Boolean) {
        prefs.edit().putBoolean(KEY_AWAITING_EMAIL_VERIFICATION, awaiting).apply()
    }

    // ── Sync / Device Identity ──────────────────────────────────────────────

    fun getOrCreateSyncDeviceId(): String = synchronized(syncDeviceIdLock) {
        val existing = prefs.getString(KEY_SYNC_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return@synchronized existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SYNC_DEVICE_ID, newId).apply()
        newId
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply()
    }

    fun getLastSyncedUserId(): String? = prefs.getString(KEY_LAST_SYNCED_USER_ID, null)

    fun setLastSyncedUserId(userId: String?) {
        if (userId == null) {
            prefs.edit().remove(KEY_LAST_SYNCED_USER_ID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_SYNCED_USER_ID, userId).apply()
        }
    }

    fun getSyncMetadataVersion(): Int = prefs.getInt(KEY_SYNC_METADATA_VERSION, 0)

    fun setSyncMetadataVersion(version: Int) {
        prefs.edit().putInt(KEY_SYNC_METADATA_VERSION, version).apply()
    }

    // ── Notifications ───────────────────────────────────────────────────────

    fun hasRequestedNotificationPermission(): Boolean = prefs.getBoolean(KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION, false)

    fun setHasRequestedNotificationPermission(requested: Boolean = true) {
        prefs.edit().putBoolean(KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION, requested).apply()
    }

    // ── Feedback Draft ────────────────────────────────────────────────────────

    fun getFeedbackDraft(): FeedbackDraft? {
        val message = prefs.getString(KEY_FEEDBACK_DRAFT_MESSAGE, null).orEmpty()
        val steps = prefs.getString(KEY_FEEDBACK_DRAFT_STEPS, null).orEmpty()
        if (message.isBlank() && steps.isBlank()) return null
        val category = prefs.getString(KEY_FEEDBACK_DRAFT_CATEGORY, "feature") ?: "feature"
        val email = prefs.getString(KEY_FEEDBACK_DRAFT_EMAIL, "") ?: ""
        val attachDiagnostics = prefs.getBoolean(KEY_FEEDBACK_DRAFT_ATTACH_DIAGNOSTICS, true)
        return FeedbackDraft(
            category = category,
            message = message,
            email = email,
            stepsToReproduce = steps,
            attachDiagnostics = attachDiagnostics,
        )
    }

    fun saveFeedbackDraft(draft: FeedbackDraft) {
        prefs.edit()
            .putString(KEY_FEEDBACK_DRAFT_CATEGORY, draft.category)
            .putString(KEY_FEEDBACK_DRAFT_MESSAGE, draft.message)
            .putString(KEY_FEEDBACK_DRAFT_EMAIL, draft.email)
            .putString(KEY_FEEDBACK_DRAFT_STEPS, draft.stepsToReproduce)
            .putBoolean(KEY_FEEDBACK_DRAFT_ATTACH_DIAGNOSTICS, draft.attachDiagnostics)
            .apply()
    }

    fun clearFeedbackDraft() {
        prefs.edit()
            .remove(KEY_FEEDBACK_DRAFT_CATEGORY)
            .remove(KEY_FEEDBACK_DRAFT_MESSAGE)
            .remove(KEY_FEEDBACK_DRAFT_EMAIL)
            .remove(KEY_FEEDBACK_DRAFT_STEPS)
            .remove(KEY_FEEDBACK_DRAFT_ATTACH_DIAGNOSTICS)
            .apply()
    }
}

/**
 * Persisted draft for the user feedback screen to prevent data loss on accidental dismissal.
 */
data class FeedbackDraft(
    val category: String,
    val message: String,
    val email: String = "",
    val stepsToReproduce: String = "",
    val attachDiagnostics: Boolean = true,
)
