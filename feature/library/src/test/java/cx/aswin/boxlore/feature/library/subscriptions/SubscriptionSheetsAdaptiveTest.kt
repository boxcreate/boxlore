package cx.aswin.boxlore.feature.library.subscriptions

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.library.LibraryCaptureFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Subscriptions' sheets become a centred panel on wide windows and half-open folds. Add shows keeps
 * its ticks and search when it switches, both ways, and Save still sends exactly those shows once;
 * a long-press menu in a short window scrolls to its last action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionSheetsAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.W839)
    private val folder = LibraryCaptureFixtures.folders.first { it.id == "commute" }

    @Test
    fun `Add shows keeps its ticks and search as it switches between sheet and panel, then saves once`() {
        val saved = mutableListOf<List<String>>()
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    FolderShowsSelectionSheet(folder, LibraryCaptureFixtures.podcasts, onSave = { saved += it }, onDismissRequest = {})
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("number 2")
        compose.onNodeWithText("Show number 2").performClick()
        compose.onNodeWithText("Show number 20").performClick()

        with(BoxloreTestWindows) {
            compose.walkWindows(window, W841, W839, FOLD_FLAT, FOLD_TABLETOP, FOLD_FLAT, TABLET) { step ->
                compose.onNode(hasSetTextAction()).assertTextContains("number 2")
                compose.onNodeWithText("4 selected", substring = true, ignoreCase = true).assertExists()
                if (step == W841 || step == FOLD_TABLETOP || step == TABLET) compose.onNodeWithTag("expanded_sheet_panel").assertExists()
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(folder.podcastIds + listOf("show-1", "show-19")), saved) }
    }

    @Test
    // The sheet lives in its own window, sized by the device: the short sideways viewport itself.
    @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun `a folder's long-press menu scrolls to Delete folder on a sideways phone`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        val deleted = mutableListOf<SubscriptionFolder>()
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    SubscriptionContextMenuSheet(
                        target = ContextMenuTarget.Folder(folder),
                        actions = SubscriptionContextMenuActions(onDeleteFolder = { deleted += it }),
                        onDismissRequest = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Delete folder").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(folder), deleted) }
    }
}
