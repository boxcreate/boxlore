package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HomePinnedShows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.components.ChangeRecommendationPodcastSheet
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.homeHeroStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of Home for `-PtabletCaptures=audit` (light only, never phone goldens): the hero
 * with many items in every style and its loading state, a swipe from the first card to the second,
 * the rating, thanks and change-show sheets, the selected show while it loads, an empty greeting and
 * the pin message. Outside audit runs the matrix is empty, so CI runs nothing here.
 * `-PauditSizes` picks the sizes; the extra widths sweep the Galaxy Z Fold 8 cover band (431-599dp).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    private val episode = TestFixtures.episode(
        title = "A Thanksgiving Dinner for \$100? Yes, It's Possible",
        description = "A conversation about curiosity and the places it takes us.",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
        duration = 3_900,
    )
    private val podcasts = List(8) { index ->
        TestFixtures.podcast(
            id = "${index + 1}",
            title = if (index == 0) "The Joe Rogan Experience" else "Curious world ${index + 1}",
            artist = "A listening studio",
            imageUrl = PhoneGoldenRule.ARTWORK_URL,
        ).copy(latestEpisode = episode.copy(id = "episode-$index", podcastId = "${index + 1}"), resumeProgress = if (index % 2 == 0) 0.35f else null)
    }

    /** Eight hero items, as a listener with a full library gets: resumes, both collections and spotlights. */
    private val heroItems = listOf(
        SmartHeroItem(HeroType.RESUME, podcasts[0], "Continue listening"),
        SmartHeroItem(HeroType.RESUME_GRID, podcasts[1], "Continue listening", gridItems = podcasts.subList(1, 5)),
        SmartHeroItem(HeroType.JUMP_BACK_IN, podcasts[2], "Jump back in"),
        SmartHeroItem(HeroType.NEW_EPISODES_GRID, podcasts[3], "New episodes", gridItems = podcasts.subList(3, 7)),
        SmartHeroItem(HeroType.SPOTLIGHT, podcasts[4], "Spotlight", description = "A new show from the people behind your favourites."),
        SmartHeroItem(HeroType.RESUME, podcasts[5], "Continue listening"),
        SmartHeroItem(HeroType.SPOTLIGHT, podcasts[6], "Spotlight"),
        SmartHeroItem(HeroType.JUMP_BACK_IN, podcasts[7], "Jump back in"),
    )

    @Test fun heroManyBlock() = hero(HeroCardStyle.BLOCK)

    @Test fun heroManyCover() = hero(HeroCardStyle.COVER)

    @Test fun heroManyDuotone() = hero(HeroCardStyle.DUOTONE)

    @Test fun heroManyTide() = hero(HeroCardStyle.TIDE)

    @Test fun heroManyHalo() = hero(HeroCardStyle.HALO)

    @Test fun heroManyMarquee() = hero(HeroCardStyle.MARQUEE)

    @Test fun loadingBlock() = loading(HeroCardStyle.BLOCK)

    @Test fun loadingCover() = loading(HeroCardStyle.COVER)

    @Test fun loadingDuotone() = loading(HeroCardStyle.DUOTONE)

    @Test fun loadingTide() = loading(HeroCardStyle.TIDE)

    @Test fun loadingHalo() = loading(HeroCardStyle.HALO)

    @Test fun loadingMarquee() = loading(HeroCardStyle.MARQUEE)

    /** Frames of a swipe from the first hero card to the second, on the default Cover style. */
    @Test fun heroPeekSwipe() {
        render(HeroCardStyle.COVER) { Home(state()) }
        fixture.compose.onNodeWithTag(ROOT_TAG).assertExists()
        fixture.captureSequence("home-hero-peek-swipe", frames = 8, stepMs = 40) {
            fixture.compose.onNodeWithTag(ROOT_TAG).performTouchInput { swipeLeft(startX = centerX + width / 4f, endX = centerX - width / 4f, durationMillis = 200) }
        }
    }

    @Test fun sheetRating() = capture("home-sheet-rating", allWindows = true) { Home(state(), HomeSheetUi(showReviewPrompt = true)) }

    @Test fun sheetThanks() = capture("home-sheet-thanks", allWindows = true) { Home(state(), HomeSheetUi(showPostReview = true)) }

    @Test fun sheetChangeShow() = capture("home-sheet-change-show", allWindows = true) {
        Home(state().copy(seemsToLikePodcast = podcasts[2]))
        ChangeRecommendationPodcastSheet(candidatePodcasts = podcasts, onDismissRequest = {}, onPodcastSelect = {})
    }

    @Test fun selectedShowLoading() = capture("home-selected-show-loading") {
        Home(state().copy(selectedPodcastId = podcasts[1].id, isSelectedPodcastLoading = true))
    }

    @Test fun selectedShowRefreshing() = capture("home-selected-show-refreshing") {
        Home(state().copy(selectedPodcastId = podcasts[1].id, selectedPodcastEpisodes = List(4) { episode.copy(id = "selected-$it") }, isSelectedRssRefreshing = true))
    }

    @Test fun greetingEmpty() = capture("home-greeting-empty") {
        Home(state().copy(editorialRows = emptyList(), isEditorialRowsLoading = false, recommendations = emptyList(), isRecommendationsLoading = false))
    }

    @Test fun snackbarPin() = capture("home-snackbar-pin") {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(host) { host.showSnackbar(HomePinnedShows.capacityUserMessage()) }
        Home(state(), snackbarHostState = host)
    }

    private fun hero(style: String) = capture("home-hero-many-${styleName(style)}", style) { Home(state()) }

    private fun loading(style: String) = capture("home-loading-${styleName(style)}", style) { Home(state(loading = true)) }

    private fun capture(name: String, style: String = HeroCardStyle.COVER, allWindows: Boolean = false, content: @Composable () -> Unit) {
        render(style, content)
        fixture.compose.onNodeWithTag(ROOT_TAG).assertExists()
        fixture.capture(name, allWindows)
    }

    private fun render(style: String, content: @Composable () -> Unit) {
        fixture.assumeAuditCapture()
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
                CompositionLocalProvider(LocalHeroCardStyle provides homeHeroStyle(style, tablet)) {
                    Surface(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            if (!tablet) {
                                Box(Modifier.align(Alignment.BottomCenter)) {
                                    BoxLoreNavigationBar(currentRoute = "home", onNavigate = {}, style = NavigationStyle.Floating, initialContentReady = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun state(loading: Boolean = false) = HomeUiState(
        heroItems = if (loading) emptyList() else heroItems,
        discoverPodcasts = if (loading) emptyList() else podcasts,
        recommendations = if (loading) emptyList() else List(9) { episode.copy(id = "recommendation-$it") },
        subscribedPodcasts = if (loading) emptyList() else podcasts,
        isLoading = loading,
        isRecommendationsLoading = loading,
        isEditorialRowsLoading = loading,
    )

    @Composable
    private fun Home(state: HomeUiState, sheets: HomeSheetUi = HomeSheetUi(), snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }) {
        HomeScreen(
            uiState = state,
            playback = HomePlaybackUi(null, null, false),
            sheets = sheets,
            callbacks = HomeScreenCallbacks(feed = feedCallbacks()),
            snackbarHostState = snackbarHostState,
        )
    }

    private fun feedCallbacks() = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = { _: Episode, _: Podcast, _: String? -> },
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null, onNavigateToExplore = null,
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {},
        onAiOnboardingClick = {}, onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {},
        onDismissBriefingForever = {}, onFeedbackClick = {},
    )

    companion object {
        private const val ROOT_TAG = "home_audit_root"

        private fun styleName(style: String) = when (style) {
            HeroCardStyle.BLOCK -> "block"
            HeroCardStyle.COVER -> "cover"
            else -> style
        }

        /** Widths around the Fold 8 cover where the hero used to jump between one card and several. */
        private val COVER_BAND_SWEEP = listOf(455 to 700, 465 to 700, 485 to 700, 498 to 700).map { (width, height) ->
            PhoneGoldenConfiguration(width, height, false)
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> =
            if (System.getProperty("boxlore.tabletCaptures") == "audit") PhoneGoldenConfiguration.auditMatrix(extra = COVER_BAND_SWEEP) else emptyList()
    }
}
