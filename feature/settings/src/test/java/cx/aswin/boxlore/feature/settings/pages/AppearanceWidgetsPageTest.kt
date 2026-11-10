package cx.aswin.boxlore.feature.settings.pages

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceWidgetsPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val added = mutableListOf<String>()
    private val coverChoices = mutableListOf<String>()
    private val widgets =
        listOf(
            AddableWidget("full", "Now Playing · Full Controls", "The full player", 320, 170),
            AddableWidget("round", "Now Playing · Round", "A round player", 160, 160),
            AddableWidget("round_next", "Now Playing · Round with Next", "A round player with next", 160, 160),
            AddableWidget("shows", "Library · Your Shows", "Your subscribed shows as a grid of artwork", 320, 290, usesShowsCoverStyle = true),
        )
    private val state =
        AppearanceUiState(
            currentThemeConfig = "system",
            isDynamicColorEnabled = false,
            currentThemeBrand = "violet",
            currentSurfaceStyle = "classic_dynamic",
            addableWidgets = widgets,
        )
    private val picture: AppearanceWidgetPreview = { widget, modifier -> Box(modifier.testTag("picture-${widget.key}")) }

    private fun render(canAdd: Boolean, state: AppearanceUiState = this.state, preview: AppearanceWidgetPreview? = picture) {
        val actions =
            AppearanceActions({}, {}, {}, {}, canAddWidgets = { canAdd }, onAddWidget = { added.add(it) }, onSetWidgetShowsArtwork = { coverChoices.add(it) })
        compose.setContent { BoxLoreTheme { AppearanceWidgetsPage(state, actions, onBack = {}, preview = preview) } }
    }

    @Test
    fun `each widget shows its picture and a button that asks the home screen to add that widget`() {
        render(canAdd = true)
        compose.onNodeWithText("Add to home screen").performScrollTo().assertIsDisplayed()
        for (widget in widgets) {
            compose.onNodeWithTag("picture-${widget.key}", useUnmergedTree = true).assertExists()
            compose.onNodeWithText(widget.name).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Add Library · Your Shows to home screen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add Now Playing · Round to home screen").performScrollTo().performClick()
        assertEquals(listOf("shows", "round"), added)
        compose.onNodeWithText(ADD_WIDGETS_BY_HAND).assertDoesNotExist()
    }

    @Test
    fun `a picture keeps its widget's shape and is never wider than the widget really is`() {
        render(canAdd = true)
        for (widget in widgets) {
            val bounds = compose.onNodeWithTag("picture-${widget.key}", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val width = (bounds.right - bounds.left).value
            val height = (bounds.bottom - bounds.top).value
            assertTrue("${widget.key} is drawn", width > 80f && height > 40f)
            assertTrue("${widget.key} is at most its real width", width <= widget.previewWidthDp + 0.5f)
            assertEquals("${widget.key} keeps its shape", widget.previewWidthDp.toFloat() / widget.previewHeightDp, width / height, 0.03f)
        }
        // The two round players share a row.
        val round = compose.onNodeWithTag("picture-round", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val roundNext = compose.onNodeWithTag("picture-round_next", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(round.top, roundNext.top)
        assertTrue(roundNext.left > round.right)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w1280dp-h800dp-mdpi")
    fun `on a tablet the pictures stay at their real size instead of stretching`() {
        render(canAdd = true)
        val full = compose.onNodeWithTag("picture-full", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(320.dp, full.right - full.left)
        compose.onNodeWithContentDescription("Add Now Playing · Full Controls to home screen").performScrollTo().performClick()
        assertEquals(listOf("full"), added)
    }

    @Test
    fun `a home screen that cannot add widgets still shows them, with the steps to add one by hand`() {
        render(canAdd = false)
        compose.onNodeWithText(ADD_WIDGETS_BY_HAND).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("boxlore widgets").assertExists()
        compose.onNodeWithText("Add to home screen").assertDoesNotExist()
        compose.onNodeWithTag("picture-full", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Add Now Playing · Full Controls to home screen").assertDoesNotExist()
        // The widget options stay either way.
        compose.onNodeWithText("Whole covers").assertExists()
        compose.onNodeWithText("Colors").assertExists()
    }

    @Test
    fun `the cover choice sits in the Your Shows card, between its picture and its name`() {
        render(canAdd = true)
        compose.onNodeWithText("Your Shows covers").assertDoesNotExist()
        val picture = compose.onNodeWithTag("picture-shows", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val choice = compose.onNodeWithText("Shapes").getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText("Library · Your Shows").getUnclippedBoundsInRoot()
        assertTrue("under the picture it changes", choice.top >= picture.bottom)
        assertTrue("above the widget's name", choice.bottom <= name.top)
        compose.onNodeWithText("Shapes").performScrollTo().performClick()
        assertEquals(listOf(WidgetShowsArtwork.SHAPES), coverChoices)
    }

    @Test
    fun `a request the phone leaves unanswered explains itself in that widget's card`() {
        render(canAdd = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add Now Playing · Full Controls to home screen").performScrollTo().performClick()
        // Nothing covered the app: the phone turned the request down without a word.
        compose.mainClock.advanceTimeBy(ADD_WIDGET_ANSWER_MS + 100)
        compose.onNodeWithText(ADD_WIDGET_UNANSWERED).assertExists()
        compose.onNodeWithText("Open app settings").assertExists()
        val tip = compose.onNodeWithText(ADD_WIDGET_UNANSWERED).getUnclippedBoundsInRoot()
        val nextCard = compose.onNodeWithTag("picture-round", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("inside the card that was tapped", tip.bottom <= nextCard.top)
        assertEquals(listOf("full"), added)
    }

    @Test
    fun `a request the home screen answers shows nothing more, and clears an earlier unanswered one`() {
        render(canAdd = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Add Now Playing · Full Controls to home screen").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(ADD_WIDGET_ANSWER_MS + 100)
        compose.onNodeWithText(ADD_WIDGET_UNANSWERED).assertExists()

        compose.onNodeWithContentDescription("Add Now Playing · Full Controls to home screen").performClick()
        // The home screen app's confirmation covers the app, then the listener comes back.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.mainClock.advanceTimeBy(ADD_WIDGET_ANSWER_MS + 100)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(ADD_WIDGET_UNANSWERED).assertDoesNotExist()
        assertEquals(listOf("full", "full"), added)
    }

    @Test
    fun `without pictures each widget is a row that adds it and the cover choice keeps its own group`() {
        render(canAdd = true, preview = null)
        compose.onNodeWithText("Your Shows covers").assertExists()
        compose.onNodeWithText("Library · Your Shows").performScrollTo().performClick()
        assertEquals(listOf("shows"), added)
    }

    @Test
    fun `with no widgets to offer the page is only the options`() {
        render(canAdd = true, state = state.copy(addableWidgets = emptyList()))
        compose.onNodeWithText("Add to home screen").assertDoesNotExist()
        compose.onNodeWithText(ADD_WIDGETS_BY_HAND).assertDoesNotExist()
        compose.onNodeWithText("Whole covers").assertExists()
    }

    @Test
    fun `small widgets pair up and every other widget has a row to itself`() {
        assertEquals(listOf(listOf("full"), listOf("round", "round_next"), listOf("shows")), widgetGalleryRows(widgets).map { row -> row.map { it.key } })
        // A lone small widget, or a third one, is not stretched into a pair.
        val three = widgets.filter { it.previewWidthDp <= 200 } + AddableWidget("extra", "Extra", "Another small one", 160, 160)
        assertEquals(listOf(listOf("round", "round_next"), listOf("extra")), widgetGalleryRows(three).map { row -> row.map { it.key } })
    }
}
