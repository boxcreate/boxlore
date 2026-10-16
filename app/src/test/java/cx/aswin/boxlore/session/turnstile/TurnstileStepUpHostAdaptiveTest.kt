package cx.aswin.boxlore.session.turnstile

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.session.TurnstileResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Turnstile step-up host keeps one page through every presentation: invisible, then the phone
 * sheet or the centered dialog as the window crosses the tablet gates (600dp wide, 480dp tall), the
 * 840dp expanded width, portrait/landscape and a half-opened fold, in both directions. Window
 * changes never count as a skip; Skip and an outside tap end the run once; the page is destroyed
 * exactly once when the run ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TurnstileStepUpHostAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val controller = TurnstileStepUpController(invisibleTimeoutMs = 60_000, interactiveTimeoutMs = 600_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val created = mutableListOf<View>()
    private val destroyed = mutableListOf<View>()
    private val urls = mutableListOf<String>()

    private val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)

    /** Phone → either side of each gate → expanded → short → portrait tablet → folded → flat → phone. */
    private val transitions = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(839f, 600f),
        BoxloreWindowLayout(840f, 600f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(1280f, 480f),
        BoxloreWindowLayout(800f, 1280f),
        BoxloreWindowLayout(673f, 841f, listOf(fold)),
        BoxloreWindowLayout(673f, 841f),
        BoxloreWindowLayout(360f, 740f),
    )

    @After
    fun tearDown() = scope.cancel()

    private fun render() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                val layout = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        TurnstileStepUpHost(
                            controller = controller,
                            apiBaseUrl = "https://api.boxlore.example/",
                            pageFactory = { context, url, _, _ ->
                                urls += url
                                View(context).also { created += it }
                            },
                            destroyPage = { destroyed += it },
                        )
                    }
                }
            }
        }
    }

    private fun start(): Deferred<TurnstileResult> {
        val result = scope.async { controller.solve() }
        compose.waitUntil(5_000) { created.isNotEmpty() }
        compose.waitForIdle()
        return result
    }

    private fun askForInteraction() {
        compose.runOnIdle { controller.onPageMessage(1, """{"type":"interactive"}""") }
        compose.waitForIdle()
    }

    @Test
    fun `an invisible pass never shows the sheet and destroys its page once`() {
        render()
        val result = start()

        compose.onNodeWithTag(INVISIBLE_PAGE_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(INVISIBLE_PAGE_TAG).assertDoesNotExist() // hidden from accessibility services
        compose.onNodeWithText("Quick check").assertDoesNotExist()
        assertEquals(listOf("https://api.boxlore.example/auth/turnstile?theme=light"), urls)
        compose.runOnIdle { controller.onPageMessage(1, """{"type":"token","value":"tok"}""") }

        assertEquals(TurnstileResult.Token("tok"), runBlocking { result.await() })
        compose.waitForIdle()
        compose.onNodeWithTag(INVISIBLE_PAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(created, destroyed)
    }

    @Test
    fun `the interactive check keeps one page through every window change`() {
        render()
        val result = start()
        val page = created.single()
        askForInteraction()

        compose.onNodeWithText("Quick check").assertIsDisplayed()
        compose.onNodeWithTag(CHALLENGE_PAGE_TAG).assertExists()
        transitions.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Quick check").assertIsDisplayed()
            compose.onNodeWithText("Confirm you’re a person to keep getting suggestions picked for you.").assertIsDisplayed()
            compose.onNodeWithText("Skip").assertIsDisplayed()
            assertSame("the same page moves between presentations", page, created.single())
            assertNotNull("$next: page attached", page.parent)
            assertFalse("$next: a window change is not a skip", result.isCompleted)
            assertTrue(destroyed.isEmpty())
        }

        compose.runOnIdle { controller.onPageMessage(1, """{"type":"token","value":"after-tap"}""") }
        assertEquals(TurnstileResult.Token("after-tap"), runBlocking { result.await() })
        compose.waitForIdle()
        compose.onNodeWithText("Quick check").assertDoesNotExist()
        assertEquals(listOf(page), destroyed)
    }

    @Test
    fun `Skip ends the run once on a phone`() {
        render()
        val result = start()
        askForInteraction()

        compose.onNodeWithText("Skip").performClick()

        assertEquals(TurnstileResult.Failed("cancelled"), runBlocking { result.await() })
        compose.waitForIdle()
        compose.onNodeWithText("Quick check").assertDoesNotExist()
        assertEquals(1, destroyed.size)
    }

    @Test
    fun `a tap outside the expanded dialog skips once, and a tap on it does not`() {
        window.value = BoxloreWindowLayout(1280f, 800f)
        render()
        val result = start()
        askForInteraction()

        compose.onNodeWithText("Quick check").performClick()
        compose.waitForIdle()
        assertFalse(result.isCompleted)
        compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }

        assertEquals(TurnstileResult.Failed("cancelled"), runBlocking { result.await() })
        compose.waitForIdle()
        compose.onNodeWithText("Quick check").assertDoesNotExist()
        assertEquals(1, destroyed.size)
    }
}
