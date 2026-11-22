package cx.aswin.boxlore.core.analytics

import android.content.Context
import android.content.SharedPreferences
import com.posthog.PostHog
import com.posthog.PostHogOnFeatureFlags
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import java.time.Instant

/**
 * What [AnalyticsHelper] keeps on this install: the first-launch mark, the event-levels switch
 * with its sample bucket, the feature-adoption values last sent and the watch's day being counted. Split out of the façade to keep that file within the source-size limit;
 * the façade's signatures are unchanged.
 */
internal object AnalyticsLocalState {
    private const val PREFS_NAME = PrefsFileMigrator.Files.ANALYTICS
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_SAMPLE_BUCKET = "analytics_sample_bucket"
    private const val KEY_POLICY = "analytics_policy"
    private const val KEY_FEATURE_ADOPTION = "feature_adoption_sent"
    private const val KEY_WATCH_LISTENING_DAY = "watch_listening_day"
    private const val KEY_WATCH_LISTENING_SENT = "watch_listening_sent_day"

    /** Where [startPolicy] keeps the bucket and switch, and the app version they belong to. */
    private class PolicyHome(val store: AnalyticsPolicy.Store, val appVersion: String)

    @Volatile
    private var policyHome: PolicyHome? = null

    /** Test hook: the payload PostHog holds for the switch. */
    @Volatile
    var policyPayload: () -> Any? = { PostHog.getFeatureFlagPayload(AnalyticsPolicy.FLAG_KEY, null) }

    fun policyStore(context: Context): AnalyticsPolicy.Store = PrefsPolicyStore(prefs(context))

    fun featureAdoptionStore(context: Context): FeatureAdoptionAnalytics.Store = PrefsFeatureAdoptionStore(prefs(context))

    fun watchListeningStore(context: Context): WatchListeningSummary.Store = PrefsWatchListeningStore(prefs(context))

    fun startPolicy(store: AnalyticsPolicy.Store, appVersion: String) {
        policyHome = PolicyHome(store, appVersion)
        AnalyticsPolicy.start(store, appVersion)
    }

    fun refreshPolicy() {
        val home = policyHome ?: return
        AnalyticsPolicy.refresh(policyPayload(), home.appVersion, home.store)
    }

    fun flagsReloaded(onLoaded: () -> Unit): PostHogOnFeatureFlags =
        PostHogOnFeatureFlags {
            try {
                refreshPolicy()
            } finally {
                onLoaded()
            }
        }

    /** Test hook: no store yet, payload read from PostHog. */
    fun resetPolicy() {
        policyHome = null
        policyPayload = { PostHog.getFeatureFlagPayload(AnalyticsPolicy.FLAG_KEY, null) }
    }

    /** Sets the first-seen person properties on the very first launch, once. */
    fun noteFirstLaunch(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_FIRST_LAUNCH, true)) return
        AnalyticsEmit.personSet(
            mapOf(
                "first_seen_at" to Instant.now().toString(),
                "onboarding_status" to "pending",
            ),
        )
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    private class PrefsPolicyStore(private val prefs: SharedPreferences) : AnalyticsPolicy.Store {
        override fun bucket(): Int? = prefs.getInt(KEY_SAMPLE_BUCKET, -1).takeIf { it >= 0 }

        override fun saveBucket(bucket: Int) = prefs.edit().putInt(KEY_SAMPLE_BUCKET, bucket).apply()

        override fun rules(): String? = prefs.getString(KEY_POLICY, null)

        override fun saveRules(encoded: String) = prefs.edit().putString(KEY_POLICY, encoded).apply()
    }

    private class PrefsFeatureAdoptionStore(private val prefs: SharedPreferences) : FeatureAdoptionAnalytics.Store {
        override fun lastSent(): String? = prefs.getString(KEY_FEATURE_ADOPTION, null)

        override fun saveSent(encoded: String) = prefs.edit().putString(KEY_FEATURE_ADOPTION, encoded).apply()
    }

    private class PrefsWatchListeningStore(private val prefs: SharedPreferences) : WatchListeningSummary.Store {
        override fun load(): WatchListeningSummary.Day? = WatchListeningSummary.decode(prefs.getString(KEY_WATCH_LISTENING_DAY, null))

        override fun save(day: WatchListeningSummary.Day?) {
            val edit = prefs.edit()
            if (day == null) edit.remove(KEY_WATCH_LISTENING_DAY) else edit.putString(KEY_WATCH_LISTENING_DAY, WatchListeningSummary.encode(day))
            edit.apply()
        }

        override fun lastSent(): Long? = prefs.getLong(KEY_WATCH_LISTENING_SENT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }

        override fun saveLastSent(epochDay: Long) = prefs.edit().putLong(KEY_WATCH_LISTENING_SENT, epochDay).apply()
    }

    private fun prefs(context: Context) =
        PrefsFileMigrator.open(
            context,
            newName = PREFS_NAME,
            oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS,
        )
}
