package cx.aswin.boxlore.feature.library.subscriptions

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.library.FolderSaveParams
import cx.aswin.boxlore.feature.library.SubscriptionSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The open folder, the folder editors, Auto-organize and Add to folders keep their drafts, step and
 * ticks while the window moves between phone, tablet, split and short layouts, both ways, and when
 * the activity is recreated; their Back, Close, Create, Save, Turn on and Done still do the current
 * thing afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderPagesAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private var reorderMode by mutableStateOf<ReorderMode>(ReorderMode.Inactive)
    private lateinit var state: FolderPagesState
    private val created = mutableListOf<Pair<FolderSaveParams, List<String>>>()
    private val updated = mutableListOf<SubscriptionFolder>()
    private var autoLook by mutableStateOf(AutoOrganizeLook())
    private val turnedOn = mutableListOf<AutoOrganizeLook>()
    private val memberships = mutableListOf<Pair<String, FolderMembershipChange>>()

    private val podcasts = listOf(
        TestFixtures.podcast(id = "a", title = "Alpha Radio", genre = "News"),
        TestFixtures.podcast(id = "b", title = "Bravo Hour", genre = "Comedy"),
    )
    private val commute = SubscriptionFolder(
        id = "f",
        name = "Commute",
        displaySize = FolderDisplaySize.SHELF,
        podcastCount = 2,
        podcastIds = listOf("a", "b"),
    )
    private val later = SubscriptionFolder(id = "later", name = "Later")

    /** Both sides of the 600dp width and 480dp height gates, the 840dp split, and back to phone. */
    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    private val actions = object : FolderPagesActions {
        override fun openShow(podcastId: String) = Unit
        override fun showMenu(target: ContextMenuTarget) = Unit
        override fun addShows(folder: SubscriptionFolder) = Unit
        override fun openSort() = Unit
        override fun setReorderMode(mode: ReorderMode) {
            reorderMode = mode
        }
        override fun saveFolderOrder(folderId: String, orderedIds: List<String>) = Unit
        override fun deleteFolder(folder: SubscriptionFolder) = Unit
        override fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?) {
            created += params to podcastIds
        }
        override fun updateFolder(folder: SubscriptionFolder) {
            updated += folder
        }
        override fun changeMembership(podcastId: String, change: FolderMembershipChange) {
            memberships += podcastId to change
        }
        override fun turnOnAutoOrganize(look: AutoOrganizeLook) {
            turnedOn += look
        }
        override fun turnOffAutoOrganize() = Unit
        override fun setAutoOrganizeLook(look: AutoOrganizeLook) {
            autoLook = look
        }
        override fun applyLookToGenreFolders(look: AutoOrganizeLook) = Unit
    }

    @Composable
    private fun pages() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                state = rememberFolderPagesState()
                val data = FolderPagesData(
                    podcasts = podcasts,
                    folders = listOf(commute, later),
                    sort = SubscriptionSort.Alphabetical,
                    smartOrderIds = emptyList(),
                    intraFolderSort = FolderIntraSort.Inherit,
                    autoOrganizeOn = false,
                    autoOrganizeLook = autoLook,
                    reorderMode = reorderMode,
                )
                OpenFolderPage(state, data, actions, bottomPadding = 0.dp)
                FolderEditorPage(state, data, actions)
                AddToFoldersHost(state, data, actions)
            }
        }
    }

    private fun moveThroughWindows(check: () -> Unit) {
        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            check()
        }
    }

    @Test
    fun `a new folder keeps its step and picks across window changes, then creates`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.openNewFolder() }
        compose.onNode(hasContentDescription("Alpha Radio, in Commute") and hasClickAction()).performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Drive")

        moveThroughWindows {
            compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).assertTextContains("Drive")
        }

        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("Create folder").performClick()
        compose.runOnIdle {
            assertEquals(listOf("a"), created.single().second)
            assertEquals("Drive", created.single().first.name)
            assertNull(state.editor)
        }
    }

    @Test
    fun `a new folder survives recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { pages() }
        compose.runOnIdle { state.openNewFolder(withShowId = "b") }
        compose.onNodeWithText("Next").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Laughs")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Laughs")
        compose.runOnIdle { assertEquals(listOf("b"), state.newFolderDraft.pickedIds) }
    }

    @Test
    fun `back goes one step back, then close leaves the flow`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.openNewFolder(withShowId = "a") }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle {
            assertNull(state.editor)
            assertEquals(emptyList<Pair<FolderSaveParams, List<String>>>(), created)
        }
    }

    @Test
    fun `an edit keeps its unsaved name across window changes, then saves`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.openEdit(commute) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Drive")

        moveThroughWindows { compose.onNode(hasSetTextAction()).assertTextContains("Drive") }

        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals("Drive", updated.single().name)
            assertNull(state.editor)
        }
    }

    @Test
    fun `the open folder stays open across window changes and Back ends reordering first`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.openFolder("f") }

        moveThroughWindows { compose.onAllNodesWithText("Commute").onFirst().assertIsDisplayed() }

        compose.runOnIdle { reorderMode = ReorderMode.FolderShows("f", "Commute") }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle {
            assertEquals(ReorderMode.Inactive, reorderMode)
            assertEquals("f", state.openFolderId)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertNull(state.openFolderId) }
    }

    @Test
    fun `auto-organize keeps its look across window changes, then turns on with it and closes`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.openAutoOrganize() }
        compose.onNodeWithText("Use one look for all").performScrollTo().performClick()

        moveThroughWindows {
            compose.onNodeWithText("Turn on auto-organize").assertIsDisplayed()
            compose.runOnIdle { assertEquals(FolderEditor.AutoOrganize, state.editor) }
        }

        compose.onNodeWithText("Turn on auto-organize").performClick()
        compose.runOnIdle { assertEquals(FolderDisplaySize.SHELF, turnedOn.single().fixedSize) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertNull(state.editor) }
    }

    @Test
    fun `add to folders keeps its ticks across window changes, then saves them`() {
        compose.setContent { pages() }
        compose.runOnIdle { state.addToFoldersPodcastId = "a" }
        compose.onNodeWithText("Later").performClick()

        moveThroughWindows { compose.onNodeWithText("Done").assertIsDisplayed() }

        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals("a" to FolderMembershipChange(addTo = listOf("later"), removeFrom = emptyList()), memberships.single())
            assertNull(state.addToFoldersPodcastId)
        }
    }
}
