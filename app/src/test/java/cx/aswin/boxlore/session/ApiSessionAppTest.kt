package cx.aswin.boxlore.session

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.network.session.ApiAttestation
import cx.aswin.boxlore.core.network.session.ApiSessionExchangeEvent
import cx.aswin.boxlore.core.network.session.ApiSessionRenewOutcome
import cx.aswin.boxlore.core.network.session.ApiSessionStepUpOutcome
import cx.aswin.boxlore.core.network.session.ApiSessionTrigger
import java.io.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** App wiring for API sessions: install channel, App Check failure reasons and analytics. */
class ApiSessionAppTest {
    private val recorder = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @BeforeEach
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(recorder)
    }

    @AfterEach
    fun tearDown() = restore()

    @Test
    fun `channel follows the update rule`() {
        var asked = 0
        assertEquals(
            "play",
            InstallChannel.of(directUpdates = false) {
                asked++
                false
            },
        )
        assertEquals(0, asked, "the Play build never asks the installer")
        assertEquals("play", InstallChannel.of(directUpdates = true) { true })
        assertEquals("github", InstallChannel.of(directUpdates = true) { false })
    }

    @Test
    fun `App Check failures become short categories`() {
        val integrity = RuntimeException("-9: Integrity API error (-9): Binding to the service in the Play Store has failed.")
        assertEquals("play_integrity_-9", AppCheckFailureReason.of(RuntimeException("App Check failed", integrity)))
        assertEquals("play_integrity_-1", AppCheckFailureReason.of(IllegalStateException("-1: API not available")))
        assertEquals("http_403", AppCheckFailureReason.of(RuntimeException("Error returned from API. code: 403 body: App attestation failed.")))
        assertEquals("too_many_attempts", AppCheckFailureReason.of(RuntimeException("Too many attempts.")))
        assertEquals("network", AppCheckFailureReason.of(RuntimeException("wrapped", IOException("reset"))))
        assertEquals("other", AppCheckFailureReason.of(RuntimeException("something else")))
        assertEquals("other", AppCheckFailureReason.of(null))
    }

    @Test
    fun `an exchange reports the App Check attempt and the session status`() {
        val listener = ApiSessionAnalyticsListener(appCheckProvider = "play_integrity")

        listener.onExchange(
            ApiSessionExchangeEvent(
                trigger = ApiSessionTrigger.Launch,
                success = true,
                durationMs = 840,
                attestation = ApiAttestation.None,
                limited = true,
                appCheckAttempted = true,
                appCheckObtained = false,
                appCheckFailure = "play_integrity_-9",
                failureReason = null,
                serverReason = "appcheck:missing",
            ),
        )

        assertEquals(
            "app_check_status" to mapOf("token_obtained" to false, "provider" to "play_integrity", "reason" to "play_integrity_-9"),
            recorder[0],
        )
        assertEquals(
            "api_session_status" to mapOf(
                "kind" to "exchange",
                "success" to true,
                "trigger" to "launch",
                "attestation" to "none",
                "limited" to true,
                "duration_ms" to 840L,
                "app_check_attempted" to true,
                "server_reason" to "appcheck:missing",
            ),
            recorder[1],
        )
    }

    @Test
    fun `an exchange without App Check sends only the session status`() {
        ApiSessionAnalyticsListener("debug").onExchange(
            ApiSessionExchangeEvent(ApiSessionTrigger.Expired, false, 12, null, null, false, false, null, "network", null),
        )

        assertEquals(listOf("api_session_status"), recorder.map { it.first })
        assertEquals("network", recorder.single().second["failure_reason"])
        assertFalse("attestation" in recorder.single().second)
    }

    @Test
    fun `renewals and step-ups report their outcome`() {
        val listener = ApiSessionAnalyticsListener("play_integrity")

        listener.onRenewal(ApiSessionRenewOutcome.Renewed, ApiAttestation.AppCheck)
        listener.onStepUp(ApiSessionStepUpOutcome.ChallengeFailed, "cancelled", 4_200)

        assertEquals(
            mapOf("kind" to "renew", "success" to true, "attestation" to "appcheck", "renew_outcome" to "renewed"),
            recorder[0].second,
        )
        assertEquals(
            mapOf("kind" to "step_up", "success" to false, "duration_ms" to 4_200L, "step_up_outcome" to "challenge_failed", "failure_reason" to "cancelled"),
            recorder[1].second,
        )
    }
}
