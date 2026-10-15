package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.theme.ArtworkMiniPlayerColors
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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
import cx.aswin.boxlore.feature.library.LibraryContent
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.player.v2.MiniPlayerActions
import cx.aswin.boxlore.feature.player.v2.MiniPlayerColors
import cx.aswin.boxlore.feature.player.v2.MiniPlayerContent
import cx.aswin.boxlore.feature.player.v2.MiniPlayerV2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellPhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)
    private val episode = TestFixtures.episode(title = "How we find our way", description = "A conversation about curiosity and the places it takes us.", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    private val podcasts = List(8) { index ->
        TestFixtures.podcast(id = "${index + 1}", title = "Curious world ${index + 1}", artist = "A listening studio", imageUrl = PhoneGoldenRule.ARTWORK_URL)
            .copy(latestEpisode = episode.copy(id = "episode-$index", podcastId = "${index + 1}"))
    }

    @Test fun homeLoaded() = render("home-loaded", "home") { Home(loading = false) }

    @Test fun homeLoading() = render("home-loading", "home") { Home(loading = true) }

    @Test fun library() = render("library", "library") {
        LibraryContent(LibraryUiState.Success(subscribedPodcasts = podcasts), {}, {}, {}, {})
    }

    @Test fun miniPlayerOverHome() = render("mini-player-home", "home") {
        Home(loading = false)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            MiniPlayerV2(
                content = MiniPlayerContent(episode, podcasts.first().title, PhoneGoldenRule.ARTWORK_URL, false, false, 120_000L, 3_600_000L),
                colors = MiniPlayerColors(MaterialTheme.colorScheme, ArtworkMiniPlayerColors.fromScheme(MaterialTheme.colorScheme)),
                actions = MiniPlayerActions({}, {}, {}, {}),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 100.dp).fillMaxWidth().height(64.dp),
            )
        }
    }

    private fun render(name: String, route: String, content: @Composable () -> Unit) {
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            BoxLoreNavigationBar(currentRoute = route, onNavigate = {}, style = NavigationStyle.Floating, initialContentReady = true)
                        }
                    }
                }
            }
        }
        golden.capture(name)
    }

    @Composable
    private fun Home(loading: Boolean) {
        HomeScreen(
            uiState = HomeUiState(
                heroItems = if (loading) emptyList() else podcasts.take(3).map { SmartHeroItem(HeroType.RESUME, it, "Continue listening") },
                discoverPodcasts = if (loading) emptyList() else podcasts,
                recommendations = if (loading) emptyList() else List(9) { episode.copy(id = "recommendation-$it") },
                subscribedPodcasts = if (loading) emptyList() else podcasts,
                isLoading = loading,
                isRecommendationsLoading = loading,
                isEditorialRowsLoading = loading,
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
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.matrix()
    }
}
