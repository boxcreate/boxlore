package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SuggestionsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val actions = mutableListOf<String>()
    private val state = mutableStateOf(OnboardingUiState())

    private fun show(id: String) = Podcast(id = id, title = "Show $id", artist = "Host $id", imageUrl = "")

    private val rows = listOf(
        OnboardingCurriculumRowDto("Cold cases", listOf(1L, 2L, 3L).map { OnboardingCurriculumPodcastDto(id = it, title = "Show $it", author = "Host $it") }),
    )

    private fun toggle(id: String) {
        val s = state.value
        val selected = if (id in s.subscribedPodcastIds) s.selectedPodcasts - id else s.selectedPodcasts + (id to show(id))
        state.value = s.copy(selectedPodcasts = selected, subscribedPodcastIds = selected.keys)
    }

    private fun render() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BoxLoreTheme {
                AiSuggestionsScreen(
                    uiState = state.value,
                    callbacks = SuggestionsCallbacks(
                        onBack = { actions += "back" },
                        onToggle = ::toggle,
                        onSetShowsSelected = { shows, on ->
                            actions += "all:$on"
                            val s = state.value
                            val selected = if (on) s.selectedPodcasts + shows.associateBy { it.id } else s.selectedPodcasts - shows.map { it.id }.toSet()
                            state.value = s.copy(selectedPodcasts = selected, subscribedPodcastIds = selected.keys)
                        },
                        onRegionChange = {},
                        onRetry = { actions += "retry" },
                        onFinish = { actions += "finish" },
                    ),
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun card(title: String) = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasText(title, substring = true))

    @Test
    fun `Echo's picks lead selected, suggestions start unselected, and the button counts what is added`() {
        state.value = OnboardingUiState(
            reachedSuggestionsViaAiFlow = true,
            aiCurriculumRows = rows,
            aiPicks = listOf(show("9")),
            selectedPodcasts = mapOf("9" to show("9")),
            subscribedPodcastIds = setOf("9"),
        )
        render()
        compose.onNodeWithText("Here’s what I found. Tap to add, hold for details.").assertIsDisplayed()
        compose.onNodeWithText("Your picks").assertIsDisplayed()
        card("Show 9").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        card("Show 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        compose.onNodeWithText("Add 1 show").assertIsDisplayed()

        card("Show 1").performClick()
        settle()
        compose.onNodeWithText("Add 2 shows").assertIsDisplayed()
        compose.onNodeWithTag("suggestions_lane_curriculum_0").assertIsDisplayed()
        compose.onNodeWithText("Add all").performClick()
        settle()
        compose.onNodeWithText("Add 4 shows").performClick()
        assertEquals(listOf("all:true", "finish"), actions)
    }

    @Test
    fun `nothing picked reads Start without adding`() {
        state.value = OnboardingUiState(aiCurriculumRows = rows)
        render()
        compose.onNodeWithText("Start without adding").assertIsDisplayed().performClick()
        assertEquals(listOf("finish"), actions)
    }

    @Test
    fun `search picks are summed up as already in the library and do not count as additions`() {
        state.value = OnboardingUiState(
            reachedSuggestionsViaSearchFlow = true,
            suggestionSeedCount = 2,
            aiCurriculumRows = rows,
            selectedPodcasts = mapOf("s1" to show("s1"), "s2" to show("s2")),
            subscribedPodcastIds = setOf("s1", "s2"),
        )
        render()
        compose.onNodeWithTag("suggestions_seed_summary").assertIsDisplayed()
        compose.onNodeWithText("Your 2 picks are in your library").assertIsDisplayed()
        compose.onNodeWithText("Start without adding").assertIsDisplayed()
    }

    @Test
    fun `a failed load offers Try again and a way to start anyway`() {
        state.value = OnboardingUiState(reachedSuggestionsViaAiFlow = true, onboardingError = "offline")
        render()
        compose.onNodeWithText("Couldn’t load suggestions").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Start without adding").performClick()
        assertEquals(listOf("retry", "finish"), actions)
    }
}
