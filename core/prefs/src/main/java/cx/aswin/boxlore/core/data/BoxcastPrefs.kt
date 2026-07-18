package cx.aswin.boxlore.core.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed façade over the legacy SharedPreferences file [PREFS_NAME] (`boxcast_prefs`).
 *
 * Features and core helpers must use this API instead of calling
 * `context.getSharedPreferences("boxcast_prefs", …)` directly.
 * Key names and the file name are identity-stable for existing installs.
 */
class BoxcastPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Onboarding ──────────────────────────────────────────────────────────

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    // ── User genres / interests ──────────────────────────────────────────────

    fun getUserGenres(): Set<String> =
        prefs.getStringSet(KEY_USER_GENRES, emptySet()) ?: emptySet()

    fun setUserGenres(genres: Set<String>) {
        prefs.edit().putStringSet(KEY_USER_GENRES, genres).apply()
    }

    // ── Home / explore recommendation cache ─────────────────────────────────

    fun getCachedRecommendationsJson(): String? =
        prefs.getString(KEY_CACHED_RECOMMENDATIONS, null)

    fun isRecommendationsFallback(default: Boolean = true): Boolean =
        prefs.getBoolean(KEY_IS_RECOMMENDATIONS_FALLBACK, default)

    fun saveRecommendationsCache(serializedJson: String, isFallback: Boolean) {
        prefs.edit()
            .putString(KEY_CACHED_RECOMMENDATIONS, serializedJson)
            .putBoolean(KEY_IS_RECOMMENDATIONS_FALLBACK, isFallback)
            .apply()
    }

    fun setCachedRecommendationsJson(serializedJson: String) {
        prefs.edit().putString(KEY_CACHED_RECOMMENDATIONS, serializedJson).apply()
    }

    // ── Because-you-like cache ──────────────────────────────────────────────

    fun getCachedBylRecommendationsJson(): String? =
        prefs.getString(KEY_CACHED_BYL_RECOMMENDATIONS, null)

    fun getCachedBylPodcastsJson(): String? =
        prefs.getString(KEY_CACHED_BYL_PODCASTS, null)

    fun getCachedBylPodcastId(): String? =
        prefs.getString(KEY_CACHED_BYL_PODCAST_ID, null)

    fun saveBylCache(
        episodesJson: String,
        podcastsJson: String,
        podcastId: String,
    ) {
        prefs.edit()
            .putString(KEY_CACHED_BYL_RECOMMENDATIONS, episodesJson)
            .putString(KEY_CACHED_BYL_PODCASTS, podcastsJson)
            .putString(KEY_CACHED_BYL_PODCAST_ID, podcastId)
            .apply()
    }

    // ── Learn curiosity history ─────────────────────────────────────────────

    fun getLearnCuriosityHistoryJson(): String? =
        prefs.getString(KEY_LEARN_CURIOSITY_HISTORY, null)

    fun setLearnCuriosityHistoryJson(json: String?) {
        prefs.edit().apply {
            if (json == null) remove(KEY_LEARN_CURIOSITY_HISTORY)
            else putString(KEY_LEARN_CURIOSITY_HISTORY, json)
            apply()
        }
    }

    fun getDismissedCuriosityIds(): Set<String> =
        prefs.getStringSet(KEY_DISMISSED_CURIOSITIES, emptySet()) ?: emptySet()

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

    fun isLearnerLogEnabled(default: Boolean = false): Boolean =
        prefs.getBoolean(KEY_LEARNER_LOG_ENABLED, default)

    fun setLearnerLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LEARNER_LOG_ENABLED, enabled).apply()
    }

    companion object {
        /** SharedPreferences file name — do not rename (existing installs). */
        const val PREFS_NAME = "boxcast_prefs"

        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_USER_GENRES = "user_genres"
        const val KEY_CACHED_RECOMMENDATIONS = "cached_recommendations"
        const val KEY_IS_RECOMMENDATIONS_FALLBACK = "is_recommendations_fallback"
        const val KEY_CACHED_BYL_RECOMMENDATIONS = "cached_byl_recommendations"
        const val KEY_CACHED_BYL_PODCASTS = "cached_byl_podcasts"
        const val KEY_CACHED_BYL_PODCAST_ID = "cached_byl_podcast_id"
        const val KEY_DISMISSED_CURIOSITIES = "dismissed_curiosities"
        const val KEY_LEARN_CURIOSITY_HISTORY = "learn_curiosity_history"
        const val KEY_LEARNER_LOG_ENABLED = "learner_log_enabled"
    }
}
