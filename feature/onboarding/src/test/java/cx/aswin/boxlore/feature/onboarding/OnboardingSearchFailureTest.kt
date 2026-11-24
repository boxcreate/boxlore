package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A show search that couldn't be read offers Try again, not "No shows found", through live phone,
 * tablet, split and short windows, and Try again asks once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingSearchFailureTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val failed = mutableStateOf(true)
    private var retries = 0

    private fun render() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        OnboardingSearchScreen(
                            query = "darknet diaries",
                            results = emptyList(),
                            isSearching = false,
                            searchFailed = failed.value,
                            onRetrySearch = { retries++ },
                            subscribedIds = emptySet(),
                            selectedPodcasts = emptyMap(),
                            onQueryChange = {},
                            onSubscribe = {},
                            onBack = {},
                            onDone = {},
                            popularPodcasts = emptyList(),
                            isPopularLoading = false,
                            selectedSearchGenre = null,
                            onGenreSelect = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a failed search offers Try again through live window changes`() {
        render()
        listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(1_280f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
        ).forEach { size ->
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            compose.onNodeWithText("Couldn’t search right now").assertIsDisplayed()
            compose.onNodeWithText("No shows found").assertDoesNotExist()
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a search that was read and found nothing still says no matches`() {
        failed.value = false
        render()
        compose.onNodeWithText("No shows found").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
}
