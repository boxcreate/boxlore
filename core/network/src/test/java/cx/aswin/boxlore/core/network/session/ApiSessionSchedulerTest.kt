package cx.aswin.boxlore.core.network.session

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Background upkeep in virtual time: a limited session retries App Check about a minute after the
 * first failure, then hourly, then daily, only in the foreground with a network, and swaps in the
 * full session silently when App Check passes. Those retries give slow App Check longer than launch
 * does, and run the Turnstile check for a limited session, never for a challenge one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiSessionSchedulerTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    private class Harness(
        scope: TestScope,
        stored: StoredApiSession,
        appCheckPassesOnAttempt: Int,
        appCheckDelayMs: Long = 0L,
        check: TurnstileTokenSource? = null,
    ) {
        val appCheckCalls = AtomicInteger()
        val full = sessionToken(sid = "full", att = "appcheck", tag = "full")
        val challenge = sessionToken(sid = "challenge", att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = "challenge")
        val exchanger = FakeExchanger { request ->
            when {
                request.appCheckToken != null -> issued(full)
                request.turnstileToken != null -> issued(challenge)
                else -> issued(sessionToken(sid = "limited-${request.hashCode()}", att = "none"))
            }
        }
        val store = FakeStore(stored)
        val manager = ApiSessionManager(
            apiBaseUrl = "https://api.boxlore.example/",
            exchanger = exchanger,
            store = store,
            deviceKey = FakeDeviceKey(),
            appCheck = {
                val attempt = appCheckCalls.incrementAndGet()
                delay(appCheckDelayMs)
                if (attempt >= appCheckPassesOnAttempt) AppCheckResult.Token("ok") else AppCheckResult.Failed("play_integrity_-9")
            },
            scope = scope.backgroundScope,
            clock = { BASE_MS + scope.testScheduler.currentTime },
        ).also { it.turnstile = check }

        fun heldToken(): String? = manager.headersForRequest()?.token
    }

    private fun limitedStored() = StoredApiSession(
        token = sessionToken(sid = "limited-0", att = "none", iat = BASE_S, exp = BASE_S + DAY_S),
        installId = "install-123",
        appCheckFailures = 1,
        nextUpgradeAtMs = BASE_MS + minute,
    )

    @Test
    fun `a limited session upgrades in the background with backoff`() = runTest {
        val harness = Harness(this, limitedStored(), appCheckPassesOnAttempt = 3)
        harness.manager.start()
        runCurrent()
        harness.manager.onForegroundChanged(true)
        runCurrent()

        testScheduler.advanceTimeBy(minute - 1_000)
        runCurrent()
        assertEquals(0, harness.appCheckCalls.get(), "nothing before the first minute")

        testScheduler.advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, harness.appCheckCalls.get(), "first retry after about a minute")

        testScheduler.advanceTimeBy(hour - 10 * minute)
        runCurrent()
        assertEquals(1, harness.appCheckCalls.get(), "then hourly")
        testScheduler.advanceTimeBy(11 * minute)
        runCurrent()
        assertEquals(2, harness.appCheckCalls.get())

        testScheduler.advanceTimeBy(hour + minute)
        runCurrent()
        assertEquals(3, harness.appCheckCalls.get())
        assertEquals(harness.full, harness.heldToken(), "the full session replaces the limited one silently")
        assertEquals(0, harness.store.state.appCheckFailures)
    }

    @Test
    fun `slow App Check gives up at launch but has time to pass on the background upgrade`() = runTest {
        // Play Integrity takes 10 s on this phone: longer than the 6 s launch limit, within the 30 s background one.
        val harness = Harness(this, StoredApiSession(), appCheckPassesOnAttempt = 1, appCheckDelayMs = 10_000)
        harness.manager.start()
        runCurrent()
        testScheduler.advanceTimeBy(6_001)
        runCurrent()
        assertNull(harness.exchanger.requests.single().appCheckToken, "launch signs in without App Check after 6 s")
        assertNotEquals(harness.full, harness.heldToken(), "so the first session is limited")

        harness.manager.onForegroundChanged(true)
        runCurrent()
        testScheduler.advanceTimeBy(minute + 11_000)
        runCurrent()
        assertEquals(2, harness.appCheckCalls.get())
        assertEquals("ok", harness.exchanger.requests.last().appCheckToken, "the background retry waits for App Check")
        assertEquals(harness.full, harness.heldToken())
    }

    @Test
    fun `nothing runs in the background or offline`() = runTest {
        val harness = Harness(this, limitedStored(), appCheckPassesOnAttempt = 1)
        harness.manager.start()
        runCurrent()

        testScheduler.advanceTimeBy(2 * hour)
        runCurrent()
        assertEquals(0, harness.appCheckCalls.get(), "background")

        harness.manager.onNetworkChanged(false)
        harness.manager.onForegroundChanged(true)
        testScheduler.advanceTimeBy(2 * hour)
        runCurrent()
        assertEquals(0, harness.appCheckCalls.get(), "offline")

        harness.manager.onNetworkChanged(true)
        runCurrent()
        assertEquals(1, harness.appCheckCalls.get(), "an overdue retry runs as soon as the network is back")
        assertEquals(harness.full, harness.heldToken())

        harness.manager.onForegroundChanged(false)
        testScheduler.advanceTimeBy(40L * 24 * hour)
        runCurrent()
        assertEquals(1, harness.appCheckCalls.get(), "a backgrounded app never re-attests")
    }

    @Test
    fun `a limited session's upgrade runs the check when App Check fails, a challenge session's never does`() = runTest {
        val solves = AtomicInteger()
        val check = TurnstileTokenSource {
            solves.incrementAndGet()
            TurnstileResult.Token("ts")
        }
        val harness = Harness(this, limitedStored(), appCheckPassesOnAttempt = Int.MAX_VALUE, check = check)
        harness.manager.start()
        runCurrent()
        harness.manager.onForegroundChanged(true)
        runCurrent()

        testScheduler.advanceTimeBy(minute + 1_000)
        runCurrent()
        assertEquals(1, harness.appCheckCalls.get())
        assertEquals(1, solves.get(), "App Check failed, so the upgrade asked Turnstile in the same exchange")
        assertEquals(harness.challenge, harness.heldToken())

        testScheduler.advanceTimeBy(hour + minute)
        runCurrent()
        assertEquals(2, harness.appCheckCalls.get(), "App Check keeps retrying to upgrade the challenge session")
        assertEquals(1, solves.get(), "but a challenge session needs no new check")
        assertEquals(harness.challenge, harness.heldToken())
    }

    @Test
    fun `plans the next background exchange from the held session`() {
        val book = ApiSessionBook { BASE_MS }
        assertEquals(ApiSessionStep(BASE_MS, ApiSessionTrigger.Retry, useAppCheck = true), ApiSessionScheduler.nextStep(book, BASE_MS))

        book.recordExchangeFailure(retryAfterSeconds = null)
        assertEquals(BASE_MS + 2_000, ApiSessionScheduler.nextStep(book, BASE_MS).atMs, "a missing session follows the exchange backoff")

        book.recordExchangeSuccess()
        book.hold(ApiSessionToken.parse(sessionToken(att = "appcheck", iat = BASE_S, attExp = BASE_S + 30 * DAY_S))!!)
        val refresh = ApiSessionScheduler.nextStep(book, BASE_MS)
        assertEquals(ApiSessionTrigger.Refresh, refresh.trigger)
        assertTrue(refresh.useAppCheck)
        assertEquals(BASE_MS + 29 * DAY_S * 1_000, refresh.atMs, "App Check sessions re-attest a day before the window ends")

        book.hold(ApiSessionToken.parse(sessionToken(att = "none", iat = BASE_S, exp = BASE_S + DAY_S))!!)
        book.recordAppCheck(passed = false)
        val upgrade = ApiSessionScheduler.nextStep(book, BASE_MS)
        assertEquals(ApiSessionStep(BASE_MS + minute, ApiSessionTrigger.Upgrade, useAppCheck = true), upgrade)

        repeat(5) { book.recordAppCheck(passed = false) }
        val plain = ApiSessionScheduler.nextStep(book, BASE_MS)
        assertEquals(ApiSessionTrigger.Refresh, plain.trigger, "with App Check on a daily retry, the limited session is refreshed first")
        assertFalse(plain.useAppCheck)
        assertEquals(BASE_MS + (DAY_S - 3_600) * 1_000, plain.atMs)

        book.lastMaintenanceAtMs = BASE_MS + (DAY_S - 3_600) * 1_000
        assertEquals(book.lastMaintenanceAtMs + ApiSessionScheduler.MIN_SPACING_MS, ApiSessionScheduler.nextStep(book, BASE_MS).atMs, "background exchanges are spaced")
    }

    @Test
    fun `a watch or TV trades as soon as it is signed in without a paired session, then an hour before that one ends, never asking App Check`() {
        val book = ApiSessionBook { BASE_MS }
        book.hold(ApiSessionToken.parse(sessionToken(att = "none", iat = BASE_S, exp = BASE_S + DAY_S))!!)
        book.recordAppCheck(passed = false)
        assertEquals(ApiSessionStep(BASE_MS, ApiSessionTrigger.Retry, useAppCheck = false), ApiSessionScheduler.nextStep(book, BASE_MS, pairedSignedIn = true), "signed in with a limited session: trade now")
        assertEquals(
            ApiSessionStep(BASE_MS + (DAY_S - 3_600) * 1_000, ApiSessionTrigger.Refresh, useAppCheck = false),
            ApiSessionScheduler.nextStep(book, BASE_MS, pairedSignedIn = false),
            "signed out: no App Check upgrade, only a fresh limited session before this one ends",
        )

        book.recordExchangeFailure(retryAfterSeconds = 60)
        assertEquals(BASE_MS + 60_000, ApiSessionScheduler.nextStep(book, BASE_MS, pairedSignedIn = true).atMs, "a refused trade (429, 503) waits out the backoff")

        book.recordExchangeSuccess()
        book.hold(ApiSessionToken.parse(sessionToken(att = "paired", iat = BASE_S, exp = BASE_S + DAY_S))!!)
        assertEquals(
            ApiSessionStep(BASE_MS + (DAY_S - 3_600) * 1_000, ApiSessionTrigger.Refresh, useAppCheck = false),
            ApiSessionScheduler.nextStep(book, BASE_MS, pairedSignedIn = true),
            "a paired session (a day, no renewal) is traded again an hour before it ends",
        )
    }
}
