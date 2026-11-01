package cx.aswin.boxlore.core.analytics

import android.content.Context
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry

/**
 * Test-double [Analytics] implementation that records every captured event in-memory.
 * Suitable for use in unit tests and `:core:testing` helpers — no PostHog SDK required.
 *
 * Usage:
 * ```kotlin
 * val analytics = RecordingAnalytics()
 * analytics.capture("my_event", mapOf("key" to "value"))
 * assertEquals(1, analytics.eventCount("my_event"))
 * ```
 */
class RecordingAnalytics : Analytics {
    data class CapturedEvent(val name: String, val properties: Map<String, Any>,)

    private val _events = mutableListOf<CapturedEvent>()

    /** All events captured since construction (or last [clear]). */
    val events: List<CapturedEvent> get() = _events.toList()

    /** Returns the number of times [eventName] was captured. */
    fun eventCount(eventName: String): Int = _events.count { it.name == eventName }

    /** Returns all captured events with the given [eventName]. */
    fun eventsNamed(eventName: String): List<CapturedEvent> = _events.filter { it.name == eventName }

    /** Returns the last captured event, or null if none. */
    val lastEvent: CapturedEvent? get() = _events.lastOrNull()

    /** Clears all recorded events. */
    fun clear() = _events.clear()

    // ── Analytics interface ────────────────────────────────────────

    override fun capture(event: String, properties: Map<String, Any>,) {
        if (!AnalyticsGlossary.isAllowedEvent(event)) return
        _events.add(CapturedEvent(event, properties))
    }

    override fun trackFirstLaunchIfNecessary(context: Context) {
        // Mirror production: person props only — never emit app_open (SDK-backed).
    }

    override fun flush() {
        // no-op in recording mode
    }

    /** Phase C — `adaptive_ranking_status`. */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        val statusSummary =
            statuses
                .joinToString(separator = ",") { "${it.objective}:${it.learningStage}" }
                .ifBlank { "empty" }
        capture(
            "adaptive_ranking_status",
            mapOf(
                "status" to statusSummary,
                "details" to "count=${statuses.size}",
            ),
        )
    }

    override fun trackEngagementPromptShown(promptType: String, source: String, completedEpisodes: Int?, npsScore: Int?,) {
        capture(
            "engagement_prompt_shown",
            buildMap {
                put("prompt_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
                npsScore?.let { put("nps_score", it) }
            },
        )
    }

    override fun trackFirstEpisodePlayed(episodeId: String?, podcastId: String?) {
        capture(
            "first_episode_played",
            buildMap {
                episodeId?.let { put("episode_id", it) }
                podcastId?.let { put("podcast_id", it) }
            },
        )
    }

    override fun trackAppCheckStatus(tokenObtained: Boolean, provider: String, reason: String?,) {
        capture(
            "app_check_status",
            buildMap<String, Any> {
                put("token_obtained", tokenObtained)
                put("provider", provider)
                reason?.let { put("reason", it) }
            },
        )
    }
}
