package cx.aswin.boxlore.core.analytics

import android.content.Context
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry

/**
 * Façade for analytics event capture. New call-sites should depend on this interface
 * rather than [AnalyticsHelper] directly, enabling substitution with [RecordingAnalytics]
 * in tests and integration points.
 *
 * The current [AnalyticsHelper] object implements this interface so existing call-sites
 * continue to work without changes.
 */
interface Analytics {
    /** Capture a generic event with an arbitrary property map. */
    fun capture(event: String, properties: Map<String, Any> = emptyMap(),)

    /** First-launch detection and user-property initialisation. */
    fun trackFirstLaunchIfNecessary(context: Context)

    /** Flush buffered events to the PostHog server. */
    fun flush()

    fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>)

    /** An engagement prompt (a review prompt) appeared: `engagement_prompt_shown`. */
    fun trackEngagementPromptShown(promptType: String, source: String, completedEpisodes: Int? = null, npsScore: Int? = null,)

    /** The NPS survey's trigger: `feedback_submitted`, which the survey's display condition keys off. */
    fun trackSurveyNpsEligible(completedEpisodes: Int?, triggerContext: String,)

    fun trackSurveyNpsManualTrigger(source: String)

    /** The first play on this install, with the episode playing when known. */
    fun trackFirstEpisodePlayed(episodeId: String? = null, podcastId: String? = null,)

    /** One App Check attempt; [reason] is a short failure category (null when a token was obtained). */
    fun trackAppCheckStatus(tokenObtained: Boolean, provider: String, reason: String? = null,)
}
