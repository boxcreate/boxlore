package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.designsystem.component.AppLoreNavigationActionSize
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHorizontalInset
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appMiniPlayerTopOffset
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.HomeFeedCallbacks
import cx.aswin.boxlore.feature.home.HomePlaybackUi
import cx.aswin.boxlore.feature.home.HomeScreen
import cx.aswin.boxlore.feature.home.HomeScreenCallbacks
import cx.aswin.boxlore.feature.home.HomeSheetUi
import cx.aswin.boxlore.feature.home.HomeUiState
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.navigation.rememberTabletDetailNavigation
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of the app's chrome as it is today (run with `-PtabletCaptures=audit`; skipped
 * otherwise): the phone's floating bar and mini player, the rail's labels in long languages, the
 * shell on a half-open fold with and without playback, snackbars over the chrome, and the playback
 * notices. Phones use the real bottom bar and player sheet with the root's placement; tablet sizes
 * use the real shell.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellChromeAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)
    private val episode = TestFixtures.episode(title = "How we find our way", description = "A conversation about curiosity and the places it takes us.", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    private val podcasts = List(8) { index ->
        TestFixtures.podcast(id = "${index + 1}", title = "Curious world ${index + 1}", artist = "A listening studio", imageUrl = PhoneGoldenRule.ARTWORK_URL)
            .copy(latestEpisode = episode.copy(id = "episode-$index", podcastId = "${index + 1}"))
    }
    private val size get() = golden.configuration
    private val isRailOnlySize get() = size in RAIL_SIZES
    private val isTablet get() = size.width >= 600 && size.height >= 480
    private val isFold get() = size.posture.startsWith("tabletop") || size.posture == "book"

    /** The floating bar and mini player over Home on compact windows (tablets dock the player). */
    @Test fun shellPhoneChrome() = render("shell-phone-chrome", include = !isTablet && !isRailOnlySize) { Shell(playing = true) { Home() } }

    /** Rail labels in long languages and large text, beside English at the same size. */
    @Test fun shellRailLabels() = render("shell-rail-labels", include = isRailOnlySize || size in RAIL_REFERENCE_SIZES) {
        Shell(playing = false) { Home() }
    }

    @Test fun shellIdleFold() = render("shell-idle-fold", include = isFold) { Shell(playing = false) { Home() } }

    @Test fun shellPlayingFold() = render("shell-playing-fold", include = isFold) { Shell(playing = true) { Home() } }

    @Test fun snackbar() = render("snackbar", include = !isRailOnlySize) { Shell(playing = false) { PageWithSnackbar() } }

    @Test fun snackbarMiniPlayer() = render("snackbar-mini-player", include = !isRailOnlySize) { Shell(playing = true) { PageWithSnackbar() } }

    @Test fun noticesSleep() = render("notices-sleep", include = !isRailOnlySize) {
        Box(Modifier.fillMaxSize()) {
            Shell(playing = true) { Home() }
            AppPlaybackNotices(repairVisible = false, nudgeVisible = true, playbackRepository = mock(PlaybackRepository::class.java))
        }
    }

    @Test fun noticesRepair() = render("notices-repair", include = !isRailOnlySize) {
        Box(Modifier.fillMaxSize()) {
            Shell(playing = true) { Home() }
            AppPlaybackNotices(repairVisible = true, nudgeVisible = false, playbackRepository = mock(PlaybackRepository::class.java))
        }
    }

    private fun render(name: String, include: Boolean, content: @Composable () -> Unit) {
        golden.assumeAuditCapture()
        assumeTrue("$name isn't captured at $size", include)
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize().testTag(SHELL_TAG)) { content() }
            }
        }
        golden.compose.onNodeWithTag(SHELL_TAG).assertExists()
        golden.capture(name, allWindows = true)
    }

    /** The phone root's bar and player sheet, or the tablet shell, around [content]. */
    @Composable
    private fun Shell(playing: Boolean, content: @Composable () -> Unit) {
        if (LocalBoxloreWindowLayout.current.usesTabletLayout) {
            TabletShellCapture("home", episode, podcasts.first(), playing, content)
            return
        }
        val dependencies = remember { tabletCapturePlayer(episode, podcasts.first()) }
        val compactProgress = remember { mutableFloatStateOf(0f) }
        val navigation = rememberTabletDetailNavigation(rememberNavController())
        val primary = rememberNavController()
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            content()
            val (leftInset, rightInset) = phoneChromeSideOffsets(NavigationStyle.Floating, maxWidth)
            PhoneBottomNavigation(primary, navigation, "home", NavigationStyle.Floating, null, true, compactProgress, leftInset to rightInset)
            if (playing) {
                // The placement BoxLoreAppRoot gives the phone player sheet.
                val systemNavigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val metrics = navigationChromeMetrics(NavigationStyle.Floating)
                PlayerSheetScaffold(
                    playbackRepository = dependencies.playbackRepository,
                    downloadRepository = dependencies.downloadRepository,
                    userPrefs = dependencies.userPrefs,
                    layout = PlayerSheetLayout(
                        collapsedTargetY = with(density) { appMiniPlayerTopOffset(NavigationStyle.Floating, maxHeight, systemNavigation).toPx() },
                        containerHeight = maxHeight + systemNavigation + 50.dp,
                        collapsedHorizontalPadding = AppNavigationBarHorizontalInset,
                        collapsedLeftInset = leftInset,
                        collapsedRightInset = rightInset,
                        navigationStyle = NavigationStyle.Floating,
                        compactProgress = compactProgress,
                        compactTargetY = with(density) {
                            (maxHeight - metrics.bottomNavigationClearance - systemNavigation + (metrics.navigationBarHeight - AppLoreNavigationActionSize) / 2).toPx()
                        },
                    ),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }

    /** A page that hosts its own snackbar, as feature pages do today. */
    @Composable
    private fun PageWithSnackbar() {
        val state = remember { SnackbarHostState() }
        val message = stringResource(cx.aswin.boxlore.core.designsystem.R.string.share_instagram_link_copied)
        LaunchedEffect(state) { state.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
        Scaffold(snackbarHost = { SnackbarHost(state) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { Home() }
        }
    }

    @Composable
    private fun Home() {
        HomeScreen(
            uiState = HomeUiState(
                heroItems = podcasts.take(3).map { SmartHeroItem(HeroType.RESUME, it, "Continue listening") },
                discoverPodcasts = podcasts,
                recommendations = List(9) { episode.copy(id = "recommendation-$it") },
                subscribedPodcasts = podcasts,
                isLoading = false,
                isRecommendationsLoading = false,
                isEditorialRowsLoading = false,
            ),
            playback = HomePlaybackUi(null, null, false),
            sheets = HomeSheetUi(),
            callbacks = HomeScreenCallbacks(
                feed = HomeFeedCallbacks(
                    onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = null,
                    onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null, onNavigateToExplore = null,
                    onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
                    onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {},
                    onAiOnboardingClick = {}, onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {},
                    onDismissBriefingForever = {}, onFeedbackClick = {},
                ),
            ),
        )
    }

    companion object {
        private const val SHELL_TAG = "audit-shell"

        /** The rail's long-text set: German, French and en-XA at 100% and 130% on a portrait tablet. */
        private val RAIL_SIZES = listOf("de", "fr", "en-rXA").flatMap { locale ->
            listOf(1f, 1.3f).map { scale -> PhoneGoldenConfiguration(800, 1_280, false, fontScale = scale, locale = locale) }
        }
        private val RAIL_REFERENCE_SIZES = listOf(PhoneGoldenConfiguration(800, 1_280, false), PhoneGoldenConfiguration(932, 650, false, locale = "de"))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix(extra = RAIL_SIZES)
    }
}
