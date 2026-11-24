package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Loading shows the page's outline only after a moment, and empty pages show their section mark
 * with a button that stays reachable in short windows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryStatesTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)

    @Test
    fun `the hub's loading outline waits a moment, then shows cards where the real ones go`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(412.dp, 915.dp)) { LibraryContent(LibraryUiState.Loading, {}, {}, {}, {}) }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(LIBRARY_LOADING_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SKELETON_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithTag(LIBRARY_SKELETON_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `an empty Liked page shows its mark and keeps Find podcasts reachable in short windows`() {
        var explored = 0
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        LikedEpisodesScreen(fixtures.viewModel(LibraryUiState.Success()), {}, { explored++ }, { _, _ -> })
                    }
                }
            }
        }
        for (step in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.PHONE)) {
            compose.runOnIdle { window.value = step }
            compose.onNodeWithTag(LIBRARY_EMPTY_ICON_TAG, useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Find podcasts").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Find podcasts").performClick()
        assertEquals(1, explored)
    }
}
