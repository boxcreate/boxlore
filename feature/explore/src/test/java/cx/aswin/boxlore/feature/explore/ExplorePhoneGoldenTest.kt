package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExplorePhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun explore() = render("explore", "explore") {
        ExploreContent(
            uiState = ExploreUiState.Success(
                trending = List(12) { TestFixtures.podcast(id = "$it", title = "Curious world $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) },
                selectedTab = 0,
            ),
            onSearchQueryChanged = {},
            onCategorySelected = {},
            onPodcastClick = { _, _, _, _ -> },
            onEpisodeClick = { _, _ -> },
            onTabSelected = {},
            onVibeSelected = { _, _ -> },
            onClearVibe = {},
        )
    }

    @Test fun lore() {
        val viewModel = mock(LearnViewModel::class.java)
        `when`(viewModel.uiState).thenReturn(
            MutableStateFlow<LearnUiState>(
                LearnUiState.Success(
                    List(4) { index ->
                        LearnCuriosityCard(
                            "episode-$index", "Why do familiar places feel different when we return?",
                            "Discover how memory and attention shape the places we call home.", 8,
                            "How we find our way", "Curious world", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 3_600, null,
                        )
                    },
                ),
            ),
        )
        `when`(viewModel.artworkAccentColors).thenReturn(MutableStateFlow(emptyMap()))
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(MutableStateFlow(PlayerState()))
        render("lore", "learn") {
            LearnScreen(viewModel, playback, appBottomChromeContentPadding(isMiniPlayerVisible = false), {}, {}, { _, _, _, _ -> }, {})
        }
    }

    private fun render(name: String, route: String, content: @Composable () -> Unit) {
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            BoxLoreNavigationBar(route, {}, NavigationStyle.Floating, initialContentReady = true)
                        }
                    }
                }
            }
        }
        golden.capture(name)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.matrix()
    }
}
