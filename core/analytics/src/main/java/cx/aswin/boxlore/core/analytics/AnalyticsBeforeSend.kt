package cx.aswin.boxlore.core.analytics

import com.posthog.PostHogBeforeSend
import com.posthog.PostHogEvent

/**
 * Runs when PostHog captures an event, before it joins the SDK's upload queue, SDK lifecycle events
 * included. It drops events this install doesn't send ([AnalyticsPolicy.admitCaptured]) and stamps
 * this build's [environment] tags (`is_internal`, `app_environment`), replacing any an event carries.
 *
 * - Events PostHog records during its own setup, such as `Application Installed` and `Application
 *   Updated`, are captured before `register` runs, so without this they arrive untagged or with the
 *   tags the previous build saved, and a test install over a release one looks real.
 * - Events already in the upload queue, for example from an offline stretch, aren't checked again
 *   when they're uploaded; a switch change applies to events captured after it.
 * - PostHog runs this twice per capture, on the capturing thread, so it must give the same answer
 *   for an event it already processed.
 */
class AnalyticsBeforeSend(private val environment: Map<String, Any>) : PostHogBeforeSend {
    override fun run(event: PostHogEvent): PostHogEvent? {
        val properties = event.properties.orEmpty()
        val extra = AnalyticsPolicy.admitCaptured(event.event, properties) ?: return null
        val tagsDiffer = environment.any { (key, value) -> properties[key] != value }
        if (extra.isEmpty() && !tagsDiffer) return event
        return event.copy(properties = (properties + environment + extra).toMutableMap())
    }
}
