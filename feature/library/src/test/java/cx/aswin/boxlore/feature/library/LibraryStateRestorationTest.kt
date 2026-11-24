package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuKey
import cx.aswin.boxlore.feature.library.subscriptions.MoveShowKey
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Subscriptions and Downloads are in the middle of comes back after the activity is
 * recreated (a text or display size change, a move to another display, the app closed in the
 * background): the search, an open long-press menu, a reorder with its unsaved order, and a
 * selection. A target that went away meanwhile is dropped quietly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryStateRestorationTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures

    @Test
    fun `a typed search and an open show menu come back after recreation`() {
        val restorer = StateRestorationTester(compose)
        val vm = fixtures.viewModel(fixtures.loaded(), gridView = false, folders = emptyList())
        restorer.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) { SubscriptionsScreen(vm, onBack = {}, onPodcastClick = {}, onExploreClick = {}) }
            }
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("number 1")
        compose.onAllNodesWithText("Show number 12")[0].performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Reorder shows").assertExists()

        restorer.emulateSavedInstanceStateRestore()

        compose.onNode(hasSetTextAction()).assertTextContains("number 1")
        compose.onNodeWithText("Reorder shows").assertExists()
    }

    @Test
    fun `saved state keeps a reorder in progress and every open target by ID`() {
        val state = SubscriptionsScreenState(
            searchQuery = "news",
            isSearchActive = true,
            sortSheetOpen = true,
            contextMenu = ContextMenuKey(ContextMenuKey.Kind.FolderPodcast, "news", "show-3"),
            addShowsFolderId = "news",
            moveShow = MoveShowKey(null, "show-4"),
            confirmDeleteFolderId = "solo",
            confirmUnsubscribeId = "show-5",
            confirmAutoOrganizeOff = true,
            reorderMode = ReorderMode.FolderShows("news", "Nachrichten-Ecke"),
            tempFolderOrder = listOf("solo", "news"),
            tempRootOrder = listOf("show-9", "show-8"),
        )
        val saved = requireNotNull(with(SubscriptionsScreenState.Saver) { SaverScope { true }.save(state) })
        val restored = requireNotNull(SubscriptionsScreenState.Saver.restore(saved))
        assertEquals("news", restored.searchQuery)
        assertEquals(true, restored.isSearchActive)
        assertEquals(true, restored.sortSheetOpen)
        assertEquals(state.contextMenu, restored.contextMenu)
        assertEquals("news", restored.addShowsFolderId)
        assertEquals(state.moveShow, restored.moveShow)
        assertEquals("solo", restored.confirmDeleteFolderId)
        assertEquals("show-5", restored.confirmUnsubscribeId)
        assertEquals(true, restored.confirmAutoOrganizeOff)
        assertEquals(ReorderMode.FolderShows("news", "Nachrichten-Ecke"), restored.reorderMode)
        assertEquals(listOf("solo", "news"), restored.tempFolderOrder)
        assertEquals(listOf("show-9", "show-8"), restored.tempRootOrder)
        for (mode in listOf(ReorderMode.Inactive, ReorderMode.Folders, ReorderMode.RootShows)) {
            val again = SubscriptionsScreenState.Saver.restore(requireNotNull(with(SubscriptionsScreenState.Saver) { SaverScope { true }.save(SubscriptionsScreenState(reorderMode = mode)) }))
            assertEquals(mode, again?.reorderMode)
        }
    }

    @Test
    fun `an open question or menu for a show that went away is dropped`() {
        val state = SubscriptionsScreenState(
            confirmUnsubscribeId = "show-gone",
            contextMenu = ContextMenuKey(ContextMenuKey.Kind.RootPodcast, null, "show-gone"),
            moveShow = MoveShowKey("gone-folder", "show-3"),
            confirmDeleteFolderId = "news",
        )
        state.dropMissing(fixtures.folders, fixtures.podcasts)
        assertNull(state.confirmUnsubscribeId)
        assertNull(state.contextMenu)
        assertNull(state.moveShow)
        assertEquals("news", state.confirmDeleteFolderId)
    }

    @Test
    fun `two selected downloaded shows stay selected after recreation and Delete removes exactly them`() {
        val restorer = StateRestorationTester(compose)
        val vm = fixtures.viewModel(fixtures.loaded())
        restorer.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    DownloadedEpisodesScreen(vm, fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
                }
            }
        }
        compose.onNodeWithContentDescription("Select podcasts").performClick()
        compose.onNodeWithText("Downloaded show 2").performClick()
        compose.onNodeWithText("Downloaded show 3").performClick()
        compose.onNodeWithText("2 selected").assertExists()

        restorer.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithContentDescription("Delete selected").performClick()
        compose.onNodeWithText("Delete").performClick()
        val removed = mockingDetails(vm).invocations.filter { it.method.name == "removeMultipleDownloads" }
        val expected = fixtures.downloads().filter { it.podcastId == "show-1" || it.podcastId == "show-2" }.map { it.episodeId }.toSet()
        assertEquals(listOf(expected), removed.map { (it.arguments[0] as List<*>).toSet() })
    }
}
