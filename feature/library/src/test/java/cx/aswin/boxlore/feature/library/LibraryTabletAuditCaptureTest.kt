package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.library.LibraryCaptureFixtures.ART
import cx.aswin.boxlore.feature.library.playlists.NewPlaylistDraft
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailContent
import cx.aswin.boxlore.feature.library.playlists.PlaylistDetailState
import cx.aswin.boxlore.feature.library.playlists.PlaylistPagesState
import cx.aswin.boxlore.feature.library.playlists.PlaylistPickSource
import cx.aswin.boxlore.feature.library.playlists.PlaylistsContent
import cx.aswin.boxlore.feature.library.subscriptions.AutoOrganizeLook
import cx.aswin.boxlore.feature.library.subscriptions.ContextMenuTarget
import cx.aswin.boxlore.feature.library.subscriptions.FolderEditorPage
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderPagesState
import cx.aswin.boxlore.feature.library.subscriptions.FolderShowsSelectionSheet
import cx.aswin.boxlore.feature.library.subscriptions.MoveToFolderSheet
import cx.aswin.boxlore.feature.library.subscriptions.NewFolderDraft
import cx.aswin.boxlore.feature.library.subscriptions.OpenFolderPage
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
import org.robolectric.shadows.ShadowDialog

/**
 * Local review renders of every Library page, sheet and menu. `-PtabletCaptures=audit` draws each
 * scene at the audit sizes (plus 360x800, `-PauditSizes` narrows them); `-PtabletCaptures=after`
 * keeps the older Liked and Downloads tablet set. Without the property every case skips, so CI and
 * the phone goldens never run it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryTabletAuditCaptureTest(private val sceneName: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    private val fixtures = LibraryCaptureFixtures

    /** A `-brand` scene draws in a non-Classic brand theme, `-brand-pure` in its Pure black dark form. */
    private val theme = when {
        sceneName.endsWith(PURE_SUFFIX) -> ThemeChoice(CAPTURE_BRAND, SurfaceStyles.AMOLED, dark = true)
        sceneName.endsWith(BRAND_SUFFIX) -> ThemeChoice(CAPTURE_BRAND, SurfaceStyles.STANDARD, dark = configuration.dark)
        else -> ThemeChoice("violet", SurfaceStyles.CLASSIC_DYNAMIC, dark = configuration.dark)
    }
    private val scene = sceneName.removeSuffix(PURE_SUFFIX).removeSuffix(BRAND_SUFFIX)

    @Test fun capture() {
        val mode = System.getProperty("boxlore.tabletCaptures")
        assumeTrue(mode == "after" || mode == "audit")
        fixture.prepare()
        val popup = scene in POPUP_SCENES
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = theme.dark, dynamicColor = false, themeBrand = theme.brand, surfaceStyle = theme.surface) {
                Surface { Scene() }
            }
        }
        settle()
        interact()
        sendInsetsToDialogs()
        // Every scene draws something: a broken fixture fails here instead of saving a blank picture.
        check(fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()) { "library-$scene drew nothing" }
        if (popup) {
            val roots = fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
            check(roots > 1) { "$scene opens its own window ($roots roots)" }
        }
        fixture.capture("library-$sceneName", allWindows = popup)
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
                playlists = LibraryPlaylistsCard(coverUrls = if (scene == "hub") List(3) { ART } else emptyList(), onClick = {}, count = if (scene == "hub") fixtures.playlists.size else 0),
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
                onPlayEpisodes = { _, _ -> },
            )
            "downloads", "downloads-selecting", "downloads-empty", "downloads-loading", "downloads-syncing", "downloads-smart-off" -> DownloadedEpisodesScreen(
                viewModel = fixtures.viewModel(
                    when (scene) {
                        "downloads-loading" -> LibraryUiState.Loading
                        "downloads-empty" -> LibraryUiState.Success()
                        else -> fixtures.loaded()
                    },
                ),
                userPrefs = fixtures.smartDownloadPrefs(enabled = scene != "downloads-smart-off"),
                onBack = {},
                onExploreClick = {},
                onPodcastShowClick = { _, _ -> },
                isPlayerActive = true,
                isSyncing = scene == "downloads-syncing",
            )
            "downloaded-show", "downloaded-show-menu" -> DownloadedShowEpisodesScreen(fixtures.viewModel(fixtures.loaded()), "show-0", LibraryCaptureFixtures.LONG_TITLE, {}, { _, _ -> }, true)
            "subs-shows", "subs-shows-list", "subs-shows-empty", "subs-shows-loading", "subs-folders", "subs-search", "subs-sort-tooltip" -> SubscriptionsScreen(
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
            "playlists", "playlists-empty", "playlists-loading", "playlists-menu", "new-playlist-pick", "new-playlist-settings" -> {
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
            "playlist-detail", "playlist-detail-snackbar" -> {
                val snackbar = remember { SnackbarHostState() }
                if (scene == "playlist-detail-snackbar") {
                    LaunchedEffect(Unit) { snackbar.showSnackbar("Removed ${fixtures.playlistShows[1].title}", "Undo", duration = SnackbarDuration.Indefinite) }
                }
                PlaylistDetailContent(
                    detail = PlaylistDetailState.Ready(fixtures.playlists.first(), fixtures.playlistShows, fixtures.comingUp),
                    state = remember { PlaylistPagesState() },
                    actions = playlistActions,
                    onBack = {},
                    pickSource = PlaylistPickSource(fixtures.podcasts, fixtures.folders),
                    snackbarHostState = snackbar,
                    isPlayerActive = scene == "playlist-detail-snackbar",
                )
            }
            "folder-page" -> OpenFolderPage(FolderPagesState(openFolderId = "tech"), folderData, noFolderActions, bottomPadding = 96.dp)
            "new-folder-pick" -> FolderEditorPage(
                remember { FolderPagesState().apply { openNewFolder() }.also { it.newFolderDraft = NewFolderDraft(pickedIds = listOf("show-1", "show-2")) } },
                folderData,
                noFolderActions,
            )
            "edit-folder" -> FolderEditorPage(remember { FolderPagesState().apply { openEdit(fixtures.folders[2]) } }, folderData, noFolderActions)
            "auto-organize" -> FolderEditorPage(remember { FolderPagesState().apply { openAutoOrganize() } }, folderData, noFolderActions)
            "auto-organize-one-look" -> FolderEditorPage(
                remember { FolderPagesState().apply { openAutoOrganize() } },
                folderData.copy(autoOrganizeLook = AutoOrganizeLook(fixedSize = FolderDisplaySize.SHELF)),
                noFolderActions,
            )
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

    /** Opens what the scene shows beyond its page: select mode, a sort, a menu, the search or a tooltip. */
    private fun interact() {
        when (scene) {
            "downloads-selecting" -> {
                tap(byDescription(R.string.library_downloads_select_podcasts))
                tap(byDescription(R.string.library_select_all))
            }
            "latest-sort" -> tap(byDescription(R.string.library_sort))
            "downloaded-show-menu" -> tap(byDescription(R.string.library_options))
            "playlists-menu" -> {
                fixture.compose.onNodeWithText("Morning mix").performTouchInput { longClick() }
                settle()
            }
            "subs-search" -> {
                tap(byDescription(R.string.library_search))
                fixture.compose.onNode(hasSetTextAction()).performTextInput("number 1")
                settle()
            }
            "subs-sort-tooltip" -> {
                byDescription(R.string.library_sort).performTouchInput { longClick() }
                settle()
            }
        }
    }

    /**
     * Editors, sheets and menus open in their own windows, which the capture's fake system bars and
     * cutout don't reach; this sends the same insets to each of them.
     */
    private fun sendInsetsToDialogs() {
        val insets = fixture.configuration.systemInsets ?: return
        fixture.compose.runOnUiThread {
            ShadowDialog.getShownDialogs().forEach { dialog ->
                dialog.window?.decorView?.let { ViewCompat.dispatchApplyWindowInsets(it, insets.toWindowInsets()) }
            }
        }
        settle()
    }

    /** The theme a scene draws in. */
    private data class ThemeChoice(val brand: String, val surface: String, val dark: Boolean)

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

    private val folderData = fixtures.folderPagesData
    private val noFolderActions = fixtures.noFolderActions

    private val playlistActions = fixtures.noPlaylistActions

    companion object {
        /** Scenes whose subject is a sheet, menu or dialog in its own window. */
        private val POPUP_SCENES = setOf(
            "latest-sort", "sort-sheet", "context-menu-show", "context-menu-folder-show", "context-menu-folder",
            "add-shows-sheet", "move-sheet", "new-folder-pick", "edit-folder", "auto-organize", "auto-organize-one-look",
            "new-playlist-pick", "new-playlist-settings", "downloaded-show-menu", "playlists-menu", "subs-sort-tooltip",
        )

        private const val BRAND_SUFFIX = "-brand"
        private const val PURE_SUFFIX = "-brand-pure"
        private const val CAPTURE_BRAND = "emerald"

        /** Open menus, search, tooltips, Smart Downloads states and snackbars: the sizes where they change most. */
        private val STATE_SCENES = listOf(
            "downloaded-show-menu",
            "playlists-menu",
            "subs-search",
            "subs-sort-tooltip",
            "downloads-syncing",
            "downloads-smart-off",
            "auto-organize-one-look",
            "playlist-detail-snackbar",
        )
        private val STATE_SIZES = setOf("360x800", "412x915", "915x412-insets", "475x695-de", "932x650-book", "932x650-tabletop", "1280x800")

        /** The hub and Playlists' empty page in a brand theme and in Pure black. */
        private val THEME_SCENES = listOf("hub", "hub-empty", "playlists-empty").flatMap { listOf(it + BRAND_SUFFIX, it + PURE_SUFFIX) }
        private val THEME_SIZES = setOf("412x915", "1280x800")

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
                val sizes = PhoneGoldenConfiguration.auditConfigurations(
                    extra = listOf(
                        PhoneGoldenConfiguration(360, 800, false),
                        PhoneGoldenConfiguration(412, 915, true),
                        PhoneGoldenConfiguration(1_280, 800, true),
                    ),
                )
                val stateSizes = sizes.filter { it.auditKey in STATE_SIZES }
                val themeSizes = sizes.filter { it.auditKey in THEME_SIZES && !it.dark }
                return AUDIT_SCENES.flatMap { scene -> sizes.map { arrayOf<Any>(scene, it) } } +
                    STATE_SCENES.flatMap { scene -> stateSizes.map { arrayOf<Any>(scene, it) } } +
                    THEME_SCENES.flatMap { scene -> themeSizes.map { arrayOf<Any>(scene, it) } }
            }
            return listOf("liked", "liked-empty", "downloads", "downloaded-show").flatMap { scene ->
                listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) } }
            }
        }
    }
}
