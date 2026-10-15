package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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
class InfoPhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)
    private val podcast = TestFixtures.podcast(id = "1", title = "Curious world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        .copy(description = "Stories about the world around us, and the ideas that change how we see it.")
    private val episode = TestFixtures.episode(
        id = "episode-1",
        title = "How we find our way",
        podcastId = "1",
        podcastTitle = podcast.title,
        description = "A conversation about curiosity, memory, and the places we call home.",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
    ).copy(podcastImageUrl = PhoneGoldenRule.ARTWORK_URL)

    @Test fun podcastInfo() {
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, listOf(episode), true)))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        render("podcast-info") { PodcastInfoScreen("1", model, {}, { _, _, _ -> }, {}, {}) }
    }

    @Test fun episodeInfo() {
        val model = mock(EpisodeInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(
            MutableStateFlow<EpisodeInfoUiState>(
                EpisodeInfoUiState.Success(episode, "1", podcast.title, relatedEpisodesLoading = false, similarEpisodesLoading = false),
            ),
        )
        `when`(model.likedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.completedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(model.isDownloading(episode.id)).thenReturn(flowOf(false))
        `when`(model.showRemoveDownloadDialog).thenReturn(MutableStateFlow(false))
        render("episode-info") {
            EpisodeInfoScreen(episode.id, episode.title, episode.description, PhoneGoldenRule.ARTWORK_URL, episode.audioUrl, episode.duration, "1", podcast.title, model, {}, {}, {}, {})
        }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        golden.prepare()
        golden.setContent {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), content = content)
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
