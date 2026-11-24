package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Audit-only captures of the regular show page in every state the adaptive work changes. Runs only
 * with `-PtabletCaptures=audit` and never writes phone baselines.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoShowAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Before fun auditOnly() = golden.assumeAuditCapture()

    @Test fun showRich() = render("show-rich") { page(model(success())) }

    @Test fun showRichOldest() = render("show-rich-oldest") { page(model(success().copy(currentSort = EpisodeSort.OLDEST))) }

    @Test fun showUnfollowed() = render("show-unfollowed") {
        page(model(success(podcast = richPodcast.copy(genre = "Podcast", fundingUrl = null, fundingMessage = null), subscribed = false)))
    }

    @Test fun showLocked() = render("show-locked") { page(model(success(podcast = richPodcast.copy(isLocked = true)))) }

    @Test fun showLoading() = render("show-loading") { page(model(PodcastInfoUiState.Loading)) }

    @Test fun showError() = render("show-error") { page(model(PodcastInfoUiState.Error)) }

    @Test fun showTitlesOnly() = render("show-titles-only") { page(model(success(), titlesOnly = true)) }

    @Test fun showSearchEmpty() = search("show-search-empty") { it.copy(searchQuery = "zebra", searchResults = emptyList()) }

    @Test fun showSearchFailed() = search("show-search-failed") { it.copy(searchQuery = "zebra", searchResults = emptyList(), searchFailed = true) }

    @Test fun showSearchLoading() = search("show-search-loading") { it.copy(searchQuery = "zebra", isSearching = true) }

    @Test fun showSearchResults() = search("show-search-results") { state ->
        state.copy(searchQuery = "The", searchResults = state.episodes.filter { it.title.startsWith("The") })
    }

    @Test fun showSelection() = render("show-selection", action = ::selectThree) { page(model(success())) }

    @Test fun showRangeMenu() = render("show-range-menu", allWindows = true, action = {
        selectThree()
        golden.compose.onNodeWithContentDescription(text(R.string.episode_selection_more)).performClick()
        settle()
    }) { page(model(success())) }

    @Test fun showMenu() = render("show-menu", allWindows = true, action = ::openMore) { page(model(success())) }

    @Test fun showWarning() = render("show-warning", action = {
        toolbarControl(text(R.string.podcast_info_auto_download)).performClick()
        settle()
    }) { page(model(success(), backgroundChecks = false)) }

    @Test fun showSheetPlayback() = render("show-sheet-playback", allWindows = true, action = {
        openMore()
        menuItem(text(R.string.podcast_info_playback_for_show))
    }) { page(model(success())) }

    @Test fun showSheetGenre() = render("show-sheet-genre", allWindows = true, action = {
        openMore()
        menuItem(text(R.string.podcast_info_change_genre))
    }) { page(model(success())) }

    @Test fun showDialogMarkAll() = render("show-dialog-markall", allWindows = true, action = {
        openMore()
        menuItem(text(R.string.podcast_info_mark_all_played))
    }) { page(model(success())) }

    @Test fun showDialogRss() = render("show-dialog-rss", allWindows = true, action = {
        toolbarControl(text(R.string.podcast_info_notifications)).performClick()
        settle()
    }) { page(model(success(podcast = richPodcast.copy(sourceType = Podcast.SOURCE_RSS, feedUrl = "https://feeds.example/curious.xml")))) }

    @Test fun showLockedExpanded() = render("show-locked-expanded", action = ::openDescription) {
        page(model(success(podcast = richPodcast.copy(isLocked = true))))
    }

    @Test fun showDialogLocked() = render("show-dialog-locked", allWindows = true, action = {
        openDescription()
        clickInView(golden.compose.onNodeWithText(text(R.string.podcast_info_locked_label)))
    }) { page(model(success(podcast = richPodcast.copy(isLocked = true)))) }

    @Test fun showSortMenu() = render("show-sort-menu", allWindows = true, action = {
        toolbarControl(text(R.string.podcast_info_sort_and_view)).performClick()
        settle()
    }) { page(model(success().copy(currentSort = EpisodeSort.OLDEST))) }

    @Test fun showSearchPlayed() = search("show-search-played") { state ->
        state.copy(searchQuery = "Un", searchResults = state.episodes.filter { it.title.startsWith("Un") || it.title.startsWith("The") })
    }

    @Test fun showSearchOpen() = only(SEARCH_OPEN_SIZES) {
        render("show-search-open", action = {
            toolbarControl(text(R.string.podcast_info_search)).performClick()
            settle()
        }) { page(model(success())) }
    }

    @Test fun showAddGenre() = render("show-add-genre") { page(model(success(podcast = richPodcast.copy(genre = "Podcast")))) }

    @Test fun showRichScrolled() = render("show-rich-scrolled", action = {
        list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, with(golden.compose.density) { 60.dp.toPx() }) }
        settle()
    }) { page(model(success())) }

    @Test fun showTooltipMore() = only(TOOLTIP_SIZES) {
        render("show-tooltip-more", allWindows = true, action = {
            golden.compose.onNodeWithContentDescription(text(R.string.podcast_info_more_options)).performTouchInput { longClick() }
            settle()
        }) { page(model(success())) }
    }

    /** Frames of a live resize across a header gate: the header swaps form with no blank frame. */
    @Test fun showResize() {
        val (from, to) = RESIZES[golden.configuration.toString()] ?: return assumeTrue("resize sizes only", false)
        val size = mutableStateOf(from)
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                val window = size.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window, LocalBoxlorePaneWidth provides window.widthDp) {
                    // The capture keeps its size while the page inside it resizes.
                    Box(Modifier.fillMaxSize()) { Surface(Modifier.size(window.widthDp.dp, window.heightDp.dp)) { page(model(success())) } }
                }
            }
        }
        golden.compose.mainClock.advanceTimeBy(1_600)
        golden.compose.waitForIdle()
        golden.captureSequence("show-resize-${from.widthDp.toInt()}x${from.heightDp.toInt()}", frames = 4, stepMs = STEP_MS) { size.value = to }
    }

    /** Frames of following a show: Subscribe turns into Subscribed with its check. */
    @Test fun showSubscribeMoment() = only(SUBSCRIBE_SIZES) {
        val state = MutableStateFlow<PodcastInfoUiState>(success(subscribed = false))
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) { Surface(Modifier.fillMaxSize()) { page(model(state)) } }
        }
        golden.compose.mainClock.advanceTimeBy(1_600)
        golden.compose.waitForIdle()
        list().performScrollToIndex(0)
        settle()
        // The follow lands as the ViewModel reports it: Subscribe turns into Subscribed.
        golden.captureSequence("show-subscribe", frames = 4, stepMs = STEP_MS) {
            state.value = (state.value as PodcastInfoUiState.Success).copy(isSubscribed = true)
        }
    }

    /** Runs [capture] only at the named [sizes]; other sizes skip it. */
    private fun only(sizes: Set<String>, capture: () -> Unit) {
        assumeTrue("captured at ${sizes.joinToString()} only", golden.configuration.toString() in sizes)
        capture()
    }

    /** Opens the description card, which then shows the lock notice and the creator's picks. */
    private fun openDescription() {
        list().performScrollToIndex(0)
        clickInView(golden.compose.onNodeWithText(DESCRIPTION_START, substring = true))
    }

    /**
     * Scrolls the page's list by the distance that puts [node] near the top, then clicks it. Short
     * windows start the hero's lower parts below the fold, and searching scrolls can stall there.
     */
    private fun clickInView(node: SemanticsNodeInteraction) {
        val top = node.fetchSemanticsNode().boundsInRoot.top
        val room = with(golden.compose.density) { 120.dp.toPx() }
        if (top > room) list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - room) }
        settle()
        node.performClick()
        settle()
    }

    private fun search(name: String, results: (PodcastInfoUiState.Success) -> PodcastInfoUiState.Success) {
        val state = MutableStateFlow<PodcastInfoUiState>(success())
        render(name, action = {
            toolbarControl(text(R.string.podcast_info_search)).performClick()
            settle()
            state.value = results(state.value as PodcastInfoUiState.Success)
            settle()
        }) { page(model(state)) }
    }

    /** Long-presses the first episode, then taps the next two; the list ends back on the first. */
    private fun selectThree() {
        richEpisodes.take(3).forEachIndexed { index, episode ->
            list().performScrollToKey(episode.id)
            settle()
            val card = golden.compose.onNodeWithText(episode.title)
            if (index == 0) card.performTouchInput { longClick() } else card.performClick()
            settle()
        }
        list().performScrollToKey(richEpisodes[0].id)
        settle()
    }

    private fun openMore() {
        golden.compose.onNodeWithContentDescription(text(R.string.podcast_info_more_options)).performClick()
        settle()
    }

    private fun menuItem(label: String) {
        golden.compose.onNodeWithText(label).performClick()
        settle()
    }

    /** The toolbar is the list's second item; on short windows it starts below the fold. */
    private fun toolbarControl(label: String): SemanticsNodeInteraction {
        list().performScrollToIndex(1)
        settle()
        return golden.compose.onNodeWithContentDescription(label)
    }

    private fun list() = golden.compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG)

    private fun text(id: Int) = golden.compose.activity.getString(id)

    /** The capture rule holds the clock: step it so clicks, menus and sheets get their frames. */
    private fun settle() {
        repeat(SETTLE_STEPS) {
            golden.compose.mainClock.advanceTimeBy(STEP_MS)
            golden.compose.waitForIdle()
        }
    }

    private fun render(name: String, allWindows: Boolean = false, action: (() -> Unit)? = null, content: @Composable () -> Unit) {
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), content = content)
            }
        }
        action?.let {
            golden.compose.mainClock.advanceTimeBy(1_600)
            golden.compose.waitForIdle()
            it()
        }
        golden.capture(name, allWindows)
    }

    @Composable
    private fun page(model: PodcastInfoViewModel) {
        PodcastInfoScreen(richPodcast.id, model, {}, { _, _, _ -> }, {}, {})
    }

    private fun success(podcast: Podcast = richPodcast, subscribed: Boolean = true) =
        PodcastInfoUiState.Success(podcast, richEpisodes, subscribed, hasMoreEpisodes = false)

    private fun model(state: PodcastInfoUiState, titlesOnly: Boolean = false, backgroundChecks: Boolean = true) =
        model(MutableStateFlow(state), titlesOnly, backgroundChecks)

    private fun model(
        state: MutableStateFlow<PodcastInfoUiState>,
        titlesOnly: Boolean = false,
        backgroundChecks: Boolean = true,
    ): PodcastInfoViewModel {
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(state)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(setOf(richEpisodes[4].id)))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.episodeTitlesOnly).thenReturn(MutableStateFlow(titlesOnly))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(backgroundChecks))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(setOf(richEpisodes[5].id)))
        `when`(model.folderNames).thenReturn(MutableStateFlow(listOf("Morning", "History")))
        stubArchive(model, MutableStateFlow(setOf(richEpisodes[7].id)))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(setOf(richEpisodes[1].id)))
        `when`(model.episodePlaybackState).thenReturn(
            MutableStateFlow(mapOf(richEpisodes[2].id to PodcastInfoViewModel.EpisodePlaybackState(isResume = true, progress = 0.4f))),
        )
        return model
    }

    private val richPodroll = listOf(
        PodrollItem("History Snacks", "https://feeds.example/snacks.xml"),
        PodrollItem("Atlas Hour", "https://feeds.example/atlas.xml"),
        PodrollItem("Field Notes", "https://feeds.example/field.xml"),
    )

    private val richPodcast = TestFixtures.podcast(
        id = "1",
        title = "Stuff You Missed in History Class",
        artist = "iHeartPodcasts and the Curious World Historical Society of Long Names",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
    ).copy(
        description = "$DESCRIPTION_START and Tracy as they bring you the people and events of history, from famous names to the " +
            "nearly forgotten. Every week they dig into a story you half remember and find the parts nobody taught you, " +
            "with listener mail, book picks and the occasional unearthed recipe.",
        genre = "Society & Culture",
        medium = "podcast",
        updateFrequency = "2",
        fundingUrl = "https://support.example/history",
        fundingMessage = "Support the show and get every episode early, ad free, with a bonus episode each month too",
        podroll = richPodroll,
        notificationsEnabled = false,
        autoDownloadEnabled = false,
    )

    private val richEpisodes: List<Episode> = listOf(
        richEpisode(0, "The Great Molasses Flood of 1919, and the city it left sticky for decades"),
        richEpisode(1, "Bonus: listener mail about lighthouse keepers", type = "bonus"),
        richEpisode(2, "The woman who mapped the ocean floor"),
        richEpisode(3, "Video: a walk through the oldest library in Europe", video = true),
        richEpisode(4, "The Lost Colony of Roanoke"),
        richEpisode(5, "Unearthed! Summer edition"),
        richEpisode(6, "Trailer: welcome to the show", type = "trailer", duration = 120),
        richEpisode(7, "Archived: an older episode about clocks"),
    )

    private fun richEpisode(index: Int, title: String, type: String? = null, video: Boolean = false, duration: Int = 2_700) =
        TestFixtures.episode(
            id = "show-episode-$index",
            title = title,
            podcastId = "1",
            podcastTitle = "Stuff You Missed in History Class",
            duration = duration,
            publishedDate = NOW_SECONDS - (index + 1) * 3L * 86_400,
            description = "A story you half remember, told again with the parts nobody taught you, and listener mail at the end.",
            imageUrl = PhoneGoldenRule.ARTWORK_URL,
        ).copy(
            podcastImageUrl = PhoneGoldenRule.ARTWORK_URL,
            episodeType = type,
            enclosureType = if (video) "video/mp4" else null,
            persons = if (index == 0) listOf(Person("Holly Frey", "host"), Person("Tracy V. Wilson", "host")) else null,
        )

    companion object {
        private const val DESCRIPTION_START = "Join Holly"

        /** Episodes a few days old, so dates and the release chip read as a running show's. */
        private val NOW_SECONDS = System.currentTimeMillis() / 1_000
        private const val SETTLE_STEPS = 8
        private const val STEP_MS = 100L

        private val SEARCH_OPEN_SIZES = setOf("360x800-light-fs130-de", "412x915-light")
        private val TOOLTIP_SIZES = setOf("1280x800-light")
        private val SUBSCRIBE_SIZES = setOf("412x915-light", "800x1280-light")

        /** The capture size each resize sequence runs at, and the window it starts and ends in. */
        private val RESIZES = mapOf(
            "600x960-light" to (BoxloreWindowLayout(599f, 960f) to BoxloreWindowLayout(600f, 960f)),
            "900x600-light" to (BoxloreWindowLayout(900f, 599f) to BoxloreWindowLayout(900f, 600f)),
            "600x480-light" to (BoxloreWindowLayout(599f, 479f) to BoxloreWindowLayout(599f, 480f)),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> =
            PhoneGoldenConfiguration.auditMatrix(
                extra = listOf(
                    PhoneGoldenConfiguration(360, 800, false),
                    PhoneGoldenConfiguration(360, 800, false, fontScale = 1.3f, locale = "de"),
                    PhoneGoldenConfiguration(900, 600, false),
                    // Dark: the cover backdrop behind the header's text and chips, and the sheets.
                    PhoneGoldenConfiguration(412, 915, true),
                    PhoneGoldenConfiguration(915, 412, true),
                    PhoneGoldenConfiguration(800, 1_280, true),
                    PhoneGoldenConfiguration(1_280, 800, true),
                ),
            )
    }
}
