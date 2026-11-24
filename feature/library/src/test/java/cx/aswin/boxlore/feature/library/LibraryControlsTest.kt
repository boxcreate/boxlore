package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.library.playlists.PlaylistCreateRequest
import cx.aswin.boxlore.feature.library.playlists.PlaylistPagesState
import cx.aswin.boxlore.feature.library.playlists.PlaylistPlaybackSettings
import cx.aswin.boxlore.feature.library.playlists.PlaylistsActions
import cx.aswin.boxlore.feature.library.playlists.PlaylistsContent
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeLook
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizePage
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizePageActions
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.FOLDER_LOOK_TILE_TAG
import cx.aswin.boxlore.feature.library.subscriptions.FolderLookRow
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowsSelectionSheet
import cx.aswin.boxlore.feature.library.subscriptions.LatestSortActions
import cx.aswin.boxlore.feature.library.subscriptions.LatestSortSheetContent
import cx.aswin.boxlore.feature.library.subscriptions.ShowContextAction
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuSheet
import cx.aswin.boxlore.feature.library.subscriptions.showContextActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library's small controls behave as single, named controls: sort and switch rows, menus, tick
 * rows, buttons at touch size, choice selectors and tiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryControlsTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
            BoxLoreTheme(dynamicColor = false) { content() }
        }
    }

    private val isToggleable = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun `New Episodes sort offers two order pills and one Hide played switch row`() {
        val orders = mutableListOf<Boolean>()
        val hides = mutableListOf<Boolean>()
        show { LatestSortSheetContent(useSmartRank = true, hideCompleted = false, actions = LatestSortActions({ orders += it }, { hides += it }, {})) }
        compose.onNodeWithText("Chronological").performClick()
        val hidePlayed = compose.onNode(hasText("Hide played episodes") and isToggleable and hasRole(Role.Switch))
        hidePlayed.performClick()
        assertEquals(listOf(false), orders)
        assertEquals(listOf(true), hides)
    }

    @Test
    fun `a long-press on a playlist opens its menu, which stays open after recreation`() {
        val restorer = StateRestorationTester(compose)
        val opened = mutableListOf<String>()
        restorer.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    PlaylistsContent(fixtures.playlists.take(2), PlaylistPagesState(), playlistActions(opened), onBack = {})
                }
            }
        }
        compose.onNodeWithText("Morning mix").performTouchInput { longClick() }
        compose.onNodeWithText("Rename").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `Add shows rows tick as one control each`() {
        show { FolderShowsSelectionSheet(fixtures.folders.first { it.id == "solo" }, fixtures.podcasts.take(6), onSave = {}, onDismissRequest = {}) }
        val row = compose.onNode(hasText("Show number 2") and isToggleable and hasRole(Role.Checkbox))
        row.assertIsDisplayed()
        row.performClick()
        compose.onNode(hasText("Show number 2") and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).assertExists()
    }

    @Test
    fun `every show offers the same actions, with Unsubscribe last`() {
        assertEquals(
            listOf(ShowContextAction.TogglePin, ShowContextAction.AddOrMoveToFolder, ShowContextAction.Reorder, ShowContextAction.Unsubscribe),
            showContextActions(inFolder = false),
        )
        assertEquals(
            listOf(ShowContextAction.TogglePin, ShowContextAction.AddOrMoveToFolder, ShowContextAction.RemoveFromFolder, ShowContextAction.Reorder, ShowContextAction.Unsubscribe),
            showContextActions(inFolder = true),
        )
    }

    @Test
    fun `a show outside folders can be pinned to Home and unsubscribed from its menu`() {
        val pinned = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()
        val show = fixtures.podcasts[6]
        show {
            SubscriptionContextMenuSheet(
                target = ContextMenuTarget.RootPodcast(show),
                actions = SubscriptionContextMenuActions(onTogglePin = { pinned += it.id }, onUnsubscribePodcast = { unsubscribed += it.id }),
                onDismissRequest = {},
            )
        }
        compose.onNodeWithText("Pin to Home screen").performClick()
        compose.onNodeWithText("Unsubscribe").performClick()
        assertEquals(listOf(show.id), pinned)
        assertEquals(listOf(show.id), unsubscribed)
    }

    @Test
    fun `a show's downloads has full-size Play all and Delete all buttons`() {
        show { cx.aswin.boxlore.feature.library.DownloadedShowEpisodesScreen(fixtures.viewModel(fixtures.loaded()), "show-1", "Downloaded show 2", {}, { _, _ -> }) }
        for (tag in listOf(cx.aswin.boxlore.feature.library.downloads.SHOW_PLAY_ALL_TAG, cx.aswin.boxlore.feature.library.downloads.SHOW_DELETE_ALL_TAG)) {
            compose.onNode(hasTestTag(tag) and hasRole(Role.Button)).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `in select mode a downloaded episode ticks as a whole`() {
        show { cx.aswin.boxlore.feature.library.DownloadedShowEpisodesScreen(fixtures.viewModel(fixtures.loaded()), "show-1", "Downloaded show 2", {}, { _, _ -> }) }
        compose.onNodeWithContentDescription("Select episodes").performClick()
        val title = fixtures.downloads().first { it.podcastId == "show-1" }.episodeTitle
        val row = compose.onNode(hasText(title) and isToggleable and hasRole(Role.Checkbox))
        row.performClick()
        compose.onNode(hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).assertExists()
        compose.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun `most from each show is one choice control`() {
        val changes = mutableListOf<Pair<PlaylistMix, Int?>>()
        show { PlaylistPlaybackSettings(PlaylistMix.TAKE_TURNS, 2, onChange = { mix, limit -> changes += mix to limit }) }
        compose.onNode(hasText("3") and hasRole(Role.RadioButton)).performClick()
        assertEquals(listOf(PlaylistMix.TAKE_TURNS to 3), changes)
    }

    @Test
    fun `folder looks sit two by two in a narrow space, the same height`() {
        show {
            Box(Modifier.width(340.dp)) { FolderLookRow(selected = null, onSelect = {}) }
        }
        val tiles = compose.onAllNodesWithTag(FOLDER_LOOK_TILE_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(4, tiles.size)
        assertEquals(2, tiles.map { it.top }.distinct().size)
        assertEquals(tiles[0].height, tiles[1].height)
    }

    @Test
    fun `Auto-organize's status row turns it off as one switch`() {
        var turnedOff = 0
        show {
            AutoOrganizePage(
                enabled = true,
                podcasts = fixtures.podcasts,
                folders = fixtures.folders,
                look = AutoOrganizeLook(),
                actions = AutoOrganizePageActions({}, {}, {}, { turnedOff++ }, {}, {}),
                bottomPadding = 0.dp,
            )
        }
        compose.onNode(isToggleable and hasRole(Role.Switch)).performClick()
        assertEquals(1, turnedOff)
    }

    @Test
    fun `Subscriptions' icon buttons name themselves on long-press`() {
        show { SubscriptionsScreen(fixtures.viewModel(fixtures.loaded(), folders = emptyList()), onBack = {}, onPodcastClick = {}, onExploreClick = {}) }
        compose.onNodeWithContentDescription("Sort").performTouchInput { longClick() }
        // The button has only a content description; a "Sort" text node is the tooltip.
        assertTrue(compose.onAllNodesWithText("Sort").fetchSemanticsNodes().isNotEmpty())
    }

    private fun playlistActions(opened: MutableList<String>) = object : PlaylistsActions {
        override fun open(playlistId: String) {
            opened += playlistId
        }
        override fun create(request: PlaylistCreateRequest) = Unit
        override fun rename(playlistId: String, name: String) = Unit
        override fun delete(playlistId: String) = Unit
    }
}
