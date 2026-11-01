package cx.aswin.boxlore.feature.home.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.home.NpsSurveyController
import cx.aswin.boxlore.feature.home.NpsSurveyCopy
import cx.aswin.boxlore.feature.home.NpsSurveyDefinition
import cx.aswin.boxlore.feature.home.NpsSurveyHost
import cx.aswin.boxlore.feature.home.NpsSurveyStep
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The NPS survey keeps one survey through every presentation: the phone sheet or the centered dialog as
 * the window crosses the tablet gates (600dp wide, 480dp tall), the 840dp expanded width,
 * portrait/landscape and a half-opened fold, in both directions. A window change never answers, dismisses
 * or re-sends anything, and the typed answer and step stay. Before a score an outside tap dismisses once;
 * on the follow-up it doesn't, so a typed answer can't be lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NpsSurveySheetAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val playing = mutableStateOf(false)
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit
    private var scheduledDisplays = 0
    private val controller = NpsSurveyController(onScheduledDisplay = { scheduledDisplays++ }, onScored = {})
    private val copy = NpsSurveyCopy()

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

    @Before
    fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @After
    fun tearDown() = restore()

    private fun render() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                val layout = window.value
                // Echo's idle never ends; its resting still keeps Compose idle for the checks.
                CompositionLocalProvider(LocalBoxloreWindowLayout provides layout, LocalEchoMotion provides false) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        NpsSurveyHost(controller, isPlaying = playing.value)
                    }
                }
            }
        }
    }

    private fun open() {
        compose.runOnIdle { controller.open(manual = false, copy = copy) }
        compose.waitForIdle()
    }

    /** The survey's own events (the sheet also sends its page view). */
    private fun names() = events.map { it.first }.filter { it.startsWith("survey ") }

    @Test
    fun `the typed answer and the step survive every window change`() {
        render()
        open()
        compose.onNodeWithText(copy.question).assertIsDisplayed()
        assertEquals(listOf("survey shown"), names())
        compose.onNodeWithTag("nps_score_4").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(NPS_ANSWER_TAG).performTextInput("Downloads that follow me")
        val submission = controller.session.value?.submissionId

        transitions.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText(copy.fixTitle).assertExists()
            compose.onNodeWithTag(NPS_ANSWER_TAG).assertTextContains("Downloads that follow me")
            compose.onNodeWithText(copy.send).performScrollTo().assertIsDisplayed()
            assertEquals("$next: same survey", submission, controller.session.value?.submissionId)
            assertEquals("$next: still on the follow-up", NpsSurveyStep.FollowUp, controller.session.value?.step)
            assertEquals("$next: a window change sends nothing", listOf("survey shown", "survey sent"), names())
        }
        assertEquals(1, scheduledDisplays)

        compose.onNodeWithText(copy.send).performClick()
        compose.waitForIdle()
        assertEquals("Downloads that follow me", events.last { it.first == "survey sent" }.second["\$survey_response_${NpsSurveyDefinition.FIX_QUESTION_ID}"])
        compose.onNodeWithText(copy.thanksLowTitle).assertIsDisplayed()
        compose.onNodeWithText(copy.done).performClick()
        compose.waitForIdle()
        assertNull(controller.session.value)
        compose.onNodeWithTag(NPS_SHEET_TAG).assertDoesNotExist()
        assertFalse(names().contains("survey dismissed"))
    }

    @Test
    fun `a tap outside the expanded dialog dismisses once before a score, and never on the follow-up`() {
        window.value = BoxloreWindowLayout(1280f, 800f)
        render()
        open()
        compose.onNodeWithText(copy.question).performClick()
        compose.waitForIdle()
        assertTrue(controller.isOpen)
        compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        compose.waitForIdle()
        assertNull(controller.session.value)
        assertEquals(listOf("survey shown", "survey dismissed"), names())

        events.clear()
        open()
        compose.onNodeWithTag("nps_score_9").performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).performTouchInput { click(Offset(16f, 16f)) }
        compose.waitForIdle()
        assertEquals(NpsSurveyStep.FollowUp, controller.session.value?.step)
        compose.onNodeWithText(copy.ideaTitle).assertIsDisplayed()
        assertFalse(names().contains("survey dismissed"))
    }

    @Test
    fun `a decided survey waits while something plays, and counts as shown only when it appears`() {
        playing.value = true
        render()
        open()
        compose.onNodeWithTag(NPS_SHEET_TAG).assertDoesNotExist()
        assertTrue(names().isEmpty())
        assertEquals(0, scheduledDisplays)

        compose.runOnIdle { playing.value = false }
        compose.waitForIdle()
        compose.onNodeWithText(copy.question).assertIsDisplayed()
        assertEquals(listOf("survey shown"), names())
        assertEquals(1, scheduledDisplays)
    }

    @Test
    @Config(qualifiers = "w740dp-h360dp-mdpi")
    fun `on a short landscape phone every score and Not now can be reached`() {
        window.value = BoxloreWindowLayout(740f, 360f)
        render()
        open()
        listOf("nps_score_0", "nps_score_5", "nps_score_10").forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertTrue("$tag inside the 360dp window", compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom <= 360.dp)
        }
        compose.onNodeWithText(copy.notNow).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("survey shown", "survey dismissed"), names())
    }
}
