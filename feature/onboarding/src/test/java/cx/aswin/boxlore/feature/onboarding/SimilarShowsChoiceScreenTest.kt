package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Search and import stop on "See similar shows" / "Go to Home" before any request, and a slow load
 * can still be left for Home. The choice and its callbacks stay through phone, tablet, split and
 * short windows and recreation (the state lives in the view model, so recreation keeps it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimilarShowsChoiceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val actions = mutableListOf<String>()
    private val picks = mapOf("s1" to Podcast(id = "s1", title = "Show s1", artist = "Host", imageUrl = ""))
    private val state = mutableStateOf(OnboardingUiState())

    @Composable
    private fun screen() {
        BoxLoreTheme(dynamicColor = false) {
            // The screen gets exactly the injected window's size, so short windows really are short.
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                AiSuggestionsScreen(
                    uiState = state.value,
                    callbacks = SuggestionsCallbacks(
                        onBack = { actions += "back" },
                        onToggle = {},
                        onSetShowsSelected = { _, _ -> },
                        onRegionChange = {},
                        onRetry = { actions += "retry" },
                        onFinish = { actions += "finish" },
                        onSeeSimilar = { actions += "similar" },
                        onGoHome = { actions += "home" },
                    ),
                )
                }
            }
        }
    }

    private fun choosing(importPath: Boolean) = OnboardingUiState(
        currentStep = OnboardingStep.AI_SUGGESTIONS,
        reachedSuggestionsViaSearchFlow = !importPath,
        reachedSuggestionsViaOpmlFlow = importPath,
        similarChoicePending = true,
        suggestionSeedCount = 1,
        selectedPodcasts = picks,
        subscribedPodcastIds = picks.keys,
    )

    @Test
    fun `search leads with See similar shows and makes no request until asked`() {
        state.value = choosing(importPath = false)
        compose.setContent { screen() }
        compose.onNodeWithText("Want shows like these?").assertIsDisplayed()
        compose.onNodeWithText("Your pick is in your library").assertIsDisplayed()
        assertEquals(emptyList<String>(), actions)
        compose.onNodeWithTag("suggestions_finish").performClick()
        compose.onNodeWithTag("similar_choice_home").performClick()
        assertEquals(listOf("similar", "home"), actions)
    }

    @Test
    fun `import leads with Go to Home`() {
        state.value = choosing(importPath = true)
        compose.setContent { screen() }
        compose.onNodeWithText("1 imported show is in your library").assertIsDisplayed()
        compose.onNodeWithTag("suggestions_finish").performClick()
        compose.onNodeWithTag("similar_choice_similar").performClick()
        assertEquals(listOf("home", "similar"), actions)
    }

    @Test
    fun `a slow load after asking offers Skip to Home, and Echo's load does not`() {
        state.value = choosing(importPath = false).copy(similarChoicePending = false, isAiLoading = true, isSynthesizing = true)
        // The loading view animates without end; step the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { screen() }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("suggestions_loading").assertIsDisplayed()
        compose.onNodeWithText("Skip to Home").performClick()
        assertEquals(listOf("home"), actions)

        state.value = state.value.copy(reachedSuggestionsViaSearchFlow = false, reachedSuggestionsViaAiFlow = true)
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("suggestions_skip_home").assertDoesNotExist()
    }

    @Test
    fun `the choice survives phone, tablet, split and short windows and still answers once`() {
        state.value = choosing(importPath = true)
        compose.setContent { screen() }
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithTag("suggestions_finish").assertIsDisplayed()
            compose.onNodeWithTag("similar_choice_similar").assertIsDisplayed()
            compose.onNodeWithText("Want shows like these?").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("boxlore can find similar shows in a few seconds, or you can start listening now.").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("suggestions_finish").performClick()
        assertEquals(listOf("home"), actions)
    }

    @Test
    fun `Skip to Home stays reachable while loading through window changes`() {
        state.value = choosing(importPath = false).copy(similarChoicePending = false, isAiLoading = true, isSynthesizing = true)
        compose.mainClock.autoAdvance = false
        compose.setContent { screen() }
        listOf(
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            window.value = next
            compose.mainClock.advanceTimeBy(1_500)
            compose.onNodeWithTag("suggestions_skip_home").assertIsDisplayed()
        }
        compose.onNodeWithTag("suggestions_skip_home").performClick()
        assertEquals(listOf("home"), actions)
    }

    @Test
    fun `the choice is still there after recreation`() {
        state.value = choosing(importPath = false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { screen() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("similar_choice").assertIsDisplayed()
        compose.onNodeWithText("See similar shows").assertIsDisplayed()
    }
}
