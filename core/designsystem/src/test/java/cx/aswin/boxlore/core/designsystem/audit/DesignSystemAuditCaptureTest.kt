package cx.aswin.boxlore.core.designsystem.audit

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.component.BoxloreTopBar
import cx.aswin.boxlore.core.designsystem.components.ActionPillFilterChip
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheet
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistSheetState
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreMenuState
import cx.aswin.boxlore.core.designsystem.components.BoxloreSearchField
import cx.aswin.boxlore.core.designsystem.components.BoxloreSnackbarHost
import cx.aswin.boxlore.core.designsystem.components.BoxloreTextField
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.components.ContentLanguagesSheet
import cx.aswin.boxlore.core.designsystem.components.ContentRegionPickerSheet
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog
import cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreNavigationRailVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of the shared sheets, dialogs, menus, chips, selectors, fields and top bars as
 * they look today, at every audit size (run with `-PtabletCaptures=audit`; skipped otherwise). Each
 * screen sits on a plain page so the component's own fit shows. Sheets, dialogs and menus have their
 * own windows, so those captures composite every window.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    private val episode = TestFixtures.episode(
        title = "The surprising places that changed how we understand our world",
        description = "A conversation about curiosity and the places it takes us.",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
    ).copy(duration = 3_600)

    /**
     * Without artwork the card itself isn't drawn (rendering it natively takes far too long here);
     * its space, the start-time switch and every action keep their real size and place.
     */
    @Test fun shareSheet() = render("share-sheet", allWindows = true, after = { turnOnStartTime() }) {
        ShareBottomSheet(
            id = episode.id,
            type = "episode",
            title = episode.title,
            subtitle = "Everything Everywhere Daily: History, Science, Geography and More",
            imageUrl = null,
            onDismissRequest = {},
            durationMs = 3_600_000L,
            currentPositionMs = 754_000L,
            showTimestampOption = true,
            episode = episode.copy(imageUrl = null, podcastImageUrl = null),
            onShare = { _, _, _, _ -> },
        )
    }

    @Test fun languagesSheet() = render("languages-sheet", allWindows = true) {
        ContentLanguagesSheet(country = "IN", selectedLanguages = listOf("en", "hi", "ta"), englishOff = false, onLanguagesChange = {}, onEnglishOffChange = {}, onDismiss = {})
    }

    @Test fun regionSheet() = render("region-sheet", allWindows = true) {
        ContentRegionPickerSheet(activeRegion = "DE", onSwitchRegion = {}, onDismiss = {})
    }

    @Test fun addToPlaylistSheet() = render("add-to-playlist-sheet", allWindows = true) {
        AddToPlaylistSheet(
            showTitle = "Everything Everywhere Daily",
            playlists = PLAYLISTS,
            memberIds = setOf("p2"),
            state = remember { AddToPlaylistSheetState(newName = "Road trip mornings") },
            onToggle = {},
            onCreate = {},
            onDismissRequest = {},
        )
    }

    /** A destructive confirmation with German copy at every size, the longest the app ships. */
    @Test fun alertDialogLong() = render("alert-dialog-long", allWindows = true) {
        German {
            RemoveDownloadConfirmationDialog(
                episodeTitle = "Die überraschenden Orte, die unser Verständnis der Welt verändert haben – Teil zwei",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }

    @Test fun menuGalleryTopBar() = render("menu-gallery-top-bar", allWindows = true) {
        Column(Modifier.fillMaxSize()) {
            PageTopBar(collapsed = false) { MoreMenu() }
        }
    }

    @Test fun menuGalleryRowEnd() = render("menu-gallery-row-end", allWindows = true) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 160.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text("Everything Everywhere Daily", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MoreMenu()
            }
        }
    }

    /** The shared menu from a top bar's end: a labelled choice group, actions and a red group, eleven rows in all. */
    @Test fun menuGallery() = render("menu-gallery", allWindows = true) {
        val labels = listOf(
            R.string.player_control_share,
            R.string.player_control_mark_played,
            R.string.player_control_add_to_queue,
            R.string.player_control_download,
            R.string.player_control_like,
            R.string.player_control_chapters,
            R.string.player_control_transcript,
        ).map { stringResource(it) }
        val sort = listOf(R.string.navigation_style_floating, R.string.navigation_style_classic).map { stringResource(it) }
        val delete = stringResource(R.string.playlist_delete)
        val heading = stringResource(R.string.navigation_library)
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 4.dp)) {
                MoreMenuButton()
                BoxloreDropdownMenu(remember { BoxloreMenuState(true) }) {
                    group {
                        groupLabel(heading)
                        sort.forEachIndexed { index, label -> selectableItem(label, selected = index == 0, onClick = {}) }
                    }
                    group { labels.forEach { item(it, onClick = {}) } }
                    item(delete, onClick = {}, destructive = true)
                }
            }
        }
    }

    /** The shared text field and search box, empty and filled. */
    @Test fun fieldsShared() = render("fields-shared") {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxloreTextField(
                value = "Road trip mornings with long names",
                onValueChange = {},
                label = stringResource(R.string.playlist_name_label),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            BoxloreTextField(value = "", onValueChange = {}, label = stringResource(R.string.playlist_name_label), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            BoxloreSearchField(query = "", onQueryChange = {}, onSearch = {}, placeholder = stringResource(R.string.add_to_playlist_title), modifier = Modifier.padding(horizontal = 16.dp))
            BoxloreSearchField(query = "History", onQueryChange = {}, onSearch = {}, placeholder = stringResource(R.string.add_to_playlist_title), modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun chipsRow() = render("chips-row") {
        val genres = listOf(
            R.string.genre_icon_music to Icons.Rounded.MusicNote,
            R.string.genre_icon_science to Icons.Rounded.Science,
            R.string.genre_icon_news to Icons.Rounded.Newspaper,
            R.string.genre_icon_comedy to Icons.Rounded.TheaterComedy,
            R.string.genre_icon_health to Icons.Rounded.Favorite,
            R.string.genre_icon_finance to Icons.Rounded.LibraryMusic,
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // A scrolling genre row and a wrapping one, as pages place PillFilterChip today.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEachIndexed { index, (label, icon) -> PillFilterChip(stringResource(label), index == 0, {}, icon = icon) }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEachIndexed { index, (label, icon) -> PillFilterChip(stringResource(label), index == 1, {}, icon = icon) }
                ActionPillFilterChip(stringResource(R.string.genre_icon_star), {}, icon = Icons.Rounded.Check)
            }
            // Stock Material chips, as feature pages draw them before a shared chip exists.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEachIndexed { index, (label, icon) ->
                    FilterChip(
                        selected = index == 0,
                        onClick = {},
                        label = { Text(stringResource(label)) },
                        leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.take(3).forEach { (label, icon) ->
                    AssistChip(onClick = {}, label = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
                }
            }
        }
    }

    @Test fun selectors() = render("selectors") {
        val tabs = listOf(
            DiscoveryTab(stringResource(R.string.navigation_home), Icons.Rounded.Home),
            DiscoveryTab(stringResource(R.string.navigation_explore), Icons.Rounded.Explore, badgeCount = 3),
            DiscoveryTab(stringResource(R.string.navigation_library), Icons.Rounded.LibraryMusic),
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ConnectedOptionSelector(
                options = listOf(1 to stringResource(R.string.navigation_home), 2 to stringResource(R.string.navigation_explore), 3 to stringResource(R.string.navigation_library)),
                selected = 2,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
            ConnectedOptionSelector(
                options = listOf(1 to stringResource(R.string.navigation_style_floating), 2 to stringResource(R.string.navigation_style_classic)),
                selected = 1,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
            DiscoveryTabSwitcher(tabs, selectedIndex = 1, onTabSelected = {}, floating = true)
            DiscoveryTabSwitcher(tabs, selectedIndex = 0, onTabSelected = {}, floating = false)
        }
    }

    /** Text fields as the shared playlist dialogs and sheets draw them: filled, empty and a search. */
    @Test fun fields() = render("fields") {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = "Road trip mornings with long names",
                onValueChange = {},
                label = { Text(stringResource(R.string.playlist_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text(stringResource(R.string.playlist_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text(stringResource(R.string.add_to_playlist_title)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test fun topBarExpanded() = render("top-bar-expanded") { PageTopBar(collapsed = false) {} }

    /** Collapsed the way a listener collapses it: by scrolling the page's list up. */
    @Test fun topBarCollapsed() = render("top-bar-collapsed", after = { golden.compose.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() } }) {
        PageTopBar(collapsed = true) {}
    }

    /** An Undo message over a page, with stand-ins where the shell draws the bar and the mini player. */
    @Test fun snackbar() = render("snackbar") { SnackbarPage(miniPlayer = true) }

    @Test fun snackbarNoMiniPlayer() = render("snackbar-no-mini-player") { SnackbarPage(miniPlayer = false) }

    /** The shared page header with Back and a long title, over a list that scrolls it shut. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBar() = render("top-bar") {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            BoxloreTopBar(stringResource(R.string.repair_progress_title), onBack = {}, actions = { MoreMenuButton() }, scrollBehavior = scrollBehavior)
            LazyColumn(Modifier.fillMaxSize()) {
                items(40) { index -> Text("${PLAYLISTS[index % PLAYLISTS.size].name} $index", Modifier.padding(16.dp)) }
            }
        }
    }

    private fun render(screen: String, allWindows: Boolean = false, after: () -> Unit = {}, content: @Composable () -> Unit) {
        golden.assumeAuditCapture()
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize().testTag(PAGE_TAG)) { content() }
            }
        }
        golden.compose.mainClock.advanceTimeBy(400)
        after()
        golden.compose.onNodeWithTag(PAGE_TAG).assertExists()
        if (allWindows) {
            val roots = golden.compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
            assertTrue("$screen opens its own window ($roots roots)", roots > 1)
        }
        golden.capture(screen, allWindows)
    }

    /** Turns the start-time switch on, as a listener sharing from the player would. */
    private fun turnOnStartTime() {
        // A semantics click: on short windows the switch may sit below the sheet's edge today.
        golden.compose.onAllNodes(isToggleable()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        golden.compose.mainClock.advanceTimeBy(400)
    }

    @Composable
    private fun MoreMenu() {
        var expanded by remember { mutableStateOf(true) }
        Box {
            IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MenuItem(R.string.player_control_share, Icons.Rounded.Share)
                MenuItem(R.string.player_control_mark_played, Icons.Rounded.Check)
                MenuItem(R.string.player_control_add_to_queue, Icons.Rounded.QueueMusic)
                MenuItem(R.string.player_control_download, Icons.Rounded.Download)
                MenuItem(R.string.player_control_like, Icons.Rounded.Favorite)
            }
        }
    }

    @Composable
    private fun MenuItem(label: Int, icon: ImageVector) {
        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {}, leadingIcon = { Icon(icon, contentDescription = null) })
    }

    /** A page's large top bar as Library and its pages draw it today, over a list that scrolls it shut. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PageTopBar(collapsed: Boolean, actions: @Composable () -> Unit) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.navigation_library)) },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                actions = {
                    actions()
                    TopBarUtilityActions(onFeedbackClick = {}, onSettingsClick = {}, modifier = Modifier.padding(end = 12.dp))
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            if (collapsed) {
                LazyColumn(Modifier.fillMaxSize().testTag(LIST_TAG)) {
                    items(40) { index -> Text("${PLAYLISTS[index % PLAYLISTS.size].name} $index", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    @Composable
    private fun SnackbarPage(miniPlayer: Boolean) {
        val host = remember { SnackbarHostState() }
        val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
        val message = stringResource(R.string.add_to_playlist_removed, PLAYLISTS[1].name)
        CompositionLocalProvider(LocalBoxloreMiniPlayerVisible provides miniPlayer, LocalBoxloreNavigationRailVisible provides tablet) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val standIn = MaterialTheme.colorScheme.surfaceContainerHighest
                    if (miniPlayer) Box(Modifier.fillMaxWidth().height(64.dp).background(standIn, RoundedCornerShape(32.dp)))
                    if (!tablet) Box(Modifier.fillMaxWidth().height(56.dp).background(standIn, RoundedCornerShape(28.dp)))
                }
                BoxloreSnackbarHost(host, Modifier.align(Alignment.BottomCenter))
            }
        }
        LaunchedEffect(message) { host.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
    }

    @Composable
    private fun MoreMenuButton() {
        IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
    }

    /** German copy whatever the capture's locale, for the longest real text. */
    @Composable
    private fun German(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val current = LocalConfiguration.current
        val configuration = remember(current) { Configuration(current).apply { setLocale(Locale.GERMAN) } }
        val german = remember(configuration) { context.createConfigurationContext(configuration) }
        CompositionLocalProvider(LocalContext provides german, LocalConfiguration provides configuration) { content() }
    }

    companion object {
        private const val PAGE_TAG = "audit-page"
        private const val LIST_TAG = "audit-list"
        private val PLAYLISTS = listOf(
            Playlist("p1", "Comedy for the commute", 0L, 0L, showCount = 3),
            Playlist("p2", "History deep dives and long conversations", 0L, 0L, showCount = 12),
            Playlist("p3", "Science", 0L, 0L, showCount = 1),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix()
    }
}
