package cx.aswin.boxlore.core.network.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiSessionPolicyTest {
    private fun parse(raw: String) = requireNotNull(ApiSessionToken.parse(raw)) { "unparseable test token" }

    @Test
    fun `parses the claims the app needs from a session token`() {
        val token = parse(sessionToken(sid = "abc", att = "challenge", iat = 100, exp = 200, attExp = 900))

        assertEquals("abc", token.sid)
        assertEquals(ApiAttestation.Challenge, token.attestation)
        assertEquals(100, token.issuedAt)
        assertEquals(200, token.expiresAt)
        assertEquals(900, token.attestedUntil)
        assertTrue(token.hasDeviceKey)
        assertTrue(token.renewable)
        assertFalse(token.limited)
        assertFalse(token.toString().contains("bls1"), "toString must never print the token")
    }

    @Test
    fun `limited and key-less sessions never renew`() {
        assertTrue(parse(sessionToken(att = "none")).limited)
        assertFalse(parse(sessionToken(att = "none")).renewable)
        assertFalse(parse(sessionToken(deviceKey = false, exp = BASE_S + 10, attExp = BASE_S + 99)).renewable)
    }

    @Test
    fun `rejects malformed tokens and treats unknown attestation as the weakest`() {
        assertNull(ApiSessionToken.parse(null))
        assertNull(ApiSessionToken.parse("not-a-token"))
        assertNull(ApiSessionToken.parse("bls2.k1.e30.sig"))
        assertNull(ApiSessionToken.parse("bls1.k1.!!!.sig"))
        assertNull(ApiSessionToken.parse("bls1.k1.e30.sig"), "a payload without sid or exp is unusable")
        assertEquals(ApiAttestation.None, parse(sessionToken(att = "web")).attestation)
    }

    @Test
    fun `proof is added only at or past expiry, with a short lead for requests in flight`() {
        val token = parse(sessionToken(iat = BASE_S, exp = BASE_S + 3_600))

        assertFalse(ApiSessionPolicy.needsProof(token, BASE_S))
        assertFalse(ApiSessionPolicy.needsProof(token, BASE_S + 3_600 - ApiSessionPolicy.PROOF_LEAD_SECONDS - 1))
        assertTrue(ApiSessionPolicy.needsProof(token, BASE_S + 3_600 - ApiSessionPolicy.PROOF_LEAD_SECONDS))
        assertTrue(ApiSessionPolicy.needsProof(token, BASE_S + 3_600 + 5 * DAY_S))
        assertFalse(ApiSessionPolicy.needsProof(parse(sessionToken(att = "none", exp = BASE_S + 10)), BASE_S + 20))
    }

    @Test
    fun `a token is usable until just before its attestation window ends`() {
        val token = parse(sessionToken(iat = BASE_S, exp = BASE_S + DAY_S, attExp = BASE_S + 30 * DAY_S))

        assertTrue(ApiSessionPolicy.isUsable(token, BASE_S + 2 * DAY_S), "past exp but renewable")
        assertTrue(ApiSessionPolicy.isUsable(token, BASE_S + 30 * DAY_S - ApiSessionPolicy.USABLE_MARGIN_SECONDS - 1))
        assertFalse(ApiSessionPolicy.isUsable(token, BASE_S + 30 * DAY_S - ApiSessionPolicy.USABLE_MARGIN_SECONDS))
    }

    @Test
    fun `refreshes App Check sessions a day early and others an hour early`() {
        val full = parse(sessionToken(att = "appcheck", iat = BASE_S, attExp = BASE_S + 30 * DAY_S))
        val limited = parse(sessionToken(att = "none", iat = BASE_S, exp = BASE_S + DAY_S))

        assertEquals(BASE_S + 29 * DAY_S, ApiSessionPolicy.refreshAt(full))
        assertEquals(BASE_S + DAY_S - 3_600, ApiSessionPolicy.refreshAt(limited))
    }

    @Test
    fun `better attestation replaces, weaker waits until the current session is due`() {
        val full = parse(sessionToken(att = "appcheck", iat = BASE_S, attExp = BASE_S + 30 * DAY_S, tag = "full"))
        val limited = parse(sessionToken(att = "none", iat = BASE_S + 60, tag = "limited"))
        val olderLimited = parse(sessionToken(att = "none", iat = BASE_S, tag = "older"))

        assertTrue(ApiSessionPolicy.shouldReplace(null, limited, BASE_S))
        assertTrue(ApiSessionPolicy.shouldReplace(limited, full, BASE_S + 60), "App Check beats limited")
        assertFalse(ApiSessionPolicy.shouldReplace(full, limited, BASE_S + 60), "a limited session never displaces a fresh full one")
        assertTrue(ApiSessionPolicy.shouldReplace(olderLimited, limited, BASE_S + 60), "same kind: the later window wins")
        assertFalse(ApiSessionPolicy.shouldReplace(limited, olderLimited, BASE_S + 60))
        val nearEnd = parse(sessionToken(att = "challenge", iat = BASE_S, exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = "challenge"))
        val freshLimited = parse(sessionToken(att = "none", iat = BASE_S + 7 * DAY_S - 1_800, tag = "fresh"))
        assertTrue(ApiSessionPolicy.shouldReplace(nearEnd, freshLimited, BASE_S + 7 * DAY_S - 1_800), "a weaker session takes over once the current one is due")
    }

    @Test
    fun `renewals only extend the session already held`() {
        val held = parse(sessionToken(sid = "s", exp = BASE_S + 10))

        assertTrue(ApiSessionPolicy.acceptsRenewal(held, parse(sessionToken(sid = "s", exp = BASE_S + DAY_S, tag = "b"))))
        assertFalse(ApiSessionPolicy.acceptsRenewal(held, parse(sessionToken(sid = "other", exp = BASE_S + DAY_S))))
        assertFalse(ApiSessionPolicy.acceptsRenewal(held, parse(sessionToken(sid = "s", exp = BASE_S + 5))), "a replayed older token")
        assertFalse(ApiSessionPolicy.acceptsRenewal(null, held))
    }

    @Test
    fun `App Check retries back off from a minute to hourly to daily`() {
        assertEquals(60_000L, ApiSessionPolicy.upgradeDelayMs(1))
        assertEquals(3_600_000L, ApiSessionPolicy.upgradeDelayMs(2))
        assertEquals(3_600_000L, ApiSessionPolicy.upgradeDelayMs(4))
        assertEquals(86_400_000L, ApiSessionPolicy.upgradeDelayMs(5))
        assertEquals(86_400_000L, ApiSessionPolicy.upgradeDelayMs(50))
    }

    @Test
    fun `exchange retries back off and honour Retry-After`() {
        assertEquals(2_000L, ApiSessionPolicy.exchangeRetryDelayMs(1))
        assertEquals(60_000L, ApiSessionPolicy.exchangeRetryDelayMs(5))
        assertEquals(300_000L, ApiSessionPolicy.exchangeRetryDelayMs(9))
        assertEquals(120_000L, ApiSessionPolicy.exchangeRetryDelayMs(1, retryAfterSeconds = 120))
        assertEquals(2_000L, ApiSessionPolicy.exchangeRetryDelayMs(0))
    }

    @Test
    fun `reads shield error codes without consuming bodies`() {
        assertEquals("session_expired", ApiErrorCodes.parse("""{"error":"session_expired"}"""))
        assertNull(ApiErrorCodes.parse("""{"error":7}"""))
        assertNull(ApiErrorCodes.parse("<html>"))
        assertNull(ApiErrorCodes.parse(null))
        assertTrue(ApiErrorCodes.VERIFICATION_REQUIRED in ApiErrorCodes.AI_UNAVAILABLE)
        assertTrue(ApiErrorCodes.DAILY_AI_LIMIT in ApiErrorCodes.AI_UNAVAILABLE)
        assertFalse(ApiErrorCodes.SESSION_EXPIRED in ApiErrorCodes.AI_UNAVAILABLE)
    }
}
