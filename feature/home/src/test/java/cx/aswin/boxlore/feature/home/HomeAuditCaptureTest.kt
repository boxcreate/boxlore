package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
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
 * with many items in every style and its loading state, a swipe from the resting first card to the
 * second, the rating, thanks and change-show sheets, the selected show while it loads and refreshes,
 * Your shows' episode rows, the caught-up Daily Mix, an empty greeting and the pin message with and
 * without the mini player, plus the states a tap opens: the mix and pin menus, the Brief's two hide
 * steps, the More genres sheet and the side rails scrolled by a card and a half. Scenes below the
 * fold scroll Home to their state and fail when it never shows. Outside audit runs the matrix is empty, so CI runs nothing here.
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

    /** Frames of a swipe from the first hero card to the second, on the default Cover style, after the resting first card. */
    @Test fun heroPeekSwipe() {
        render(HeroCardStyle.COVER) { Home(state()) }
        fixture.compose.onNodeWithTag(ROOT_TAG).assertExists()
        fixture.capture("home-hero-peek-swipe-rest")
        fixture.captureSequence("home-hero-peek-swipe", frames = 8, stepMs = 40) {
            fixture.compose.onNodeWithTag(ROOT_TAG).performTouchInput { swipeLeft(startX = centerX + width / 4f, endX = centerX - width / 4f, durationMillis = 200) }
        }
    }

    @Test fun sheetRating() = capture("home-sheet-rating", allWindows = true) { Home(state().copy(completedEpisodeCount = 12), HomeSheetUi(showReviewPrompt = true)) }

    @Test fun sheetThanks() = capture("home-sheet-thanks", allWindows = true) { Home(state(), HomeSheetUi(showPostReview = true)) }

    @Test fun sheetChangeShow() = capture("home-sheet-change-show", allWindows = true) {
        Home(state().copy(seemsToLikePodcast = podcasts[2]))
        ChangeRecommendationPodcastSheet(candidatePodcasts = podcasts, onDismissRequest = {}, onPodcastSelect = {})
    }

    @Test fun selectedShowLoading() = capture("home-selected-show-loading", scrollTo = "your_shows" to "home_selected_show_loading") {
        Home(state().copy(selectedPodcastId = podcasts[1].id, isSelectedPodcastLoading = true))
    }

    /** An RSS show refreshing over the episodes it already has: the refresh bar sits above the rows. */
    @Test fun selectedShowRefreshing() = capture("home-selected-show-refreshing", scrollTo = "your_shows" to "home_selected_show_refreshing") {
        val rss = state().subscribedPodcasts.map { if (it.id == podcasts[1].id) it.copy(sourceType = Podcast.SOURCE_RSS) else it }
        Home(state().copy(subscribedPodcasts = rss, selectedPodcastId = podcasts[1].id, selectedPodcastEpisodes = List(4) { episode.copy(id = "selected-$it") }, isSelectedRssRefreshing = true))
    }

    /** Your shows' episode rows with progress and long titles, for German and enlarged text. */
    @Test fun denseRowsLong() = capture("home-dense-rows-long", scrollTo = "your_shows" to "home_filtered_episodes") {
        val episodes = List(6) { episode.copy(id = "selected-$it", podcastId = podcasts[1].id) }
        Home(
            state().copy(
                selectedPodcastId = podcasts[1].id,
                selectedPodcastEpisodes = episodes,
                episodePlaybackState = episodes.take(3).associate { it.id to (cx.aswin.boxlore.core.model.EpisodeStatus.IN_PROGRESS to 0.4f) },
            ),
        )
    }

    /** The greeting's place with its picks empty: where the greeting was, Explore shows' heading now follows. */
    @Test fun greetingEmpty() = capture("home-greeting-empty", scrollTo = "discover_header" to GENRES_MORE_TAG) {
        Home(state().copy(editorialRows = emptyList(), isEditorialRowsLoading = false, recommendations = emptyList(), isRecommendationsLoading = false))
    }

    /** Daily Mix with nothing new: the caught-up state in Your shows (tablets show it beside the Brief). */
    @Test fun mixCaughtUp() {
        capture("home-mix-caught-up", scrollTo = "your_shows" to "home_show_selector_mix") { Home(state().copy(latestEpisodes = emptyList())) }
    }

    @Test fun snackbarPin() = capture("home-snackbar-pin") {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(host) { host.showSnackbar(HomePinnedShows.capacityUserMessage()) }
        Home(state(), snackbarHostState = host)
    }

    /** The pin message while an episode plays: a placeholder stands where the app's mini player sits, over the nav bar. */
    @Test fun snackbarPinMiniPlayer() = capture("home-snackbar-pin-mini-player", miniPlayer = true) {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(host) { host.showSnackbar(HomePinnedShows.capacityUserMessage()) }
        Home(state(), snackbarHostState = host)
    }

    /** The Daily/Offline mix menu open over Your shows (it shows once a download has finished). */
    @Test fun menuMix() = capture(
        "home-menu-mix",
        allWindows = true,
        scrollTo = "your_shows" to MIX_TRIGGER_TAG,
        act = { click(MIX_TRIGGER_TAG) },
        waitFor = "home_mix_menu",
    ) { Home(state(), playback = withDownload()) }

    /** The Pin menu after a long press on a show cover in Your shows. */
    @Test fun menuPin() = capture(
        "home-menu-pin",
        allWindows = true,
        scrollTo = "your_shows" to "home_show_selector_${podcasts[2].id}",
        act = { fixture.compose.onNodeWithTag("home_show_selector_${podcasts[2].id}").performSemanticsAction(SemanticsActions.OnLongClick) },
        waitFor = "home_pin_menu",
    ) { Home(state()) }

    /** The Brief after tapping hide: the "hide for today?" step. */
    @Test fun briefConfirmToday() {
        render(HeroCardStyle.COVER) { Home(state().copy(briefing = brief, briefingChapters = briefChapters)) }
        scrollFeedTo(BRIEF_KEYS, BRIEF_HIDE_TAG)
        click(BRIEF_HIDE_TAG)
        awaitTag("home_brief_hide_today")
        reveal("home_brief_always_hide")
        fixture.capture("home-brief-confirm-today")
    }

    /** The Brief's second step, "always hide?". */
    @Test fun briefConfirmForever() {
        render(HeroCardStyle.COVER) { Home(state().copy(briefing = brief, briefingChapters = briefChapters)) }
        scrollFeedTo(BRIEF_KEYS, BRIEF_HIDE_TAG)
        click(BRIEF_HIDE_TAG)
        awaitTag("home_brief_always_hide")
        click("home_brief_always_hide")
        awaitTag("home_brief_confirm_always_hide")
        reveal("home_brief_confirm_always_hide")
        fixture.capture("home-brief-confirm-forever")
    }

    /** The More genres sheet over Explore shows. */
    @Test fun genresSheet() = capture(
        "home-genres-sheet",
        allWindows = true,
        scrollTo = "discover_header" to "home_genre_chip",
        act = {
            awaitTag(GENRES_MORE_TAG)
            click(GENRES_MORE_TAG)
        },
        waitFor = "home_genres_sheet",
    ) { Home(state()) }

    /**
     * Because you like and the time-of-day picks, each rail scrolled by one and a half cards, so the
     * captures show where cards are cut at the side while they move.
     */
    @Test fun railsScrolled() {
        render(HeroCardStyle.COVER) { Home(railState()) }
        scrollFeedTo(listOf("because_you_like"), RAIL_TAG)
        scrollRails()
        fixture.capture("home-rails-scrolled")
        scrollFeedTo(listOf("editorial_${editorialRow.providerId}"), RAIL_TAG)
        scrollRails()
        fixture.capture("home-rails-scrolled-daypart")
    }

    private fun scrollRails() {
        val compose = fixture.compose
        compose.onAllNodesWithTag(RAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes().indices.forEach { index ->
            val rail = compose.onAllNodesWithTag(RAIL_TAG, useUnmergedTree = true)[index]
            val firstCard = rail.onChildAt(0).fetchSemanticsNode().size.width
            rail.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(firstCard * 1.5f, 0f) }
        }
        compose.waitForIdle()
    }

    private fun hero(style: String) = capture("home-hero-many-${styleName(style)}", style) { Home(state()) }

    private fun loading(style: String) = capture("home-loading-${styleName(style)}", style) { Home(state(loading = true)) }

    /**
     * Renders [content] and captures it as [name]. With [scrollTo] (a grid item key and a tag inside
     * it), Home's grid first scrolls to that item, and the scene fails when the tagged state never shows.
     */
    @Suppress("LongParameterList")
    private fun capture(
        name: String,
        style: String = HeroCardStyle.COVER,
        allWindows: Boolean = false,
        scrollTo: Pair<String, String>? = null,
        miniPlayer: Boolean = false,
        act: (() -> Unit)? = null,
        waitFor: String? = null,
        content: @Composable () -> Unit,
    ) {
        render(style, miniPlayer, content)
        fixture.compose.onNodeWithTag(ROOT_TAG).assertExists()
        if (scrollTo != null) {
            val (key, tag) = scrollTo
            scrollFeedTo(listOf(key), tag)
        }
        if (act != null) {
            act()
            fixture.compose.waitForIdle()
        }
        if (waitFor != null) awaitTag(waitFor)
        fixture.capture(name, allWindows)
    }

    /** Scrolls Home's grid to the first of [keys] it has (tablets group some sections) and waits for [tag] there. */
    private fun scrollFeedTo(keys: List<String>, tag: String) {
        val compose = fixture.compose
        // Sections can wait on saved preferences, so give them a moment to appear before scrolling.
        compose.waitUntil(SCENE_WAIT_MS) { keys.any { key -> runCatching { compose.onNodeWithTag(FEED_TAG).performScrollToKey(key) }.isSuccess } }
        awaitTag(tag)
        reveal(tag)
    }

    /** Scrolls Home just enough to show [tag] whole; on short windows a tall section can leave it below the fold. */
    private fun reveal(tag: String) {
        val compose = fixture.compose
        val grid = compose.onNodeWithTag(FEED_TAG).fetchSemanticsNode().boundsInRoot
        val node = compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
        val bottomClearance = with(compose.density) { BOTTOM_BAR_CLEARANCE.toPx() }
        val below = node.bottom - (grid.bottom - bottomClearance)
        if (below <= 0f) return
        // A slow drag that rests before lifting, so the grid moves by [below] without a fling.
        compose.onNodeWithTag(FEED_TAG).performTouchInput {
            // From the middle: the floating nav bar over the grid's foot would take the touch.
            val start = Offset(centerX, centerY + minOf(below, centerY) / 2f)
            down(start)
            val steps = 20
            // Plus the touch slop, which the grid takes before it starts to scroll.
            val distance = below + viewConfiguration.touchSlop
            repeat(steps) { moveBy(Offset(0f, -distance / steps), delayMillis = 16) }
            advanceEventTime(300)
            up()
        }
        compose.mainClock.advanceTimeBy(SCENE_SETTLE_MS)
    }

    /** Captures run on a paused clock, so step it until [tag] shows, failing after [SCENE_WAIT_MS] of app time. */
    private fun awaitTag(tag: String) {
        val compose = fixture.compose
        var waited = 0L
        while (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            check(waited < SCENE_WAIT_MS) { "$tag never showed" }
            compose.mainClock.advanceTimeBy(SCENE_STEP_MS)
            waited += SCENE_STEP_MS
        }
    }

    /** Taps [tag] through its click action, as TalkBack would, so a bar drawn over it can't take the tap. */
    private fun click(tag: String) = fixture.compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    private fun render(style: String, miniPlayer: Boolean = false, content: @Composable () -> Unit) {
        fixture.assumeAuditCapture()
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
                CompositionLocalProvider(LocalHeroCardStyle provides homeHeroStyle(style, tablet), LocalBoxloreMiniPlayerVisible provides miniPlayer) {
                    Surface(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            if (miniPlayer) MiniPlayerPlaceholder(Modifier.align(Alignment.BottomCenter))
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

    /** :feature:home can't show the real mini player (another feature), so a solid bar marks where it sits above the nav bar. */
    @Composable
    private fun MiniPlayerPlaceholder(modifier: Modifier) {
        Box(
            modifier.padding(start = 16.dp, end = 16.dp, bottom = appBottomChromeContentPadding(isMiniPlayerVisible = false))
                .fillMaxWidth().widthIn(max = 560.dp).height(64.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(32.dp)),
        )
    }

    private val brief = Briefing("2026-11-20", "us", "The world this morning, in eight stories", "", "", "")
    private val briefChapters = List(8) { Chapter(startTime = it * 90.0, title = TestFixtures.briefingChapterTitle(it)) }
    private val editorialRow = HomeEditorialRow("morning_picks", "Your morning picks", "Fresh shows to start the day", HomeEditorialIcon.entries.first(), podcasts)

    private fun withDownload() = HomePlaybackUi(
        currentPlayingPodcastId = null,
        currentPlayingEpisodeId = null,
        isPlaying = false,
        completedDownloads = StableCompletedDownloadList(listOf(CompletedDownloadItem(episode.copy(id = "downloaded"), podcasts[1], downloadedAt = 1L))),
    )

    /** Because you like (with suggested shows) and one time-of-day row, both long enough to scroll. */
    private fun railState() = state().copy(
        seemsToLikePodcast = podcasts[2],
        becauseYouLikeRecommendations = List(8) { episode.copy(id = "because-$it") },
        becauseYouLikePodcasts = podcasts,
        editorialRows = listOf(editorialRow),
        isEditorialRowsLoading = false,
    )

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
    private fun Home(
        state: HomeUiState,
        sheets: HomeSheetUi = HomeSheetUi(),
        snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
        playback: HomePlaybackUi = HomePlaybackUi(null, null, false),
    ) {
        HomeScreen(
            uiState = state,
            playback = playback,
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
        private const val FEED_TAG = "home_feed_grid"
        private const val SCENE_WAIT_MS = 5_000L
        private const val SCENE_STEP_MS = 100L
        private const val SCENE_SETTLE_MS = 600L
        private val BOTTOM_BAR_CLEARANCE = 140.dp
        private const val MIX_TRIGGER_TAG = "home_mix_mode_trigger"
        private const val BRIEF_HIDE_TAG = "home_brief_hide"
        private const val GENRES_MORE_TAG = "home_genres_more"
        private const val RAIL_TAG = "home_discovery_rail"

        /** Phones list the Brief on its own; tablets put it beside Your shows. */
        private val BRIEF_KEYS = listOf("briefing", "your_shows")

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
