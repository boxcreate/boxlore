package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.HiddenShow
import cx.aswin.boxlore.core.prefs.DiscoverySections
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LeaveMeAlone's Hidden shows: the empty state, each hidden show with Unhide, a show without a saved
 * name, and the same list while the window crosses the tablet, split and short gates both ways.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HiddenShowsSectionTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val shows = mutableStateOf<List<HiddenShow>?>(emptyList())
    private val unhidden = mutableListOf<String>()

    @Composable
    private fun page() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                RecommendationsSettingsPage(
                    sections = DiscoverySections(),
                    onSet = { _, _ -> },
                    onBack = {},
                    hiddenShows = HiddenShowsControl(shows.value) { show ->
                        unhidden += show.id
                        shows.value = shows.value.orEmpty() - show
                    },
                )
            }
        }
    }

    @Test
    fun `with nothing hidden it says how to hide a show`() {
        compose.setContent { page() }
        compose.onNodeWithText("Hidden shows").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HIDDEN_SHOWS_EMPTY_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No hidden shows").assertIsDisplayed()
        compose.onNodeWithText("To hide one, open the show and choose Hide show everywhere from its menu.").assertIsDisplayed()
    }

    @Test
    fun `before the stored list is read it shows neither the empty state nor the group`() {
        // Reviewed: the list started empty, so "No hidden shows" flashed before the hidden shows.
        shows.value = null
        compose.setContent { page() }
        compose.onNodeWithTag(HIDDEN_SHOWS_EMPTY_TAG).assertDoesNotExist()
        compose.onNodeWithText("Hidden shows").assertDoesNotExist()

        compose.runOnIdle { shows.value = listOf(HiddenShow("1", "Curious world")) }
        compose.onNodeWithText("Curious world").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(HIDDEN_SHOWS_EMPTY_TAG).assertDoesNotExist()
    }

    @Test
    fun `each hidden show unhides on its own and the empty state returns`() {
        shows.value = listOf(HiddenShow("1", "Curious world"), HiddenShow("rss:a", ""))
        compose.setContent { page() }
        compose.onNodeWithText("Curious world").performScrollTo().assertIsDisplayed()
        // A show hidden before its name was known still has a row.
        compose.onNodeWithText("Unknown show").performScrollTo().assertIsDisplayed()
        // Every row has the shared artwork, which draws boxlore's mark when the show has none.
        assertEquals(2, compose.onAllNodesWithTag(HIDDEN_SHOW_ART_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)

        compose.onNodeWithContentDescription("Unhide Curious world").performScrollTo().performClick()
        compose.onNodeWithText("Curious world").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unhide Unknown show").performScrollTo().performClick()
        assertEquals(listOf("1", "rss:a"), unhidden)
        compose.onNodeWithTag(HIDDEN_SHOWS_EMPTY_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the list and Unhide stay the same across window changes and recreation`() {
        shows.value = listOf(HiddenShow("1", "Curious world"), HiddenShow("2", "Night owls"))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { page() }
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Curious world").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Night owls").performScrollTo().assertIsDisplayed()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Unhide Night owls").performScrollTo().performClick()
        assertEquals(listOf("2"), unhidden)
        compose.onNodeWithText("Curious world").performScrollTo().assertIsDisplayed()
    }
}
