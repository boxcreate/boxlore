package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.library.LibraryCaptureFixtures.ART
import cx.aswin.boxlore.feature.library.playlists.NewPlaylistDraft
import cx.aswin.boxlore.feature.library.playlists.PlaylistCreateRequest
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailActions
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailContent
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailState
import cx.aswin.boxlore.feature.library.playlists.PlaylistPagesState
import cx.aswin.boxlore.feature.library.playlists.PlaylistPickSource
import cx.aswin.boxlore.feature.library.playlists.PlaylistsContent
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeLook
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.FolderEditorPage
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderMembershipChange
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesActions
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesData
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesState
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowMove
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowsSelectionSheet
import cx.aswin.boxlore.feature.library.subscriptions.MoveToFolderSheet
import cx.aswin.boxlore.feature.library.subscriptions.NewFolderDraft
import cx.aswin.boxlore.feature.library.subscriptions.OpenFolderPage
import cx.aswin.boxlore.feature.library.subscriptions.ReorderMode
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionContextMenuSheet
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortActions
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortConfig
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionSortSheet
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local review renders of every Library page, sheet and menu. `-PtabletCaptures=audit` draws each
 * scene at the audit sizes (plus 360x800, `-PauditSizes` narrows them); `-PtabletCaptures=after`
 * keeps the older Liked and Downloads tablet set. Without the property every case skips, so CI and
 * the phone goldens never run it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTabletAuditCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    private val fixtures = LibraryCaptureFixtures

    @Test fun capture() {
        val mode = System.getProperty("boxlore.tabletCaptures")
        assumeTrue(mode == "after" || mode == "audit")
        fixture.prepare()
        val popup = scene in POPUP_SCENES
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                Surface { Scene() }
            }
        }
        settle()
        interact()
        // Every scene draws something: a broken fixture fails here instead of saving a blank picture.
        check(fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()) { "library-$scene drew nothing" }
        if (popup) {
            val roots = fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
            check(roots > 1) { "$scene opens its own window ($roots roots)" }
        }
        fixture.capture("library-$scene", allWindows = popup)
    }

    @Composable
    @Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per scene.
    private fun Scene() {
        when (scene) {
            "hub", "hub-empty", "hub-loading" -> LibraryContent(
                uiState = when (scene) {
                    "hub-loading" -> LibraryUiState.Loading
                    "hub-empty" -> fixtures.loaded(empty = true)
                    else -> fixtures.loaded()
                },
                onNavigateToLiked = {},
                onNavigateToSubscriptions = {},
                onNavigateToDownloads = {},
                onNavigateToHistory = {},
                playlists = LibraryPlaylistsCard(coverUrls = if (scene == "hub") List(3) { ART } else emptyList(), onClick = {}),
            )
            "liked", "liked-empty", "liked-loading" -> LikedEpisodesScreen(
                viewModel = fixtures.viewModel(
                    when (scene) {
                        "liked-loading" -> LibraryUiState.Loading
                        "liked-empty" -> LibraryUiState.Success()
                        else -> fixtures.loaded()
                    },
                ),
                onBack = {},
                onExploreClick = {},
                onEpisodeClick = { _, _ -> },
            )
            "downloads", "downloads-selecting", "downloads-empty", "downloads-loading" -> DownloadedEpisodesScreen(
                viewModel = fixtures.viewModel(
                    when (scene) {
                        "downloads-loading" -> LibraryUiState.Loading
                        "downloads-empty" -> LibraryUiState.Success()
                        else -> fixtures.loaded()
                    },
                ),
                userPrefs = fixtures.smartDownloadPrefs(),
                onBack = {},
                onExploreClick = {},
                onPodcastShowClick = { _, _ -> },
                isPlayerActive = true,
            )
            "downloaded-show" -> DownloadedShowEpisodesScreen(fixtures.viewModel(fixtures.loaded()), "show-0", LibraryCaptureFixtures.LONG_TITLE, {}, { _, _ -> }, true)
            "subs-shows", "subs-shows-list", "subs-shows-empty", "subs-shows-loading", "subs-folders" -> SubscriptionsScreen(
                viewModel = fixtures.viewModel(
                    state = when (scene) {
                        "subs-shows-loading" -> LibraryUiState.Loading
                        "subs-shows-empty" -> LibraryUiState.Success()
                        else -> fixtures.loaded()
                    },
                    gridView = scene != "subs-shows-list",
                    folders = if (scene == "subs-folders") fixtures.folders else emptyList(),
                ),
                onBack = {},
                onPodcastClick = {},
                onExploreClick = {},
            )
            "new-episodes", "new-episodes-empty", "latest-sort" -> SubscriptionsScreen(
                viewModel = fixtures.viewModel(
                    state = if (scene == "new-episodes-empty") LibraryUiState.Success(subscribedPodcasts = fixtures.podcasts.take(3).map { it.copy(latestEpisode = null) }) else fixtures.loaded(),
                    tabStyle = SubscriptionsTabStyle.FLOATING,
                    folders = emptyList(),
                ),
                onBack = {},
                onPodcastClick = {},
                onExploreClick = {},
                initialTab = 1,
            )
            "playlists", "playlists-empty", "playlists-loading", "new-playlist-pick", "new-playlist-settings" -> {
                val state = remember {
                    PlaylistPagesState(
                        newDraft = when (scene) {
                            "new-playlist-pick" -> NewPlaylistDraft(step = NewPlaylistDraft.PICK, pickedIds = listOf("show-1", "show-2"))
                            "new-playlist-settings" -> NewPlaylistDraft(step = NewPlaylistDraft.FINISH, pickedIds = listOf("show-1", "show-2", "show-3"), name = "Morning mix", nameEdited = true)
                            else -> null
                        },
                    )
                }
                PlaylistsContent(
                    playlists = when (scene) {
                        "playlists-loading" -> null
                        "playlists-empty" -> emptyList()
                        else -> fixtures.playlists
                    },
                    state = state,
                    actions = playlistActions,
                    onBack = {},
                    pickSource = PlaylistPickSource(fixtures.podcasts, fixtures.folders),
                )
            }
            "playlist-detail" -> PlaylistDetailContent(
                detail = PlaylistDetailState.Ready(fixtures.playlists.first(), fixtures.playlistShows, fixtures.comingUp),
                state = remember { PlaylistPagesState() },
                actions = playlistActions,
                onBack = {},
                pickSource = PlaylistPickSource(fixtures.podcasts, fixtures.folders),
            )
            "folder-page" -> OpenFolderPage(FolderPagesState(openFolderId = "tech"), folderData, noFolderActions, bottomPadding = 96.dp)
            "new-folder-pick" -> FolderEditorPage(
                remember { FolderPagesState().apply { openNewFolder() }.also { it.newFolderDraft = NewFolderDraft(pickedIds = listOf("show-1", "show-2")) } },
                folderData,
                noFolderActions,
            )
            "edit-folder" -> FolderEditorPage(remember { FolderPagesState().apply { openEdit(fixtures.folders[2]) } }, folderData, noFolderActions)
            "auto-organize" -> FolderEditorPage(remember { FolderPagesState().apply { openAutoOrganize() } }, folderData, noFolderActions)
            "sort-sheet" -> Backdrop {
                SubscriptionSortSheet(
                    config = SubscriptionSortConfig(SubscriptionSort.SmartRank, FolderInterSort.Inherit, FolderIntraSort.Inherit, autoOrganizeFolders = true, genreFolderCount = 2),
                    actions = SubscriptionSortActions({}, {}, {}, {}, {}),
                )
            }
            "context-menu-show", "context-menu-folder-show", "context-menu-folder" -> Backdrop {
                SubscriptionContextMenuSheet(
                    target = when (scene) {
                        "context-menu-show" -> ContextMenuTarget.RootPodcast(fixtures.podcasts[6])
                        "context-menu-folder-show" -> ContextMenuTarget.FolderPodcast(fixtures.folders[2], fixtures.podcasts[3])
                        else -> ContextMenuTarget.Folder(fixtures.folders[2])
                    },
                    actions = SubscriptionContextMenuActions(),
                    onDismissRequest = {},
                )
            }
            "add-shows-sheet" -> Backdrop {
                FolderShowsSelectionSheet(folder = fixtures.folders[2], allSubscribedPodcasts = fixtures.podcasts, onSave = {}, onDismissRequest = {})
            }
            "move-sheet" -> Backdrop {
                MoveToFolderSheet(podcast = fixtures.podcasts[3], availableFolders = fixtures.folders, onSelectFolder = {}, onCreateNewFolder = {}, onDismissRequest = {}, currentFolderId = "news")
            }
            else -> error("Unknown scene $scene")
        }
    }

    /** The page a sheet opens over. */
    @Composable
    private fun Backdrop(sheet: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        sheet()
    }

    /** Opens what the scene shows beyond its page: select mode, or the New Episodes sort. */
    private fun interact() {
        when (scene) {
            "downloads-selecting" -> {
                tap(byDescription(R.string.library_downloads_select_podcasts))
                tap(byDescription(R.string.library_select_all))
            }
            "latest-sort" -> tap(byDescription(R.string.library_sort))
        }
    }

    private fun byDescription(id: Int): SemanticsNodeInteraction =
        fixture.compose.onAllNodesWithContentDescription(fixture.compose.activity.getString(id)).onFirst()

    /** Clicks through semantics (popups open on the paused clock) and lets the result draw. */
    private fun tap(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun settle() {
        repeat(6) {
            Snapshot.sendApplyNotifications()
            fixture.compose.mainClock.advanceTimeBy(100)
            fixture.compose.waitForIdle()
        }
    }

    private val folderData = FolderPagesData(
        podcasts = fixtures.podcasts,
        folders = fixtures.folders,
        sort = SubscriptionSort.SmartRank,
        smartOrderIds = fixtures.podcasts.map { it.id },
        intraFolderSort = FolderIntraSort.Inherit,
        autoOrganizeOn = true,
        autoOrganizeLook = AutoOrganizeLook(),
        reorderMode = ReorderMode.Inactive,
    )

    private val noFolderActions = object : FolderPagesActions {
        override fun openShow(podcastId: String) = Unit
        override fun showMenu(target: ContextMenuTarget) = Unit
        override fun addShows(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun openSort() = Unit
        override fun setReorderMode(mode: ReorderMode) = Unit
        override fun saveFolderOrder(folderId: String, orderedIds: List<String>) = Unit
        override fun deleteFolder(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?) = Unit
        override fun updateFolder(folder: cx.aswin.boxlore.core.model.SubscriptionFolder) = Unit
        override fun changeMembership(podcastId: String, change: FolderMembershipChange) = Unit
        override fun turnOnAutoOrganize(look: AutoOrganizeLook) = Unit
        override fun turnOffAutoOrganize() = Unit
        override fun setAutoOrganizeLook(look: AutoOrganizeLook) = Unit
        override fun applyLookToGenreFolders(look: AutoOrganizeLook) = Unit
    }

    private val playlistActions = object : PlaylistDetailActions {
        override fun open(playlistId: String) = Unit
        override fun create(request: PlaylistCreateRequest) = Unit
        override fun rename(playlistId: String, name: String) = Unit
        override fun delete(playlistId: String) = Unit
        override fun play() = Unit
        override fun setPlayback(mix: PlaylistMix, perShowLimit: Int?) = Unit
        override fun addShows(podcastIds: List<String>) = Unit
        override fun removeShow(show: PlaylistShow) = Unit
    }

    companion object {
        /** Scenes whose subject is a sheet, menu or dialog in its own window. */
        private val POPUP_SCENES = setOf(
            "latest-sort", "sort-sheet", "context-menu-show", "context-menu-folder-show", "context-menu-folder",
            "add-shows-sheet", "move-sheet", "new-folder-pick", "edit-folder", "auto-organize",
            "new-playlist-pick", "new-playlist-settings",
        )

        private val AUDIT_SCENES = listOf(
            "hub", "hub-empty", "hub-loading",
            "liked", "liked-empty", "liked-loading",
            "downloads", "downloads-selecting", "downloads-empty", "downloads-loading", "downloaded-show",
            "new-episodes", "new-episodes-empty", "latest-sort",
            "playlists", "playlists-empty", "playlists-loading", "playlist-detail", "new-playlist-pick", "new-playlist-settings",
            "subs-shows", "subs-shows-list", "subs-shows-empty", "subs-shows-loading", "subs-folders",
            "folder-page", "new-folder-pick", "edit-folder", "auto-organize",
            "sort-sheet", "context-menu-show", "context-menu-folder-show", "context-menu-folder", "add-shows-sheet", "move-sheet",
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> {
            if (System.getProperty("boxlore.tabletCaptures") == "audit") {
                val sizes = PhoneGoldenConfiguration.auditConfigurations(extra = listOf(PhoneGoldenConfiguration(360, 800, false)))
                return AUDIT_SCENES.flatMap { scene -> sizes.map { arrayOf<Any>(scene, it) } }
            }
            return listOf("liked", "liked-empty", "downloads", "downloaded-show").flatMap { scene ->
                listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) } }
            }
        }
    }
}
