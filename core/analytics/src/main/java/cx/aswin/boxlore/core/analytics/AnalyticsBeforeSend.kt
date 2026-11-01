package cx.aswin.boxlore.core.analytics

import com.posthog.PostHogBeforeSend
import com.posthog.PostHogEvent

/**
 * Runs on every event just before PostHog sends it, SDK lifecycle events included. It drops events
 * this install doesn't send ([AnalyticsPolicy]) and stamps the [environment] tags (`is_internal`,
 * `app_environment`) where an event lacks them. Events PostHog records during its own setup, such as
 * `Application Installed`, are captured before `register` runs, so without this they arrive untagged
 * and test installs look like real ones.
 */
class AnalyticsBeforeSend(private val environment: Map<String, Any>) : PostHogBeforeSend {
    override fun run(event: PostHogEvent): PostHogEvent? {
        val extra = AnalyticsPolicy.admit(event.event) ?: return null
        val properties = event.properties.orEmpty()
        val missingTags = environment.filterKeys { it !in properties }
        if (extra.isEmpty() && missingTags.isEmpty()) return event
        return event.copy(properties = (properties + missingTags + extra).toMutableMap())
    }
}
