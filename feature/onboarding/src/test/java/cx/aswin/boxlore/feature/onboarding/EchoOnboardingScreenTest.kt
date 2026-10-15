package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoOnboardingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING))
    private val actions = mutableListOf<String>()
    private val choices = listOf("Optimistic wonder", "Calm contemplation", "Humorous lightness", "Serious analysis")

    private val callbacks = EchoCallbacks(
        onBack = { actions += "back" },
        onCancelRequest = { actions += "cancel" },
        onOptionToggle = { option ->
            val selected = state.value.aiSelectedOptions
            state.value = state.value.copy(aiSelectedOptions = if (option in selected) selected - option else selected + option)
        },
        onCustomInputChange = { state.value = state.value.copy(aiCustomInputText = it) },
        onContinue = { actions += "continue" },
        onRevealSuggestions = { actions += "reveal" },
        onRetry = { actions += "retry" },
        onPickTopics = { actions += "topics" },
        onFindShows = { actions += "find" },
        onChangeAnswers = { actions += "change" },
        onSearch = { actions += "search:${it.orEmpty()}" },
        onDeckReaction = { reaction ->
            actions += "deck:${reaction.wire}"
            state.value = state.value.copy(aiDeck = EchoDeckLogic.react(state.value.aiDeck, reaction))
        },
        onDeckBack = {
            actions += "deck:back"
            state.value = state.value.copy(aiDeck = EchoDeckLogic.back(state.value.aiDeck))
        },
    )

    /** Records what the deck asks of the clip player; no audio in JVM tests. */
    private class FakePreview : EchoShowPreview {
        val loads = mutableListOf<Long?>()
        override val status = mutableStateOf(EchoPreviewStatus())

        override fun load(clip: EchoClip?) {
            loads += clip?.showId
            status.value = EchoPreviewStatus(showId = clip?.showId)
        }

        override fun toggle() {
            status.value = status.value.copy(playing = !status.value.playing)
        }

        override fun pause() {
            status.value = status.value.copy(playing = false)
        }

        override fun tick() = Unit

        override fun release() = Unit
    }

    private val preview = FakePreview()

    /** Echo, the shimmer and the progress wave animate forever, so the clock is driven by hand. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun render(fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalEchoShowPreviewFactory provides { _ -> preview },
                ) {
                    EchoOnboardingScreen(state.value, callbacks)
                }
            }
        }
        settle()
    }

    private fun laterTurn(finish: String? = null) = OnboardingUiState(
        currentStep = OnboardingStep.AI_ONBOARDING,
        aiCurrentTurn = 2,
        aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("True crime")))),
        aiOptions = choices,
        aiGuidedTurn = GuidedOnboardingTurn(1, "question", "Cold cases. Respect.", "What tone do you enjoy?", choices, intent = "preferences", finish = finish),
    )

    private fun deckTurn() = laterTurn().copy(
        aiSelectedOptions = setOf(choices.first()),
        aiDeck = EchoDeck(
            EchoDeckStatus.Ready,
            listOf(
                OnboardingProbeCard(745392, "Serial", "Serial Productions", hook = "A journalist re-investigates a decades-old murder.", question = "Could you binge a case like this?", previewUrl = "https://cdn/serial.mp3", previewStartSeconds = 960),
                OnboardingProbeCard(7, "Criminal", "Radiotopia", hook = "Short stories of people who did wrong.", question = "Shorter cases, maybe?"),
            ),
            showing = true,
        ),
    )

    @Test
    fun `a show card asks in Echo's bubble, hides the headline and answers with four big buttons`() {
        state.value = deckTurn()
        render()
        compose.onNodeWithTag("echo_deck_question").assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Could you binge a case like this?"))))
        assertTrue(compose.onAllNodesWithTag("echo_question").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("echo_primary").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("echo_deck_card").assertIsDisplayed()
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
        listOf("Sounds good", "Not for me", "I already listen", "Not sure").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertEquals(listOf<Long?>(745392L), preview.loads)

        compose.onNodeWithTag("echo_deck_clip").performClick()
        assertTrue(preview.status.value.playing)
        compose.onNodeWithTag("echo_deck_like").performClick()
        settle()
        compose.onNodeWithText("Shorter cases, maybe?").assertIsDisplayed()
        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        // A positive answer picks the show and says so, briefly.
        compose.onNodeWithText("Serial added to your picks").assertIsDisplayed()
        // Answering stops the clip; the next card waits for its own tap.
        assertEquals(listOf<Long?>(745392L, null), preview.loads)
        assertFalse(preview.status.value.playing)
        assertTrue(compose.onAllNodesWithTag("echo_deck_clip").fetchSemanticsNodes().isEmpty())

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithText("Could you binge a case like this?").assertIsDisplayed()
        assertEquals(listOf("deck:like", "deck:back"), actions)
    }

    @Test
    fun `a new card ignores a double tap meant for the one before`() {
        state.value = deckTurn()
        render()
        compose.onNodeWithTag("echo_deck_dislike").performClick()
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("echo_deck_dislike").performClick()
        settle()
        assertEquals(listOf("deck:dislike"), actions)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun `with Echo folded away on a tight screen the card's question takes the headline slot`() {
        state.value = deckTurn()
        render(fontScale = 2f)
        compose.onNodeWithTag("echo_deck_question").assertIsDisplayed()
        compose.onNodeWithTag("echo_deck_card").assertIsDisplayed()
        val root = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot
        val buttons = compose.onNodeWithTag("echo_deck_reactions").fetchSemanticsNode().boundsInRoot
        assertTrue(buttons.bottom <= root.bottom)
        assertTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `opening turn shows Echo, the question and eight topics, and Continue needs a pick`() {
        render()
        compose.onNodeWithTag("echo_acknowledgment").assertIsDisplayed()
        compose.onNodeWithTag("echo_topics").assertExists()
        compose.onNodeWithText("Where does your mind like to wander?").assertIsDisplayed()
        val checkboxes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNodes().size
        assertEquals(8, checkboxes)
        compose.onNodeWithTag("echo_primary").assertIsNotEnabled()
        compose.onNodeWithText("True crime").performClick()
        settle()
        compose.onNodeWithText("True crime").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onNodeWithTag("echo_primary").assertIsEnabled().performClick()
        assertEquals(listOf("continue"), actions)
    }

    @Test
    fun `later turns use four checkbox rows and even "enough" keeps Continue as the main button`() {
        state.value = laterTurn(finish = "prominent")
        render()
        compose.onNodeWithText("What tone do you enjoy?").assertIsDisplayed()
        compose.onNodeWithText("Cold cases. Respect.").assertIsDisplayed()
        choices.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        // Finishing stays the listener's quiet choice; more answers mean better suggestions.
        compose.onNodeWithText("Up to 6 more questions").assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsNotEnabled()
        compose.onNodeWithText(choices.first()).performClick()
        settle()
        compose.onNodeWithTag("echo_primary").assertIsEnabled().performClick()
        assertEquals(listOf("continue"), actions)
    }

    @Test
    fun `while optional, I'm done is a quiet way out that asks before leaving`() {
        state.value = laterTurn(finish = "available")
        render()
        compose.onNodeWithText("Up to 6 more questions").assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsNotEnabled()
        compose.onNodeWithText("I’m done").performClick()
        settle()
        compose.onNodeWithText("Find your shows now?").assertIsDisplayed()
        compose.onNodeWithText("Keep answering").performClick()
        settle()
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("I’m done").performClick()
        settle()
        compose.onNodeWithText("Find my shows").performClick()
        assertEquals(listOf("find"), actions)
    }

    @Test
    fun `answers sit low and nothing on the screen scrolls`() {
        state.value = laterTurn()
        render()
        val question = compose.onNodeWithTag("echo_question").fetchSemanticsNode().boundsInRoot
        val answers = compose.onNodeWithTag("echo_choices").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithTag("echo_primary").fetchSemanticsNode().boundsInRoot
        assertTrue(answers.top > question.bottom)
        assertTrue(footer.top - answers.bottom < 120f)
        assertTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `waiting shows a placeholder where the question goes and Back undoes the send`() {
        state.value = laterTurn().copy(isAiLoading = true, aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE)
        render()
        compose.onNodeWithTag("echo_waiting").assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithText("What tone do you enjoy?").fetchSemanticsNodes().isEmpty())
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf("cancel"), actions)
    }

    @Test
    fun `while Echo thinks the footer stays put, paused, and the main button says so`() {
        state.value = laterTurn(finish = "available")
        render()
        compose.onNodeWithText("I’m done").assertIsEnabled()
        state.value = state.value.copy(isAiLoading = true, aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE)
        settle()
        compose.onNodeWithTag("echo_thinking", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Thinking…", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("echo_secondary").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Up to 6 more questions").assertIsDisplayed()
    }

    @Test
    fun `errors explain themselves and offer a retry and a way out`() {
        state.value = laterTurn().copy(onboardingError = "offline")
        render()
        compose.onNodeWithTag("echo_error").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Pick topics instead").performClick()
        assertEquals(listOf("retry", "topics"), actions)
    }

    @Test
    fun `Something else swaps the choices for a text box and Back swaps them back`() {
        state.value = laterTurn()
        render()
        compose.onNodeWithTag("echo_something_else").performClick()
        settle()
        compose.onNodeWithTag("echo_answer_input").assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsNotEnabled()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithText("Optimistic wonder").assertIsDisplayed()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `a named show offers a search for it`() {
        state.value = laterTurn().copy(aiSearchSuggestion = "Serial")
        render()
        compose.onNodeWithTag("echo_search_suggestion").performClick()
        assertEquals(listOf("search:Serial"), actions)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun `small phones at double text size fit everything without scrolling`() {
        state.value = laterTurn(finish = "available")
        render(fontScale = 2f)
        compose.onNodeWithTag("echo_question").assertIsDisplayed()
        compose.onNodeWithTag("echo_choices").assertIsDisplayed()
        compose.onNodeWithTag("echo_primary").assertIsDisplayed()
        compose.onNodeWithTag("echo_secondary").assertIsDisplayed()
        val root = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("echo_primary").fetchSemanticsNode().boundsInRoot
        assertTrue(button.bottom <= root.bottom)
    }
}
