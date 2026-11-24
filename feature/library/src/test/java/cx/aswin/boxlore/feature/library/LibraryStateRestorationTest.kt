package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.library.downloads.DOWNLOADS_EMPTY_PAGE_TAG
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuKey
import cx.aswin.boxlore.feature.library.subscriptions.MoveShowKey
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
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
    fun `a restored move waits for the folders, so it moves rather than adds`() {
        val state = SubscriptionsScreenState(moveShow = MoveShowKey(fromFolderId = "news", podcastId = "show-3"))
        assertNull("No sheet before the folders load", state.moveTarget(folders = null, podcasts = fixtures.podcasts))
        val target = state.moveTarget(fixtures.folders, fixtures.podcasts)
        assertEquals("news", target?.first?.id)
        assertEquals("show-3", target?.second?.id)
    }

    @Test
    fun `folder targets wait for the folders when the shows load first after a restart`() {
        val waiting = SubscriptionsScreenState(
            contextMenu = ContextMenuKey(ContextMenuKey.Kind.FolderPodcast, "news", "show-3"),
            reorderMode = ReorderMode.FolderShows("news", "Nachrichten-Ecke"),
            tempFolderOrder = listOf("show-11", "show-3"),
            addShowsFolderId = "news",
        )
        waiting.dropMissing(folders = null, podcasts = fixtures.podcasts)
        assertEquals(ContextMenuKey(ContextMenuKey.Kind.FolderPodcast, "news", "show-3"), waiting.contextMenu)
        assertEquals(ReorderMode.FolderShows("news", "Nachrichten-Ecke"), waiting.reorderMode)
        assertEquals(listOf("show-11", "show-3"), waiting.tempFolderOrder)
        assertEquals("news", waiting.addShowsFolderId)

        // Once the folders are in, a folder that really went away still closes its targets.
        waiting.dropMissing(folders = fixtures.folders.filter { it.id != "news" }, podcasts = fixtures.podcasts)
        assertNull(waiting.contextMenu)
        assertEquals(ReorderMode.Inactive, waiting.reorderMode)
        assertNull(waiting.addShowsFolderId)
    }

    @Test
    fun `an open folder menu survives a restart where the shows load before the folders`() {
        val restorer = StateRestorationTester(compose)
        // Not loaded yet: null, never an empty list standing in for the folders.
        val loadedFolders = MutableStateFlow<List<SubscriptionFolder>?>(null)
        val restarted = fixtures.viewModel(fixtures.loaded(), folders = emptyList())
        `when`(restarted.loadedFolders).thenReturn(loadedFolders)
        val vm = mutableStateOf(fixtures.viewModel(fixtures.loaded()))
        restorer.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) { SubscriptionsScreen(vm.value, onBack = {}, onPodcastClick = {}, onExploreClick = {}) }
            }
        }
        compose.onNodeWithText("Nachrichten-Ecke").performTouchInput { longClick() }
        compose.onNodeWithText("Delete folder").assertExists()

        vm.value = restarted
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        loadedFolders.value = fixtures.folders
        compose.waitForIdle()

        compose.onNodeWithText("Delete folder").assertExists()
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

    @Test
    fun `a restored selection keeps only the shows still downloaded`() {
        val restorer = StateRestorationTester(compose)
        val vm = mutableStateOf(fixtures.viewModel(fixtures.loaded()))
        restorer.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    DownloadedEpisodesScreen(vm.value, fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
                }
            }
        }
        compose.onNodeWithContentDescription("Select podcasts").performClick()
        compose.onNodeWithText("Downloaded show 2").performClick()
        compose.onNodeWithText("1 selected").assertExists()

        // Show 2's downloads went away before the restart.
        vm.value = fixtures.viewModel(fixtures.loaded().copy(downloadedEpisodes = fixtures.downloads().filter { it.podcastId != "show-1" }))
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("1 selected").assertDoesNotExist()
        compose.onNodeWithContentDescription("Select all").performClick()
        compose.onNodeWithText("3 selected").assertExists()
    }

    @Test
    fun `an open Smart Downloads card stays open when the first download arrives`() {
        val state = MutableStateFlow<LibraryUiState>(fixtures.loaded().copy(downloadedEpisodes = emptyList()))
        val vm = fixtures.viewModel(fixtures.loaded())
        `when`(vm.uiState).thenReturn(state)
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    DownloadedEpisodesScreen(vm, fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
                }
            }
        }
        compose.onNodeWithContentDescription("Expand").performClick()
        compose.onNodeWithContentDescription("Smart Downloads Settings").assertExists()

        state.value = fixtures.loaded()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Smart Downloads Settings").assertExists()
    }

    @Test
    fun `in a short window the open Smart Downloads card and the empty page both scroll into reach`() {
        val vm = fixtures.viewModel(fixtures.loaded().copy(downloadedEpisodes = emptyList()))
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE_LAND) {
                BoxLoreTheme(dynamicColor = false) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredSize(412.dp, 300.dp)) {
                        DownloadedEpisodesScreen(vm, fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Expand").performClick()
        compose.onNodeWithText("Sync now").performScrollTo().assertIsDisplayed()
        // The page scrolls on past the dashboard to the empty page, whose own content scrolls to its button.
        compose.onNodeWithTag(DOWNLOADS_EMPTY_PAGE_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.onNodeWithText("Find podcasts").performScrollTo().assertIsDisplayed()
    }
}
