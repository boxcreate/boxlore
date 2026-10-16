package cx.aswin.boxlore.core.analytics

/**
 * `api_session_status`: how this install gets and keeps its API session. One event per
 * `/auth/session` exchange (`kind = exchange`), per first silent renewal of a token or renewal
 * problem (`kind = renew`), and per Turnstile step-up (`kind = step_up`). Properties carry
 * categories only: never tokens, ids or keys.
 */
internal object ApiSessionAnalyticsTracks {
    const val EVENT = "api_session_status"

    @Suppress("LongParameterList") // One event shape shared by exchanges, renewals and step-ups.
    fun trackApiSessionStatus(
        kind: String,
        success: Boolean,
        trigger: String? = null,
        attestation: String? = null,
        limited: Boolean? = null,
        durationMs: Long? = null,
        appCheckAttempted: Boolean? = null,
        renewOutcome: String? = null,
        stepUpOutcome: String? = null,
        failureReason: String? = null,
        serverReason: String? = null,
    ) {
        val properties = buildMap<String, Any> {
            put("kind", kind)
            put("success", success)
            trigger?.let { put("trigger", it) }
            attestation?.let { put("attestation", it) }
            limited?.let { put("limited", it) }
            durationMs?.let { put("duration_ms", it) }
            appCheckAttempted?.let { put("app_check_attempted", it) }
            renewOutcome?.let { put("renew_outcome", it) }
            stepUpOutcome?.let { put("step_up_outcome", it) }
            failureReason?.let { put("failure_reason", it.take(MAX_REASON_LENGTH)) }
            serverReason?.let { put("server_reason", it.take(MAX_REASON_LENGTH)) }
        }
        AnalyticsEmit.event(EVENT, properties)
    }

    private const val MAX_REASON_LENGTH = 60
}
