package cx.aswin.boxcast.core.data.analytics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory telemetry aggregator for BoxCast Insight Engine.
 * Instead of firing database writes for every interaction, this object tallies
 * metrics in-memory and generates a batch JSON payload when flushed.
 */
object SessionAggregator {
    
    // In-memory tally of daily aggregate metrics
    private val dailyAggregates = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    // In-memory tally of podcast-specific intelligence metrics
    // Format: Map<PodcastId, Map<MetricKey, Value>>
    private val podcastIntelligence = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    /**
     * Increment a global daily metric (e.g. action_skip_forward, funnel_search, rage_tap)
     */
    fun incrementAggregate(metricKey: String, amount: Int = 1) {
        dailyAggregates.update { current ->
            val count = current[metricKey] ?: 0
            current + (metricKey to (count + amount))
        }
    }

    /**
     * Increment a podcast-specific metric (e.g. podcast_plays, podcast_abandoned, milestone_50)
     */
    fun incrementPodcastMetric(podcastId: String, metricKey: String, amount: Int = 1) {
        podcastIntelligence.update { current ->
            val podMetrics = current[podcastId]?.toMutableMap() ?: mutableMapOf()
            val count = podMetrics[metricKey] ?: 0
            podMetrics[metricKey] = count + amount
            
            val newMap = current.toMutableMap()
            newMap[podcastId] = podMetrics
            newMap
        }
    }

    /**
     * Set a boolean podcast flag (converts to 1 for SQL integer storage)
     */
    fun setPodcastFlag(podcastId: String, flagKey: String) {
        incrementPodcastMetric(podcastId, flagKey, 1)
    }

    /**
     * Compile the current tallies into a JSON-friendly map and CLEAR the aggregator.
     * This is called by AppHealthReporter before making the HTTP batch request.
     */
    fun flushToPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>()
        
        val aggregates = dailyAggregates.value
        if (aggregates.isNotEmpty()) {
            payload["daily_aggregates"] = aggregates
        }
        
        val intel = podcastIntelligence.value
        if (intel.isNotEmpty()) {
            payload["podcast_intelligence"] = intel
        }
        
        // Reset tallies after generating payload
        dailyAggregates.value = emptyMap()
        podcastIntelligence.value = emptyMap()
        
        return payload
    }
}
