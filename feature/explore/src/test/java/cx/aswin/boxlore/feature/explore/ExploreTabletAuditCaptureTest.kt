package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of Explore and Lore states. `-PtabletCaptures=audit` renders every scene at the
 * shared audit sizes (filter with `-PauditSizes`); `-PtabletCaptures=after` keeps the older tablet
 * set of the first five scenes. The window comes from the capture configuration.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreTabletAuditCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") in setOf("after", "audit"))
        fixture.prepare()
        when {
            scene.startsWith("lore-history") -> renderHistory()
            scene.startsWith("lore") -> renderLore()
            else -> renderExplore()
        }
        when (scene) {
            "genre-sheet" -> openMoreGenres()
            "lore-history-clear" -> fixture.compose.onNodeWithContentDescription(string(R.string.lore_history_clear)).performClick()
            "search-ime" -> fixture.compose.runOnUiThread {
                applyFakeSystemInsets(fixture.compose.activity, (fixture.configuration.systemInsets ?: SystemInsets()).copy(imeBottom = IME_DP))
            }
        }
        fixture.capture("explore-$scene", allWindows = scene in DialogScenes)
    }

    private fun renderExplore() {
        val shows = List(15) { index ->
            TestFixtures.podcast(id = "$index", title = if (index == 1) LONG_SHOW else "Curious world $index", imageUrl = PhoneGoldenRule.ARTWORK_URL, genre = GENRES[index % GENRES.size])
        }
        val episodes = List(15) { index ->
            TestFixtures.episode(id = "$index", title = "How we understand the universe $index", podcastTitle = if (index == 1) LONG_SHOW else "Curious world $index", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        }
        val moods = listOf("Learn something new" to "Learn something new", "Feel good" to "Feel good", "What's happening" to "What's happening")
        val state = when (scene) {
            "show-search", "search-ime" -> ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = shows, alsoFoundResults = shows.takeLast(3))
            "episode-search" -> ExploreUiState.Success(searchQuery = "space", isSearching = true, searchTab = SearchTab.EPISODES, hasPerformedSemanticSearch = true, semanticPodcastResults = shows, semanticSearchResults = episodes)
            "topic" -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, searchResults = shows)
            "empty-topic", "failed-topic" -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true)
            "loading-topic" -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, isLoading = true)
            "for-you" -> ExploreUiState.Success(recommendations = episodes, suggestedVibes = moods, selectedTab = 1)
            "for-you-empty" -> ExploreUiState.Success(suggestedVibes = moods, selectedTab = 1)
            "genre-sheet" -> ExploreUiState.Success(trending = shows, selectedTab = 0)
            "top-genre" -> ExploreUiState.Success(trending = shows, selectedTab = 0, currentCategory = "Comedy")
            "show-landing" -> ExploreUiState.Success(suggestedVibes = moods, selectedTab = 0)
            "ask-landing" -> ExploreUiState.Success(suggestedVibes = moods, selectedTab = 0, searchTab = SearchTab.EPISODES)
            else -> error("Unknown Explore scene $scene")
        }
        val openSearch = scene == "show-landing" || scene == "ask-landing"
        withChrome("explore") {
            ExploreContent(
                uiState = state, onSearchQueryChanged = {}, onCategorySelected = {}, onPodcastClick = { _, _, _, _ -> }, onEpisodeClick = { _, _ -> },
                onTabSelected = {}, onVibeSelected = { _, _ -> }, onClearVibe = {}, searchRequested = openSearch,
            )
        }
    }

    private fun renderLore() {
        val viewModel = mock(LearnViewModel::class.java)
        val cards = List(4) { index ->
            if (scene == "lore-long") {
                LearnCuriosityCard(
                    "episode-$index", LONG_QUESTION, LONG_EXPLANATION, 8, "The night the lights went out across a whole continent", LONG_SHOW,
                    PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 5_400, null,
                )
            } else {
                LearnCuriosityCard(
                    "episode-$index", "Why do familiar places feel different when we return?", "Discover how memory and attention shape the places we call home.", 8,
                    "How we find our way", "Curious world", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 3_600, null,
                )
            }
        }
        val state: LearnUiState = when (scene) {
            "lore-loading" -> LearnUiState.Loading
            "lore-caught-up", "lore-caught-up-mini" -> LearnUiState.CaughtUp()
            "lore-error" -> LearnUiState.Error(UiText.res(R.string.lore_error_load_failed))
            else -> LearnUiState.Success(cards)
        }
        `when`(viewModel.uiState).thenReturn(MutableStateFlow(state))
        `when`(viewModel.artworkAccentColors).thenReturn(MutableStateFlow(emptyMap()))
        val mini = scene == "lore-caught-up-mini"
        val playback = mock(PlaybackRepository::class.java)
        val playing = if (mini) PlayerState(currentEpisode = Episode(id = "playing", title = "Playing", description = "", audioUrl = "")) else PlayerState()
        `when`(playback.playerState).thenReturn(MutableStateFlow(playing))
        withChrome("learn") {
            LearnScreen(viewModel, playback, appBottomChromeContentPadding(isMiniPlayerVisible = mini), {}, {}, { _, _, _, _ -> }, {})
        }
    }

    private fun renderHistory() {
        val viewModel = mock(LearnHistoryViewModel::class.java)
        val now = System.currentTimeMillis()
        val entries = if (scene == "lore-history-empty") {
            emptyList()
        } else {
            List(8) { index ->
                LearnHistoryEntry(
                    episodeId = "history-$index",
                    episodeTitle = "How we find our way $index",
                    podcastTitle = if (index == 2) LONG_SHOW else "Curious world",
                    imageUrl = PhoneGoldenRule.ARTWORK_URL,
                    feedImage = PhoneGoldenRule.ARTWORK_URL,
                    podcastId = "1",
                    audioUrl = null,
                    duration = 3_600,
                    description = null,
                    question = if (index == 1) LONG_QUESTION else "Why do familiar places feel different when we return?",
                    explanation = null,
                    curiosityScore = 8,
                    action = if (index % 3 == 0) LearnHistoryAction.QUEUE else LearnHistoryAction.DISMISS,
                    dismissedAtMs = now - index * 26L * HOUR_MS,
                )
            }
        }
        `when`(viewModel.uiState).thenReturn(MutableStateFlow(LearnHistoryUiState.Success(entries)))
        withChrome("learn") {
            LearnHistoryScreen(viewModel, appBottomChromeContentPadding(isMiniPlayerVisible = false), {}, {})
        }
    }

    /** The floating nav bar draws over compact windows, as in the app; tablets use the shell's rail. */
    private fun withChrome(route: String, content: @Composable () -> Unit) {
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        if (!LocalBoxloreWindowLayout.current.usesTabletLayout) {
                            Box(Modifier.align(Alignment.BottomCenter)) {
                                BoxLoreNavigationBar(route, {}, NavigationStyle.Floating, initialContentReady = true)
                            }
                        }
                    }
                }
            }
        }
    }

    /** More sits at the end of the compact genre row, so the row scrolls to it first. */
    private fun openMoreGenres() {
        val more = string(R.string.explore_genre_more)
        val compose = fixture.compose
        compose.mainClock.advanceTimeBy(600)
        if (compose.onAllNodesWithText(more).fetchSemanticsNodes().isEmpty()) {
            // The clock is paused, so scroll by index (one jump) and let one frame lay the row out.
            compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(string(R.string.explore_genre_all)))).performScrollToIndex(COMPACT_MORE_INDEX)
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onAllNodesWithText(more)[0].performClick()
        // The sheet opens in its own window over a few frames; step the paused clock until it's there.
        val title = string(R.string.explore_genre_browse_title)
        var waited = 0L
        while (compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() && waited < SHEET_WAIT_MS) {
            compose.mainClock.advanceTimeBy(SHEET_STEP_MS)
            waited += SHEET_STEP_MS
        }
        repeat(SHEET_SETTLE_STEPS) { compose.mainClock.advanceTimeBy(SHEET_STEP_MS) }
    }

    private fun string(id: Int): String = fixture.compose.activity.getString(id)

    companion object {
        private const val IME_DP = 300
        private const val SHEET_STEP_MS = 100L
        private const val SHEET_WAIT_MS = 3_000L
        private const val SHEET_SETTLE_STEPS = 10

        /** All, five top genres, then More. */
        private const val COMPACT_MORE_INDEX = 6
        private const val HOUR_MS = 3_600_000L
        const val LONG_SHOW = "The Rest Is History: ancient worlds, lost empires and strange tales"
        const val LONG_QUESTION = "Why did a single faulty relay in a quiet substation leave fifty million people in the dark for days, and what did it teach engineers?"
        const val LONG_EXPLANATION = "Follow the minute-by-minute cascade of the 2003 blackout, from an overgrown tree line in Ohio to the control rooms that missed the warning signs, and the rules that changed afterwards."
        private val GENRES = listOf("Society & Culture", "Comedy", "Podcast", "History", "")
        private val DialogScenes = setOf("genre-sheet", "lore-history-clear")

        private val TabletAfterScenes = listOf("show-search", "episode-search", "topic", "empty-topic", "loading-topic")
        val AuditScenes = TabletAfterScenes + listOf(
            "for-you", "for-you-empty", "genre-sheet", "show-landing", "ask-landing", "failed-topic", "search-ime", "top-genre",
            "lore-loading", "lore-caught-up", "lore-caught-up-mini", "lore-error", "lore-long", "lore-history", "lore-history-empty", "lore-history-clear",
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = when (System.getProperty("boxlore.tabletCaptures")) {
            "audit" -> AuditScenes.flatMap { scene -> PhoneGoldenConfiguration.auditConfigurations().map { arrayOf<Any>(scene, it) } }
            "after" -> TabletAfterScenes.flatMap { scene ->
                listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) } }
            }
            else -> listOf(arrayOf("show-search", PhoneGoldenConfiguration(412, 915, false)))
        }
    }
}
