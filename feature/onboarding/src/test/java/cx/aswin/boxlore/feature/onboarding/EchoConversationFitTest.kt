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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Echo in the conversation across phone, foldable and tablet sizes and text sizes, with the system
 * bars listeners have: wherever it shows, its figure (which holds everything Echo does, see
 * [EchoFigureGeometry]) sits below the top bar and clear of its bubble, the question, the answers, the
 * show card and its buttons. Nothing scrolls or is cut off: the question always shows whole, the
 * answers end above the main button and Something else stays on screen (a show card's question
 * included). On common phones at default text, Echo always shows.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EchoConversationFitTest(
    private val widthDp: Int,
    private val heightDp: Int,
    private val fontScale: Float,
    private val mustShow: Boolean,
    private val bars: String,
) {
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

    @Test fun `long question`() = check(
        later.copy(aiGuidedTurn = later.aiGuidedTurn?.copy(question = "Which of these would you happily hear about on a long drive?")),
        expectEcho = mustShow,
    )

    // Long topic labels: German and the en-XA pseudo-locale, where every string is about a third longer.
    @Test
    @Config(qualifiers = "+de")
    fun `opening turn in German`() = check(opening, expectEcho = false, longText = true)

    @Test
    @Config(qualifiers = "+en-rXA")
    fun `opening turn in long pseudo text`() = check(opening, expectEcho = false, longText = true)

    @Test fun `show card`() = check(deck, expectEcho = mustShow)

    // The card's answers in German and the en-XA pseudo-locale keep their labels whole, in one row or two.
    @Test
    @Config(qualifiers = "+de")
    fun `show card in German`() = check(deck, expectEcho = false, longText = true)

    @Test
    @Config(qualifiers = "+en-rXA")
    fun `show card in long pseudo text`() = check(deck, expectEcho = false, longText = true)

    // Echo thinking: the answers' skeleton keeps the loaded rows' shape and Echo keeps its size.
    @Test fun `waiting keeps the next turn's shape`() {
        assumeTrue("Checked on a phone with bars and a tabletop half", "${widthDp}x$heightDp$bars" in WAITING_SIZES && fontScale == 1f)
        val turn = mutableStateOf(later.copy(isAiLoading = true))
        show(turn)
        val rows = compose.onAllNodesWithTag("echo_skeleton_row").fetchSemanticsNodes()
        assertTrue("no skeleton on $widthDp x $heightDp", rows.isNotEmpty())
        rows.forEach { assertTrue("a skeleton row is squeezed (${it.boundsInRoot.height})", it.boundsInRoot.height >= 48f - 0.5f) }
        val waitingEcho = bounds("echo_onboarding_avatar")
        turn.value = later
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals("Echo changes size when the question arrives", waitingEcho?.width, bounds("echo_onboarding_avatar")?.width)
    }

    // Writing with the keyboard up: the question stays whole and the field shows, nothing scrolls.
    @Test fun `writing with the keyboard open`() {
        assumeTrue("Checked on a phone with bars and a sideways phone", "${widthDp}x$heightDp$bars" in KEYBOARD_SIZES && fontScale == 1f)
        show(mutableStateOf(later.copy(aiCustomInputText = "Long drives and old mysteries")), ime = true)
        assertTrue("something scrolls", compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
        val question = compose.onAllNodesWithTag("echo_question").fetchSemanticsNodes().single()
        textLayouts(question).forEach { assertTrue("the question is cut off with the keyboard open", !it.hasVisualOverflow) }
        val field = bounds("echo_answer_input")!!
        assertTrue("the answer field is squeezed (${field.height})", field.height >= 48f)
        val keyboardTop = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot.bottom - ImeHeight
        assertTrue("the answer field is under the keyboard ($field, $keyboardTop)", field.top + 48f <= keyboardTop + 1f)
    }

    // A failed turn: Echo says so in its bubble (or the error stands alone with Echo folded away), whole,
    // centred in the body on phones and the cover, no wider than the conversation, with Try again on screen.
    @Test fun `a failed turn`() {
        show(mutableStateOf(later.copy(onboardingError = "offline")))
        val label = "${widthDp}x$heightDp$bars dp at ${fontScale}x text"
        val root = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot
        val error = compose.onAllNodesWithTag("echo_error").fetchSemanticsNodes().single().boundsInRoot
        compose.onAllNodes(hasAnyAncestor(hasTestTag("echo_error")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap(::textLayouts).forEach { layout ->
                // Centred text is laid out across its whole slot, so only a cut at the bottom or a shortened line counts.
                val cut = layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized)
                assertTrue("the error is cut short on $label: ${layout.layoutInput.text}", !cut)
            }
        val retry = bounds("echo_primary")!!
        compose.onNodeWithTag("echo_primary").assertTextEquals("Try again")
        assertTrue("Try again is cut off on $label", retry.bottom <= root.bottom + 1f)
        assertTrue("the error runs into Try again on $label", error.bottom <= retry.top + 1f)
        if ("${widthDp}x$heightDp$bars" in setOf("412x915", "475x695-bars") && fontScale == 1f) {
            val top = listOf("echo_progress", "echo_more").maxOf { bounds(it)!!.bottom }
            val third = (retry.top - top) / 3f
            assertTrue("the error is off the middle on $label", error.center.y in (top + third)..(retry.top - third))
        }
        if (widthDp >= 1280) assertTrue("the error stretches across $label", error.width <= 672f)
    }

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

    private fun check(state: OnboardingUiState, expectEcho: Boolean, longText: Boolean = false) {
        val isDeck = state.aiDeck.current != null
        assumeTrue("Long text is checked on phones, the cover, sideways phones and short halves", !longText || "${widthDp}x$heightDp$bars" in LONG_TEXT_SIZES)
        show(mutableStateOf(state))
        val label = "${widthDp}x$heightDp$bars dp at ${fontScale}x text"
        checkEcho(label, expectEcho)
        assertTrue("something scrolls on $label", compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
        val root = compose.onNodeWithTag("echo_onboarding_screen").fetchSemanticsNode().boundsInRoot
        // A show card answers with its own buttons; every other turn has a main button.
        bounds("echo_primary")?.let { assertTrue("the main button is cut off on $label", it.bottom <= root.bottom + 1f) }
        if (isDeck) checkDeck(label, root) else checkQuestionAndAnswers(label, root)
        checkReadingOrder(label)
    }

    /** The screen at this size and text size, with the bars this size has (and the keyboard, when [ime]), settled. */
    private fun show(state: State<OnboardingUiState>, ime: Boolean = false) {
        compose.mainClock.autoAdvance = false
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            BoxLoreTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                    LocalEchoShowPreviewFactory provides { _ -> SilentPreview },
                    LocalBoxloreWindowLayout provides BoxloreWindowLayout(widthDp.toFloat(), heightDp.toFloat()),
                ) {
                    // Sideways, the step transition keeps Echo clear of the side buttons and cutout, as in the app.
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                        if (bars == SIDEWAYS) {
                            OnboardingStepTransition(OnboardingStep.AI_ONBOARDING) { EchoOnboardingScreen(state.value, callbacks) }
                        } else {
                            EchoOnboardingScreen(state.value, callbacks)
                        }
                    }
                }
            }
        }
        val keyboard = if (ime) ImeHeight.toInt() else 0
        when (bars) {
            BARS -> compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.PHONE_BARS.copy(imeBottom = keyboard)) }
            SIDEWAYS -> compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.SIDEWAYS_3_BUTTON.copy(imeBottom = keyboard)) }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    /** Echo, wherever it shows, sits below the top bar and clear of everything else. */
    private fun checkEcho(label: String, expectEcho: Boolean) {
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
    }

    /** The show card stays inside the screen above its reactions, and neither its pitch nor its question is cut short. */
    private fun checkDeck(label: String, root: Rect) {
        bounds("echo_deck_reactions")?.let { reactions ->
            assertTrue("the reactions are cut off on $label", reactions.bottom <= root.bottom + 1f)
            assertTrue("the card runs into the reactions on $label", bounds("echo_deck_card")!!.bottom <= reactions.top + 1f)
            val card = bounds("echo_deck_card")!!
            assertTrue("the card runs off the side on $label", card.left >= root.left - 1f && card.right <= root.right + 1f)
            // The show's name always shows, inside the card.
            val title = bounds("echo_deck_title")
            assertTrue("the card has no name on $label", title != null)
            assertTrue("the card's name runs past the card on $label ($title vs $card)", title!!.top >= card.top - 1f && title.bottom <= card.bottom + 1f)
            // ... and whole: the art and the pitch give way before the name ends in "…".
            compose.onAllNodesWithTag("echo_deck_title").fetchSemanticsNodes().flatMap(::textLayouts).forEach { layout ->
                val cut = layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized)
                assertTrue("the card's name is cut short on $label: ${layout.layoutInput.text}", !cut)
            }
            // Each answer's label shows whole, in one row or two.
            EchoDeckReaction.entries.forEach { reaction ->
                compose.onAllNodes(hasAnyAncestor(hasTestTag("echo_deck_${reaction.wire}")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    .fetchSemanticsNodes().flatMap(::textLayouts)
                    .forEach {
                        val detail = "${it.layoutInput.text} (${it.size}, ${it.lineCount} lines at ${it.layoutInput.style.fontSize}, ${it.layoutInput.constraints})"
                        assertTrue("the ${reaction.wire} answer is cut short on $label: $detail", !it.hasVisualOverflow)
                    }
            }
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

    /** The question shows whole and clear of the answers, which end above the main button; Something else is on screen. */
    private fun checkQuestionAndAnswers(label: String, root: Rect) {
        val question = compose.onAllNodesWithTag("echo_question").fetchSemanticsNodes()
        assertEquals("the question is missing on $label", 1, question.size)
        textLayouts(question.single()).forEach {
            assertTrue("the question is cut off on $label", !it.hasVisualOverflow)
            // At default text the question keeps to three lines; larger text may take a fourth at its smallest size.
            if (fontScale == 1f) assertTrue("the question runs past three lines on $label (${it.lineCount})", it.lineCount <= 3)
        }
        val questionBounds = question.single().boundsInRoot
        assertTrue("the question runs off the screen on $label", questionBounds.top >= root.top - 1f && questionBounds.bottom <= root.bottom + 1f)
        val primary = bounds("echo_primary")!!
        val answers = bounds("echo_topics") ?: bounds("echo_choices")!!
        assertTrue("the answers run under the main button on $label ($answers vs $primary)", answers.bottom <= primary.top + 1f)
        assertTrue("the question overlaps the answers on $label ($questionBounds vs $answers)", !questionBounds.overlaps(answers.deflate(1f)))
        val somethingElse = bounds("echo_something_else")!!
        assertTrue("Something else is off screen on $label", somethingElse.top >= root.top - 1f && somethingElse.bottom <= root.bottom + 1f)
        assertTrue("Something else sits under the main button on $label", !somethingElse.overlaps(primary.deflate(1f)))
        assertTrue("the question runs into the footer on $label", questionBounds.bottom <= minOf(primary.top, somethingElse.top) + 1f)
        // Every answer shows whole: no label is cut short.
        val labels = compose.onAllNodes(
            (hasAnyAncestor(hasTestTag("echo_topics")) or hasAnyAncestor(hasTestTag("echo_choices"))) and
                !hasAnyAncestor(hasTestTag("echo_something_else")) and !hasTestTag("echo_something_else") and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("no answer labels on $label", labels.isNotEmpty())
        labels.forEach { node -> textLayouts(node).forEach { assertTrue("an answer is cut short on $label: ${it.layoutInput.text}", !it.hasVisualOverflow) } }
        // Where larger text keeps room for it, Echo says all it has to say.
        val pseudo = compose.activity.resources.configuration.locales[0].country == "XA"
        if ("${widthDp}x$heightDp$bars" == "412x915-bars" && fontScale == 1.3f && !pseudo) {
            compose.onAllNodes(hasAnyAncestor(hasTestTag("echo_acknowledgment")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { node -> textLayouts(node).forEach { assertTrue("Echo's bubble is cut short on $label", !it.hasVisualOverflow) } }
        }
    }

    /** Echo, the question and the answers are read in that order, each as a group, whatever the arrangement. */
    private fun checkReadingOrder(label: String) {
        val order = EchoBodySlotTags.mapNotNull { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.config?.getOrNull(SemanticsProperties.TraversalIndex)
        }
        assertEquals("the body's reading order on $label", listOf(0f, 1f, 2f), order)
    }

    private fun textLayouts(node: SemanticsNode): List<TextLayoutResult> =
        mutableListOf<TextLayoutResult>().also { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(it) }

    private fun bounds(tag: String): Rect? = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    companion object {
        private const val BARS = "-bars"
        private const val SIDEWAYS = "-sideways"
        private val LONG_TEXT_SIZES = setOf("412x915-bars", "475x695-bars", "915x412-sideways", "360x800-bars", "475x330", "673x410", "740x360", "695x475")
        private val WAITING_SIZES = setOf("412x915-bars", "932x314")
        private val KEYBOARD_SIZES = setOf("412x915-bars")

        private const val ImeHeight = 300f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}x{1}{4} font {2}")
        fun sizes(): List<Array<Any>> = listOf(
            // Common phones at default text must show Echo.
            arrayOf<Any>(360, 740, 1.0f, true, ""),
            arrayOf<Any>(393, 852, 1.0f, true, ""),
            arrayOf<Any>(412, 915, 1.0f, true, ""),
            arrayOf<Any>(427, 952, 1.0f, true, ""),
            // Small phones and larger text: Echo may fold away, but never overlaps anything.
            arrayOf<Any>(320, 568, 1.0f, false, ""),
            arrayOf<Any>(360, 640, 1.0f, false, ""),
            arrayOf<Any>(360, 740, 1.3f, false, ""),
            arrayOf<Any>(393, 852, 2.0f, false, ""),
            // Foldables and tablets, both orientations, and a split-screen half.
            arrayOf<Any>(673, 841, 1.0f, false, ""),
            arrayOf<Any>(841, 673, 1.0f, false, ""),
            arrayOf<Any>(800, 1280, 1.0f, false, ""),
            arrayOf<Any>(1280, 800, 1.0f, false, ""),
            arrayOf<Any>(640, 800, 1.3f, false, ""),
            // A phone with its status and gesture bars keeps Echo and shows the whole question.
            arrayOf<Any>(412, 915, 1.0f, true, BARS),
            arrayOf<Any>(412, 915, 2.0f, false, BARS),
            arrayOf<Any>(360, 800, 2.0f, false, BARS),
        ) + listOf(1.0f, 1.3f).flatMap { scale ->
            // Phones and the Fold 8 cover with their bars.
            listOf(412 to 915, 360 to 800, 430 to 932, 384 to 854, 443 to 649, 475 to 695).map { (w, h) -> arrayOf<Any>(w, h, scale, false, BARS) } +
                // A phone on its side with 3-button navigation and a cutout.
                listOf(arrayOf<Any>(915, 412, scale, false, SIDEWAYS)) +
                // A small sideways phone, the cover sideways and in split-screen, a phone split-screen half,
                // the lower halves of foldables in tabletop, and open foldables and tablets.
                listOf(740 to 360, 695 to 475, 412 to 450, 475 to 330, 932 to 314, 841 to 340, 673 to 410, 750 to 832, 932 to 650, 841 to 701, 1600 to 1000)
                    .map { (w, h) -> arrayOf<Any>(w, h, scale, false, "") }
        }.distinctBy { it.toList() }
    }
}
