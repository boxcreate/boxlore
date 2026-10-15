package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class PlayerPhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    @Test fun standardFullPlayer() {
        val episode = TestFixtures.episode(title = "How we find our way", description = "A conversation about curiosity and the places it takes us.", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val podcast = TestFixtures.podcast(title = "Curious world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(
            MutableStateFlow(PlayerState(currentEpisode = episode, currentPodcast = podcast, duration = 3_600_000L, position = 120_000L)),
        )
        val downloads = mock(DownloadRepository::class.java)
        `when`(downloads.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(episode.id)).thenReturn(flowOf(false))
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(FullPlayerStyle.STANDARD))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(FullPlayerStyle.STANDARD)
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(false))
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                FullPlayerV2(
                    FullPlayerDependencies(playback, downloads, prefs),
                    FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true),
                    FullPlayerActions({}, {}, {}, {}),
                )
            }
        }
        golden.capture("full-player")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.matrix()
    }
}
