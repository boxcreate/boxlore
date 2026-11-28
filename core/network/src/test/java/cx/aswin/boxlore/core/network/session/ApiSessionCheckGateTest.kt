package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The sign-in check and the step-up never queue two checks: a caller that waited learns what the turn before it did. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApiSessionCheckGateTest {
    private val clock = FakeClock()
    private val limited = ApiSessionToken.parse(sessionToken(sid = "l1", att = "none", tag = "limited"))!!
    private val challenge = ApiSessionToken.parse(sessionToken(sid = "c1", att = "challenge", tag = "challenge"))!!

    @Volatile private var held: ApiSessionToken? = limited
    private val gate = ApiSessionCheckGate(clock, ApiSessionTiming()) { held }

    @Test
    fun `a check queued behind one the listener skipped is declined, and holds sign-in checks for a while`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val first = async { gate.withTurn { turn -> turn.also { release.await() } } }
            runCurrent()
            val second = async { gate.withTurn { it } }
            runCurrent()
            assertEquals(1, gate.waiting)

            gate.missed(skipped = true)
            release.complete(Unit)

            assertEquals(ApiSessionCheckGate.Turn.Go, first.await())
            assertEquals(ApiSessionCheckGate.Turn.Declined, second.await())
            assertTrue(gate.skipHeld())
            // A later caller that didn't have to wait asks as usual.
            assertEquals(ApiSessionCheckGate.Turn.Go, gate.withTurn { it })
            clock.advanceSeconds(ApiSessionTiming().checkSkipHoldMs / 1_000L)
            assertFalse(gate.skipHeld())
        }

    @Test
    fun `a check queued behind one that got a better session uses that session`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val first = async { gate.withTurn { release.await() } }
            runCurrent()
            val second = async { gate.withTurn { it } }
            runCurrent()

            held = challenge
            release.complete(Unit)
            first.await()

            assertEquals(ApiSessionCheckGate.Turn.Covered(challenge), second.await())
            assertFalse(gate.skipHeld(), "only a skip holds sign-in checks")
        }
}
