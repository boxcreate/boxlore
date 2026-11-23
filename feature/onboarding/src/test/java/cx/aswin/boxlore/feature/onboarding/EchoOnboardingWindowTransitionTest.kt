package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One live Echo screen while the window changes under it: a phone turned on its side and back, a
 * split-screen half, a foldable's tabletop half, and both sides of the 600dp, 480dp and 840dp gates.
 * The layout moves between today's, the shrunk one and Echo beside the answers, but the picked answer,
 * a typed draft and its focus, and an open confirmation all stay, the answers don't enter again, and
 * the buttons and Back call the current callbacks once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoOnboardingWindowTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private var state by mutableStateOf(OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING))
    private val calls = mutableListOf<String>()

    private val callbacks = EchoCallbacks(
        onBack = { calls += "back" },
        onCancelRequest = {},
        onOptionToggle = { option ->
            val picked = state.aiSelectedOptions
            state = state.copy(aiSelectedOptions = if (option in picked) picked - option else picked + option)
        },
        onCustomInputChange = { state = state.copy(aiCustomInputText = it) },
        onContinue = { calls += "continue" },
        onRevealSuggestions = {},
        onRetry = {},
        onPickTopics = {},
        onFindShows = { calls += "find" },
        onChangeAnswers = {},
        onSearch = {},
    )

    private object SilentPreview : EchoShowPreview {
        override val status: State<EchoPreviewStatus> = mutableStateOf(EchoPreviewStatus())
        override fun load(clip: EchoClip?) = Unit
        override fun toggle() = Unit
        override fun pause() = Unit
        override fun tick() = Unit
        override fun release() = Unit
    }

    /** Phone ⇄ sideways, phone ⇄ split half, book ⇄ tabletop half, and both sides of each gate, there and back. */
    private val walk = with(BoxloreTestWindows) {
        arrayOf(
            PHONE_LAND, PHONE, SPLIT_HALF, PHONE,
            W599_H900, W600_H900, W599_H900, W900_H479, W900_H480, W900_H479, W839, W841, W839,
            UPRIGHT_BOOK, TABLETOP_LOWER_9PRO, UPRIGHT_BOOK, PHONE,
        )
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalEchoShowPreviewFactory provides { _ -> SilentPreview }) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) { EchoOnboardingScreen(state, callbacks) }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun topic(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun `a picked topic stays picked and the answers never enter again`() {
        show()
        topic("Science").performClick()
        settle()

        compose.walkWindows(window, *walk) { step ->
            // The same answers stay in place: a second set would mean they entered again.
            assertEquals("answers on $step", 1, compose.onAllNodesWithTag("echo_topics").fetchSemanticsNodes().size)
            topic("Science").assertToggle(ToggleableState.On, step)
            topic("Tech").assertToggle(ToggleableState.Off, step)
            compose.onNodeWithTag("echo_question").assertExists()
            // When Something else moves beside the main button, the footer takes it on the next frame.
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("echo_something_else").assertExists()
        }
        settle()
        compose.onNodeWithTag("echo_primary").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(listOf("continue", "back"), calls)
    }

    @Test
    fun `answers that move to four columns never enter again`() {
        show()
        topic("Science").performClick()
        settle()

        compose.walkWindows(window, TABLETOP_LOWER_FOLD8, PHONE_LAND, TABLETOP_LOWER_FOLD8, BoxloreTestWindows.PHONE) { step ->
            // The first frame after the change: the first tile already sits at the top of the answers,
            // not lifted on its way in again.
            val answers = compose.onNodeWithTag("echo_topics").fetchSemanticsNode().boundsInRoot
            assertEquals("the answers entered again on $step", answers.top, topic("Science").fetchSemanticsNode().boundsInRoot.top, 0.5f)
            topic("Science").assertToggle(ToggleableState.On, step)
            settle()
        }
        assertEquals("four columns on the tabletop half", 2, rowsOn(TABLETOP_LOWER_FOLD8))
    }

    @Test
    fun `a draft keeps its text and focus`() {
        state = laterTurn.copy(aiCustomInputText = "Long drives and big ideas")
        show()
        compose.onNodeWithTag("echo_answer_input").performClick()
        settle()

        compose.walkWindows(window, *walk) {
            compose.onNodeWithTag("echo_answer_input").assertIsFocused().assertTextContains("Long drives and big ideas")
        }
        assertEquals("Long drives and big ideas", state.aiCustomInputText)
    }

    @Test
    fun `the finish confirmation stays open and confirms once`() {
        state = laterTurn.copy(aiSelectedOptions = setOf(choices.first()))
        show()
        compose.onNodeWithTag("echo_secondary").performClick()
        settle()

        compose.walkWindows(window, *walk) {
            compose.onNodeWithTag("echo_finish_confirmation").assertExists()
        }
        compose.onNode(hasText("Find my shows") and hasClickAction()).performClick()
        settle()

        assertEquals(listOf("find"), calls)
        compose.onNodeWithTag("echo_finish_confirmation").assertDoesNotExist()
    }

    @Test
    fun `the About Echo sheet stays open through every window change`() {
        show()
        compose.onNodeWithTag("echo_more").performClick()
        settle()
        compose.onNode(hasText("About Echo") and hasClickAction()).performClick()
        settle()

        compose.walkWindows(window, *walk) { step ->
            settle()
            assertEquals("the About sheet closed on $step", 1, compose.onAllNodes(hasText("About Echo")).fetchSemanticsNodes().size)
        }
        assertEquals(emptyList<String>(), calls)
    }

    /** Rows of topic tiles on [size]: distinct tile tops. */
    private fun rowsOn(size: BoxloreWindowLayout): Int {
        window.value = size
        settle()
        return compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("echo_topics")) and hasClickAction())
            .fetchSemanticsNodes().map { it.boundsInRoot.top.toInt() }.distinct().size
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertToggle(expected: ToggleableState, step: BoxloreWindowLayout) {
        val actual = fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
        assertEquals("picked state on $step", expected, actual)
    }

    private val choices = listOf("Unsolved mysteries", "Big ideas in science", "Stories from history", "Comedians riffing")
    private val laterTurn = OnboardingUiState(
        currentStep = OnboardingStep.AI_ONBOARDING,
        aiCurrentTurn = 3,
        aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("True crime")))),
        aiOptions = choices,
        aiGuidedTurn = GuidedOnboardingTurn(2, "question", "Good mix.", "Which of these would you happily hear about on a long drive?", choices, intent = "preferences", finish = "available"),
    )

    private companion object {
        /** The lower half a foldable like the Pixel 9 Pro Fold gives onboarding in tabletop. */
        val TABLETOP_LOWER_9PRO = BoxloreWindowLayout(673f, 410f)

        /** The Galaxy Z Fold 8's lower half in tabletop, where the topics stand four across. */
        val TABLETOP_LOWER_FOLD8 = BoxloreWindowLayout(932f, 314f)
        val PHONE_LAND = BoxloreTestWindows.PHONE_LAND
    }
}
