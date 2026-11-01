package cx.aswin.boxlore.analytics

import com.posthog.PostHogOnFeatureFlags

/** PostHog wiring for `BoxLoreApplication.startPostHog`, kept here so JVM tests can check it. */
internal object PostHogWiring {
    /**
     * Tags every event carries. Debug and isolated-test builds are internal, so PostHog's test-account
     * filter leaves them out; release builds say so explicitly.
     */
    fun environment(debug: Boolean, isolatedTests: Boolean,): Map<String, Any> =
        if (debug || isolatedTests) {
            mapOf("is_internal" to true, "app_environment" to if (isolatedTests) "update_test" else "debug")
        } else {
            mapOf("is_internal" to false, "app_environment" to "production")
        }

    /**
     * One flags-loaded callback that runs [steps] in order. A step that throws doesn't stop the rest;
     * the first failure is rethrown once all ran, with any later ones attached as suppressed.
     */
    fun onFlagsLoaded(vararg steps: () -> Unit): PostHogOnFeatureFlags =
        PostHogOnFeatureFlags {
            steps
                .mapNotNull { step -> runCatching(step).exceptionOrNull() }
                .reduceOrNull { first, next -> first.apply { addSuppressed(next) } }
                ?.let { failure -> throw failure }
        }
}
