package cx.aswin.boxlore.core.analytics

/**
 * One `api_session_status` event: an API session exchange (`kind = exchange`), the first silent
 * renewal of a token or a renewal problem (`kind = renew`), or a Turnstile step-up
 * (`kind = step_up`). Null properties are left out. Categories only: never tokens, ids or keys.
 */
data class ApiSessionStatus(
    val kind: String,
    val success: Boolean,
    val trigger: String? = null,
    val attestation: String? = null,
    val limited: Boolean? = null,
    val durationMs: Long? = null,
    val appCheckAttempted: Boolean? = null,
    val renewOutcome: String? = null,
    val stepUpOutcome: String? = null,
    val failureReason: String? = null,
    val serverReason: String? = null,
)

/** `api_session_status`: how this install gets and keeps its API session. */
internal object ApiSessionAnalyticsTracks {
    const val EVENT = "api_session_status"

    fun trackApiSessionStatus(status: ApiSessionStatus) {
        val properties = buildMap<String, Any> {
            put("kind", status.kind)
            put("success", status.success)
            status.trigger?.let { put("trigger", it) }
            status.attestation?.let { put("attestation", it) }
            status.limited?.let { put("limited", it) }
            status.durationMs?.let { put("duration_ms", it) }
            status.appCheckAttempted?.let { put("app_check_attempted", it) }
            status.renewOutcome?.let { put("renew_outcome", it) }
            status.stepUpOutcome?.let { put("step_up_outcome", it) }
            status.failureReason?.let { put("failure_reason", it.take(MAX_REASON_LENGTH)) }
            status.serverReason?.let { put("server_reason", it.take(MAX_REASON_LENGTH)) }
        }
        AnalyticsEmit.event(EVENT, properties)
    }

    private const val MAX_REASON_LENGTH = 60
}
