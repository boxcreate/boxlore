package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.material3.Surface
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
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
class LibraryTabletAuditCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") == "after")
        val vm = mock(LibraryViewModel::class.java)
        val liked = List(12) { index -> ListeningHistoryEntity("$index", "show-1", "How we find our way through the universe $index", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "https://audio.example.invalid/episode.mp3", "Curious world", 120_000L, 3_600_000L, false, isLiked = true, lastPlayedAt = 1_760_000_000_000L) }
        val downloads = List(12) { index -> DownloadedEpisodeEntity("$index", "show-1", "How we find our way through the universe $index", "Episode description", PhoneGoldenRule.ARTWORK_URL, "Curious world", PhoneGoldenRule.ARTWORK_URL, 3_600_000L, 1_760_000_000L, "/tmp/fixture-$index.mp3", index.toLong(), 1_760_000_000_000L, 20_000_000L, status = DownloadedEpisodeEntity.STATUS_COMPLETED) }
        `when`(vm.uiState).thenReturn(MutableStateFlow<LibraryUiState>(LibraryUiState.Success(likedEpisodes = if (scene == "liked-empty") emptyList() else liked, downloadedEpisodes = downloads)))
        `when`(vm.downloadsSortOrder).thenReturn(MutableStateFlow(DownloadsSortOrder.RECENT))
        `when`(vm.showSortOrder).thenReturn(MutableStateFlow(ShowSortOrder.NEWEST))
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.smartDownloadsEnabledStream).thenReturn(flowOf(true))
        `when`(prefs.smartDownloadsMaxEpisodesStream).thenReturn(flowOf(10))
        `when`(prefs.smartDownloadsStorageBudgetStream).thenReturn(flowOf(250L))
        `when`(prefs.smartDownloadsLastSyncTimeStream).thenReturn(flowOf(0L))
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                Surface {
                when (scene) {
                    "downloads" -> DownloadedEpisodesScreen(vm, prefs, onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> }, isPlayerActive = true)
                    "downloaded-show" -> DownloadedShowEpisodesScreen(vm, "show-1", "Curious world", {}, { _, _ -> }, true)
                    else -> LikedEpisodesScreen(vm, {}, {}, { _, _ -> })
                }
                }
            }
        }
        fixture.capture("library-$scene")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf("liked", "liked-empty", "downloads", "downloaded-show").flatMap { scene ->
            listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) } }
        }
    }
}
