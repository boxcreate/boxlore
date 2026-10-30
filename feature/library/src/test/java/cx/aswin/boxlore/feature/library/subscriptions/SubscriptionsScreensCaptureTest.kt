package cx.aswin.boxlore.feature.library.subscriptions

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.library.FolderSaveParams
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.library.LibraryViewModel
import cx.aswin.boxlore.feature.library.SubscriptionSort
import cx.aswin.boxlore.feature.library.SubscriptionsScreen
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Local-only renders of the Subscriptions screens at phone size, light and dark, for checking the
 * look by eye. Runs only with BOXLORE_SUBS_CAPTURES set; writes to build/reports/subscriptions-captures.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionsScreensCaptureTest(private val dark: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()

    private fun show(id: String, title: String, genre: String, isNew: Boolean = false): Podcast =
        TestFixtures.podcast(id = id, title = title, genre = genre, imageUrl = "https://art.example.invalid/$id.png", subscribedAt = now - 30L * 86_400_000L)
            .copy(latestEpisode = if (isNew) TestFixtures.episode(id = "ep-$id", podcastId = id, publishedDate = now / 1000L - 3_600L) else null)

    private val podcasts = listOf(
        show("conspiracy", "Conspiracy Theories", "True Crime"),
        show("desi", "The Desi Crime Podcast", "True Crime", isNew = true),
        show("killer", "Killer Stories", "True Crime"),
        show("cyber", "Cyber Hack", "True Crime"),
        show("serial", "Serial", "True Crime"),
        show("obscura", "Obscura", "True Crime"),
        show("unseen", "Unseen", "True Crime"),
        show("wanted", "The Most Wanted Olympian", "True Crime"),
        show("mysteries", "Unexplained Mysteries", "True Crime"),
        show("verge", "The Vergecast", "Tech", isNew = true),
        show("decoder", "Decoder", "Tech"),
        show("waveform", "Waveform", "Tech"),
        show("faithful", "Android Faithful", "Tech", isNew = true),
        show("version", "Version History", "Tech"),
        show("darknet", "Darknet Diaries", "Tech"),
        show("wan", "The WAN Show", "Tech"),
        show("sherlock", "Sherlock Holmes", "Fiction"),
        show("blood", "Blood Trails", "Fiction", isNew = true),
        show("history", "History Hit", "History"),
        show("science", "Science Vs", "Science"),
    )

    private fun ids(vararg id: String) = id.toList()

    private val trueCrime = SubscriptionFolder(
        id = "tc",
        name = "True Crime",
        icon = "true_crime",
        displaySize = FolderDisplaySize.SHELF,
        linkedGenre = "True Crime",
        podcastCount = 9,
        podcastIds = ids("conspiracy", "desi", "killer", "cyber", "serial", "obscura", "unseen", "wanted", "mysteries"),
    )
    private val tech = SubscriptionFolder(
        id = "tech",
        name = "Tech",
        icon = "tech",
        displaySize = FolderDisplaySize.SHELF,
        linkedGenre = "Tech",
        podcastCount = 7,
        podcastIds = ids("verge", "decoder", "waveform", "faithful", "version", "darknet", "wan"),
    )
    private val commute = SubscriptionFolder(
        id = "commute",
        name = "Commute",
        displaySize = FolderDisplaySize.COMPACT,
        showPodcastGrid = true,
        podcastCount = 3,
        podcastIds = ids("decoder", "serial", "history"),
    )
    private val fiction = SubscriptionFolder(
        id = "fiction",
        name = "Fiction",
        icon = "fiction",
        displaySize = FolderDisplaySize.COMPACT,
        linkedGenre = "Fiction",
        podcastCount = 2,
        podcastIds = ids("sherlock", "blood"),
    )
    private val folders = listOf(trueCrime, tech, commute, fiction)

    @Before
    fun setUp() {
        assumeTrue(System.getenv("BOXLORE_SUBS_CAPTURES") != null)
        RuntimeEnvironment.setQualifiers("en-rUS-w412dp-h915dp-port-${if (dark) "night" else "notnight"}-xxhdpi")
        val context = compose.activity
        val palette = listOf(0xFF7B5EA7, 0xFFE0A030, 0xFF2E6B5E, 0xFFB23A48, 0xFF2A4D8F, 0xFF8C6D3F, 0xFF3F8C7A, 0xFF5A5A66)
        val loader = ImageLoader.Builder(context).components {
            add(
                coil.intercept.Interceptor { chain ->
                    val color = palette[(chain.request.data.toString().hashCode() and 0x7fffffff) % palette.size].toInt()
                    val art = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                    SuccessResult(BitmapDrawable(context.resources, art), chain.request, DataSource.MEMORY_CACHE)
                },
            )
        }.build()
        Coil.setImageLoader(loader)
        compose.mainClock.autoAdvance = false
    }

    private fun render(scene: String, content: @Composable () -> Unit) {
        compose.setContent { BoxLoreTheme(darkTheme = dark, dynamicColor = false) { content() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val file = File("build/reports/subscriptions-captures/$scene-${if (dark) "dark" else "light"}.png")
        requireNotNull(file.parentFile).mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    }

    private val noActions = object : FolderPagesActions {
        override fun openShow(podcastId: String) = Unit
        override fun showMenu(target: ContextMenuTarget) = Unit
        override fun addShows(folder: SubscriptionFolder) = Unit
        override fun openSort() = Unit
        override fun setReorderMode(mode: ReorderMode) = Unit
        override fun saveFolderOrder(folderId: String, orderedIds: List<String>) = Unit
        override fun deleteFolder(folder: SubscriptionFolder) = Unit
        override fun createFolder(params: FolderSaveParams, podcastIds: List<String>, move: FolderShowMove?) = Unit
        override fun updateFolder(folder: SubscriptionFolder) = Unit
        override fun changeMembership(podcastId: String, change: FolderMembershipChange) = Unit
        override fun turnOnAutoOrganize(look: AutoOrganizeLook) = Unit
        override fun turnOffAutoOrganize() = Unit
        override fun setAutoOrganizeLook(look: AutoOrganizeLook) = Unit
        override fun applyLookToGenreFolders(look: AutoOrganizeLook) = Unit
    }

    private fun data(autoOn: Boolean = true) = FolderPagesData(
        podcasts = podcasts,
        folders = folders,
        sort = SubscriptionSort.SmartRank,
        smartOrderIds = podcasts.map { it.id },
        intraFolderSort = FolderIntraSort.Inherit,
        autoOrganizeOn = autoOn,
        autoOrganizeLook = AutoOrganizeLook(),
        reorderMode = ReorderMode.Inactive,
    )

    @Test
    fun shows() {
        val vm = mock(LibraryViewModel::class.java)
        `when`(vm.uiState).thenReturn(MutableStateFlow<LibraryUiState>(LibraryUiState.Success(subscribedPodcasts = podcasts, smartOrderIds = podcasts.map { it.id })))
        `when`(vm.lastSeenEpisodes).thenReturn(MutableStateFlow(emptyMap()))
        `when`(vm.subscriptionsGridView).thenReturn(MutableStateFlow(true))
        `when`(vm.isRefreshingNewEpisodes).thenReturn(MutableStateFlow(false))
        `when`(vm.pendingPlayedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(vm.useSmartRank).thenReturn(MutableStateFlow(true))
        `when`(vm.hideCompletedInSubs).thenReturn(MutableStateFlow(false))
        `when`(vm.pinnedPodcastIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(vm.autoOrganizeFolders).thenReturn(MutableStateFlow(true))
        `when`(vm.autoOrganizeLook).thenReturn(MutableStateFlow(AutoOrganizeLook()))
        `when`(vm.folderSort).thenReturn(MutableStateFlow(FolderInterSort.Inherit))
        `when`(vm.folderManualOrder).thenReturn(MutableStateFlow(emptyList()))
        `when`(vm.intraFolderSort).thenReturn(MutableStateFlow(FolderIntraSort.Inherit))
        `when`(vm.folders).thenReturn(MutableStateFlow(folders))
        `when`(vm.subscriptionsTabStyle).thenReturn(MutableStateFlow(SubscriptionsTabStyle.TOP))
        render("01-shows") {
            SubscriptionsScreen(viewModel = vm, onBack = {}, onPodcastClick = {}, onExploreClick = {})
        }
    }

    @Test
    fun folderGenre() = render("02-folder-genre") {
        OpenFolderPage(FolderPagesState(openFolderId = "tc"), data(), noActions, bottomPadding = 0.dp)
    }

    @Test
    fun folderManual() = render("03-folder-manual") {
        OpenFolderPage(FolderPagesState(openFolderId = "commute"), data(), noActions, bottomPadding = 0.dp)
    }

    private fun newFolder(scene: String, draft: NewFolderDraft, autoOn: Boolean = false) = render(scene) {
        NewFolderFlow(
            draft = draft,
            onDraftChange = {},
            data = NewFolderFlowData(podcasts = podcasts, folders = folders, autoOrganizeOn = autoOn),
            actions = NewFolderFlowActions(onClose = {}, onCreate = { _, _ -> }, onTurnOnAutoOrganize = { _, _ -> }, onManageAutoOrganize = {}),
        )
    }

    @Test
    fun newPick() = newFolder("04-new-pick", NewFolderDraft(pickedIds = ids("verge", "decoder")))

    @Test
    fun newGenre() = newFolder("05-new-genre", NewFolderDraft(mode = NewFolderMode.OneGenre, genre = "Tech", name = "Tech", icon = "tech"))

    @Test
    fun newAuto() = newFolder("06-new-auto", NewFolderDraft(mode = NewFolderMode.AllGenres))

    @Test
    fun newName() = newFolder("07-new-name", NewFolderDraft(step = 1, pickedIds = ids("verge", "decoder"), name = "Commute", nameEdited = true))

    @Test
    fun newLook() = newFolder("08-new-look", NewFolderDraft(step = 2, pickedIds = ids("verge", "decoder", "waveform", "wan"), name = "Commute", nameEdited = true))

    @Test
    fun edit() = render("09-edit") {
        EditFolderPage(
            folder = commute,
            shows = EditFolderShows(inFolder = podcasts.filter { it.id in commute.podcastIds }, library = podcasts),
            draft = EditFolderDraft.from(commute),
            onDraftChange = {},
            actions = EditFolderActions(onClose = {}, onSave = {}, onAddShows = {}, onRemoveShow = {}, onDelete = {}),
            bottomPadding = 0.dp,
        )
    }

    private fun auto(scene: String, on: Boolean) = render(scene) {
        AutoOrganizePage(
            enabled = on,
            podcasts = podcasts,
            folders = folders,
            look = AutoOrganizeLook(),
            actions = AutoOrganizePageActions(onClose = {}, onLookChange = {}, onTurnOn = {}, onTurnOff = {}, onOpenFolder = {}, onApplyToAll = {}),
            bottomPadding = 0.dp,
        )
    }

    @Test
    fun autoOn() = auto("10-auto-on", on = true)

    @Test
    fun autoOff() = auto("11-auto-off", on = false)

    @Test
    fun sort() = render("12-sort") {
        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow) {
            SubscriptionSortSheetContent(
                config = SubscriptionSortConfig(
                    currentSort = SubscriptionSort.SmartRank,
                    folderSort = FolderInterSort.Inherit,
                    intraFolderSort = FolderIntraSort.Inherit,
                    autoOrganizeFolders = true,
                    genreFolderCount = 3,
                ),
                actions = SubscriptionSortActions({}, {}, {}, {}, {}),
                modifier = androidx.compose.ui.Modifier.padding(top = 24.dp),
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
