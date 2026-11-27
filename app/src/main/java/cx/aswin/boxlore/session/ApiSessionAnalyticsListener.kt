package cx.aswin.boxlore.session

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.ApiSessionStatus
import cx.aswin.boxlore.core.network.session.ApiAttestation
import cx.aswin.boxlore.core.network.session.ApiSessionExchangeEvent
import cx.aswin.boxlore.core.network.session.ApiSessionListener
import cx.aswin.boxlore.core.network.session.ApiSessionRenewOutcome
import cx.aswin.boxlore.core.network.session.ApiSessionStepUpOutcome

/**
 * Forwards session events to `api_session_status`, and each App Check attempt (they happen only
 * for a session sign-in now, which may then send no exchange) to `app_check_status` with its
 * failure reason.
 */
internal class ApiSessionAnalyticsListener(private val appCheckProvider: String) : ApiSessionListener {
    override fun onAppCheck(obtained: Boolean, failure: String?) {
        AnalyticsHelper.trackAppCheckStatus(tokenObtained = obtained, provider = appCheckProvider, reason = failure)
    }

    override fun onExchange(event: ApiSessionExchangeEvent) {
        AnalyticsHelper.trackApiSessionStatus(
            ApiSessionStatus(
                kind = "exchange",
                success = event.success,
                trigger = event.trigger.wire,
                attestation = event.attestation?.wire,
                limited = event.limited,
                durationMs = event.durationMs,
                appCheckAttempted = event.appCheckAttempted,
                failureReason = event.failureReason,
                serverReason = event.serverReason,
                turnstile = event.turnstile,
            ),
        )
    }

    override fun onRenewal(outcome: ApiSessionRenewOutcome, attestation: ApiAttestation) {
        AnalyticsHelper.trackApiSessionStatus(
            ApiSessionStatus(
                kind = "renew",
                success = outcome == ApiSessionRenewOutcome.Renewed,
                attestation = attestation.wire,
                renewOutcome = outcome.wire,
            ),
        )
    }

    override fun onStepUp(outcome: ApiSessionStepUpOutcome, reason: String?, durationMs: Long) {
        AnalyticsHelper.trackApiSessionStatus(
            ApiSessionStatus(
                kind = "step_up",
                success = outcome == ApiSessionStepUpOutcome.Passed,
                durationMs = durationMs,
                stepUpOutcome = outcome.wire,
                failureReason = reason,
            ),
        )
    }
}
