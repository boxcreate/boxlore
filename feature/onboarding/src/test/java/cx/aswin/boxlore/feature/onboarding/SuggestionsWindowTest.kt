package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shows for you across windows: on wide windows each lane is rows of cards, two deep, with Show all
 * kept open through turns; phones keep their sideways rows; on a sideways phone the footer never
 * covers the first row; and a show's details sheet stays open through window changes, with Add
 * reachable and acting once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestionsWindowTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreTestWindows.TABLET)
    private val toggles = mutableListOf<String>()
    private val rows = listOf(
        OnboardingCurriculumRowDto("Cold cases", (1L..14L).map { OnboardingCurriculumPodcastDto(id = it, title = "Show $it", author = "Host $it") }),
    )
    private val state = mutableStateOf(OnboardingUiState(reachedSuggestionsViaAiFlow = true, aiCurriculumRows = rows))

    private fun render() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        AiSuggestionsScreen(
                            uiState = state.value,
                            callbacks = SuggestionsCallbacks(
                                onBack = {},
                                onToggle = { toggles += it },
                                onSetShowsSelected = { _, _ -> },
                                onRegionChange = {},
                                onRetry = {},
                                onFinish = {},
                            ),
                        )
                    }
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun cardsInGrid() =
        compose.onAllNodes(hasAnyAncestor(hasTestTag("suggestions_grid_curriculum_0")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNodes()

    @Test
    fun `wide windows show lanes as two rows of cards, and Show all stays open through turns`() {
        render()
        val columns = suggestionGridColumns(compose.onNodeWithTag("suggestions_grid_curriculum_0").fetchSemanticsNode().boundsInRoot.width + 40f)
        assertEquals(columns * 2, cardsInGrid().size)
        // No card runs past the lane's edge.
        val lane = compose.onNodeWithTag("suggestions_grid_curriculum_0").fetchSemanticsNode().boundsInRoot
        cardsInGrid().forEach { assertTrue("a card is cut at the column edge", it.boundsInRoot.right <= lane.right + 0.5f) }
        // The cards share the leftover width, so a full row ends within a gap of the lane's edge, or at the widest cards.
        val lastInRow = cardsInGrid().take(columns).maxOf { it.boundsInRoot.right }
        val widest = cardsInGrid().first().boundsInRoot.width >= 168f - 0.5f
        assertTrue("a ragged gap of ${lane.right - lastInRow}dp after the row", widest || lane.right - lastInRow <= 1f)
        compose.onNodeWithTag("suggestions_show_all_curriculum_0").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.walkWindows(window, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.TABLET) { settle() }
        assertEquals(14, cardsInGrid().size)
        // A phone keeps the sideways row.
        compose.walkWindows(window, BoxloreTestWindows.PHONE) { settle() }
        compose.onNodeWithTag("suggestions_grid_curriculum_0").assertDoesNotExist()
    }

    @Test
    fun `on a sideways phone the footer never covers the first row`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        settle()
        val footer = compose.onNodeWithTag("suggestions_footer").fetchSemanticsNode().boundsInRoot
        // As the listener first sees it, before any scrolling: the first card and its name show above the footer.
        val card = compose.onNode(hasText("Show 1") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNode()
        val bottom = card.positionInRoot.y + card.size.height
        assertTrue("the first card's text is under the footer ($bottom vs $footer)", bottom <= footer.top + 0.5f)
    }

    @Test
    fun `a show's details stay open through window changes, and Add acts once`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        state.value = state.value.copy(selectedPodcasts = emptyMap())
        render()
        compose.onNode(hasText("Show 2") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        settle()
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.PHONE_SMALL) { step ->
            settle()
            assertEquals("the details closed at $step", 1, compose.onAllNodes(hasText("Close") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().size)
        }
        compose.onNode(hasText("Add show") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals(listOf("2"), toggles)
    }
}
