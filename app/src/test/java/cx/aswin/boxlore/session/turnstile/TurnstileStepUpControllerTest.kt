package cx.aswin.boxlore.session.turnstile

import cx.aswin.boxlore.core.network.session.TurnstileResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TurnstileStepUpControllerTest {
    private val controller = TurnstileStepUpController(invisibleTimeoutMs = 20_000, interactiveTimeoutMs = 120_000)

    @Test
    fun `an invisible pass returns the token and clears the host`() = runTest {
        val result = async { controller.solve() }
        runCurrent()
        assertEquals(TurnstileStepUpState.Running(1, interactive = false), controller.state.value)

        controller.onPageMessage(1, """{"type":"ready"}""")
        controller.onPageMessage(1, """{"type":"token","value":"tok"}""")

        assertEquals(TurnstileResult.Token("tok"), result.await())
        assertEquals(TurnstileStepUpState.Idle, controller.state.value)
    }

    @Test
    fun `nothing within the invisible window fails, which also covers a backgrounded app`() = runTest {
        val result = async { controller.solve() }
        advanceTimeBy(19_999)
        assertFalse(result.isCompleted)
        advanceTimeBy(2)

        assertEquals(TurnstileResult.Failed("timeout"), result.await())
    }

    @Test
    fun `asking for interaction shows the sheet and allows longer`() = runTest {
        val result = async { controller.solve() }
        advanceTimeBy(5_000)
        controller.onPageMessage(1, """{"type":"interactive"}""")
        runCurrent()
        assertEquals(TurnstileStepUpState.Running(1, interactive = true), controller.state.value)

        advanceTimeBy(60_000)
        assertFalse(result.isCompleted, "past the invisible window, the listener still has time")
        controller.onPageMessage(1, """{"type":"token","value":"after-tap"}""")

        assertEquals(TurnstileResult.Token("after-tap"), result.await())
    }

    @Test
    fun `an unanswered interaction times out`() = runTest {
        val result = async { controller.solve() }
        runCurrent()
        controller.onPageMessage(1, """{"type":"interactive"}""")
        advanceTimeBy(120_001)

        assertEquals(TurnstileResult.Failed("interaction_timeout"), result.await())
    }

    @Test
    fun `skip, errors and page failures end the run with a reason`() = runTest {
        suspend fun endWith(finish: (Int) -> Unit): TurnstileResult {
            val result = async { controller.solve() }
            runCurrent()
            finish((controller.state.value as TurnstileStepUpState.Running).attempt)
            return result.await()
        }

        assertEquals(TurnstileResult.Failed("cancelled"), endWith { controller.cancel(it) })
        assertEquals(TurnstileResult.Failed("turnstile_110200"), endWith { controller.onPageMessage(it, """{"type":"error","value":"110200"}""") })
        assertEquals(TurnstileResult.Failed("expired"), endWith { controller.onPageMessage(it, """{"type":"expired"}""") })
        assertEquals(TurnstileResult.Failed("challenge_timeout"), endWith { controller.onPageMessage(it, """{"type":"timeout"}""") })
        assertEquals(TurnstileResult.Failed("unsupported"), endWith { controller.onPageMessage(it, """{"type":"unsupported"}""") })
        assertEquals(TurnstileResult.Failed("page_http_404"), endWith { controller.onPageFailed(it, "page_http_404") })
    }

    @Test
    fun `a page from an earlier run cannot answer a later one`() = runTest {
        val first = async { controller.solve() }
        runCurrent()
        controller.cancel(1)
        first.await()

        val second = async { controller.solve() }
        runCurrent()
        controller.onPageMessage(1, """{"type":"token","value":"stale"}""")
        controller.onPageMessage(1, """{"type":"interactive"}""")
        runCurrent()
        assertFalse(second.isCompleted)
        assertEquals(TurnstileStepUpState.Running(2, interactive = false), controller.state.value)
        controller.onPageMessage(2, """{"type":"token","value":"fresh"}""")
        assertEquals(TurnstileResult.Token("fresh"), second.await())
    }

    @Test
    fun `the bridge reads only well-formed messages`() {
        assertEquals(TurnstileBridgeEvent.Token("t"), TurnstileBridge.parse("""{"type":"token","value":"t"}"""))
        assertEquals(TurnstileBridgeEvent.Error("empty_token"), TurnstileBridge.parse("""{"type":"token","value":""}"""))
        assertEquals(TurnstileBridgeEvent.Error("unknown"), TurnstileBridge.parse("""{"type":"error"}"""))
        assertEquals(TurnstileBridgeEvent.Error("300030script"), TurnstileBridge.parse("""{"type":"error","value":"300030<script>"}"""), "codes keep only safe characters")
        assertEquals(TurnstileBridgeEvent.Interactive, TurnstileBridge.parse("""{"type":"interactive"}"""))
        assertEquals(TurnstileBridgeEvent.Unknown, TurnstileBridge.parse("not json"))
        assertEquals(TurnstileBridgeEvent.Unknown, TurnstileBridge.parse("""{"type":"other"}"""))
        assertEquals(TurnstileBridgeEvent.Unknown, TurnstileBridge.parse(null))
    }

    @Test
    fun `the page lives on the API host and may only navigate there or to Turnstile`() {
        assertEquals("https://api.boxlore.example/auth/turnstile?theme=dark", TurnstileBridge.pageUrl("https://api.boxlore.example/", dark = true))
        assertEquals("https://api.boxlore.example/auth/turnstile?theme=light", TurnstileBridge.pageUrl("https://api.boxlore.example", dark = false))
        assertEquals("https://api.boxlore.example", TurnstileBridge.origin("https://api.boxlore.example/auth/turnstile?theme=dark"))
        assertEquals("http://127.0.0.1:8080", TurnstileBridge.origin("http://127.0.0.1:8080/auth/turnstile"))
        assertTrue(TurnstileBridge.allowsNavigation("https://api.boxlore.example/auth/turnstile", "api.boxlore.example"))
        assertTrue(TurnstileBridge.allowsNavigation("https://challenges.cloudflare.com/cdn-cgi/x", "api.boxlore.example"))
        assertFalse(TurnstileBridge.allowsNavigation("https://www.cloudflare.com/privacypolicy/", "api.boxlore.example"))
        assertFalse(TurnstileBridge.allowsNavigation("http://api.boxlore.example/", "api.boxlore.example"))
        assertFalse(TurnstileBridge.allowsNavigation("intent://x", "api.boxlore.example"))
        assertTrue(TurnstileBridge.allowsNavigation("about:blank", "api.boxlore.example"))
        assertTrue(TurnstileBridge.allowsNavigation("about:srcdoc", "api.boxlore.example"))
        assertFalse(TurnstileBridge.allowsNavigation("about:config", "api.boxlore.example"))
    }
}
