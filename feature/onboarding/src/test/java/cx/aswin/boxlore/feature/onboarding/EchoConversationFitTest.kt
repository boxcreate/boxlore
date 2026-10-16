package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Echo in the conversation across phone, foldable and tablet sizes and text sizes: wherever it shows,
 * its figure (which holds everything Echo does, see [EchoFigureGeometry]) sits below the top bar and
 * clear of its bubble, the question, the answers, the show card and its buttons, and nothing scrolls or
 * is cut off (a show card's question included). On common phones at default text, Echo always shows.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoConversationFitTest(private val widthDp: Int, private val heightDp: Int, private val fontScale: Float, private val mustShow: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val choices = listOf("Optimistic wonder", "Calm contemplation", "Humorous lightness", "Serious analysis")

    private val callbacks = EchoCallbacks(
        onBack = {}, onCancelRequest = {}, onOptionToggle = {}, onCustomInputChange = {}, onContinue = {}, onRevealSuggestions = {},
        onRetry = {}, onPickTopics = {}, onFindShows = {}, onChangeAnswers = {}, onSearch = {}, onDeckReaction = {}, onDeckBack = {},
    )

    private object SilentPreview : EchoShowPreview {
        override val status: State<EchoPreviewStatus> = mutableStateOf(EchoPreviewStatus())
        override fun load(clip: EchoClip?) = Unit
        override fun toggle() = Unit
        override fun pause() = Unit
        override fun tick() = Unit
        override fun release() = Unit
    }

    private val opening = OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING)

    private val later = OnboardingUiState(
        currentStep = OnboardingStep.AI_ONBOARDING,
        aiCurrentTurn = 2,
        aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("True crime")))),
        aiOptions = choices,
        aiGuidedTurn = GuidedOnboardingTurn(1, "question", "Cold cases. Respect.", "What tone do you enjoy when a story gets dark?", choices, intent = "preferences"),
    )

    private val deck = later.copy(
        aiSelectedOptions = setOf(choices.first()),
        aiDeck = EchoDeck(
            EchoDeckStatus.Ready,
            listOf(
                OnboardingProbeCard(745392, "Serial", "Serial Productions", hook = "A journalist re-investigates a decades-old murder.", question = "Could you binge a case like this?"),
                OnboardingProbeCard(7, "Criminal", "Radiotopia", hook = "Short stories of people who did wrong.", question = "Shorter cases, maybe?"),
            ),
            showing = true,
        ),
    )

    @Test fun `opening turn`() = check(opening, expectEcho = mustShow)

    @Test fun `later question`() = check(later, expectEcho = mustShow)

    @Test fun `show card`() = check(deck, expectEcho = mustShow)

    // A very long name and a long pitch: the name shrinks or wraps and the pitch is still shown whole.
    @Test fun `show card with a long name`() = check(
        deck.copy(
            aiDeck = deck.aiDeck.copy(
                cards = listOf(
                    OnboardingProbeCard(
                        9,
                        "The Dry Bar Comedy Podcast: Stand-Up Stories, Backstage Chats and Much More",
                        "Dry Bar Comedy",
                        role = "related",
                        hook = "Stand-up comedians swap stories from the road and the stage, with plenty of tangents along the way and a few surprises.",
                        question = "Clean stand-up, maybe?",
                    ),
                    deck.aiDeck.cards.last(),
                ),
            ),
        ),
        expectEcho = mustShow,
    )

    private fun check(state: OnboardingUiState, expectEcho: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalEchoShowPreviewFactory provides { _ -> SilentPreview },
                ) {
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) { EchoOnboardingScreen(state, callbacks) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val label = "${widthDp}x$heightDp dp at ${fontScale}x text"

        val echo = compose.onAllNodesWithTag("echo_onboarding_avatar").fetchSemanticsNodes()
        if (expectEcho) assertEquals("Echo should show on $label", 1, echo.size)
        echo.forEach { node ->
            val figure = node.boundsInRoot
            val topBar = listOf("echo_progress", "echo_more").maxOf { bounds(it)!!.bottom }
            assertTrue("Echo overlaps the top bar on $label (${figure.top} < $topBar)", figure.top >= topBar - 1f)
            listOf("echo_acknowledgment", "echo_question", "echo_choices", "echo_deck_question", "echo_deck_card", "echo_deck_reactions", "echo_primary").forEach { tag ->
                val other = bounds(tag) ?: return@forEach
                assertTrue("Echo overlaps $tag on $label ($figure vs $other)", !figure.overlaps(other.deflate(1f)))
            }
        }
        assertTrue("something scrolls on $label", compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
        val root = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot
        // A show card answers with its own buttons; every other turn has a main button.
        bounds("echo_primary")?.let { assertTrue("the main button is cut off on $label", it.bottom <= root.bottom + 1f) }
        bounds("echo_deck_reactions")?.let { reactions ->
            assertTrue("the reactions are cut off on $label", reactions.bottom <= root.bottom + 1f)
            assertTrue("the card runs into the reactions on $label", bounds("echo_deck_card")!!.bottom <= reactions.top + 1f)
            val card = bounds("echo_deck_card")!!
            assertTrue("the card runs off the side on $label", card.left >= root.left - 1f && card.right <= root.right + 1f)
            // The pitch is never cut short; the art gives way instead.
            compose.onAllNodesWithTag("echo_deck_hook").fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { assertTrue("the card's pitch is cut short on $label", !it.hasVisualOverflow) }
                assertTrue("the card's pitch runs past the card on $label", node.boundsInRoot.bottom <= card.bottom + 1f)
            }
        }
        // The card's question is never cut short, beside Echo or in the headline slot.
        compose.onAllNodesWithTag("echo_deck_question").fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { assertTrue("the card's question is cut short on $label", !it.hasVisualOverflow) }
        }
    }

    private fun bounds(tag: String): Rect? = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1} font {2}")
        fun sizes() = listOf(
            // Common phones at default text must show Echo.
            arrayOf<Any>(360, 740, 1.0f, true),
            arrayOf<Any>(393, 852, 1.0f, true),
            arrayOf<Any>(412, 915, 1.0f, true),
            arrayOf<Any>(427, 952, 1.0f, true),
            // Small phones and larger text: Echo may fold away, but never overlaps anything.
            arrayOf<Any>(320, 568, 1.0f, false),
            arrayOf<Any>(360, 640, 1.0f, false),
            arrayOf<Any>(360, 740, 1.3f, false),
            arrayOf<Any>(393, 852, 2.0f, false),
            // Foldables and tablets, both orientations, and a split-screen half.
            arrayOf<Any>(673, 841, 1.0f, false),
            arrayOf<Any>(841, 673, 1.0f, false),
            arrayOf<Any>(800, 1280, 1.0f, false),
            arrayOf<Any>(1280, 800, 1.0f, false),
            arrayOf<Any>(640, 800, 1.3f, false),
        )
    }
}
