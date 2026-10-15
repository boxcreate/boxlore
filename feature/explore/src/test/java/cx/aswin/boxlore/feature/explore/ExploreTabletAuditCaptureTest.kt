package cx.aswin.boxlore.feature.explore

import android.app.Application
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreTabletAuditCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") == "after")
        val shows = List(15) { TestFixtures.podcast(id = "$it", title = "Curious world $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }
        val episodes = List(15) { TestFixtures.episode(id = "$it", title = "How we understand the universe $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }
        val state = when (scene) {
            "show-search" -> ExploreUiState.Success(searchQuery = "space", isSearching = true, searchResults = shows, alsoFoundResults = shows.takeLast(3))
            "episode-search" -> ExploreUiState.Success(searchQuery = "space", isSearching = true, searchTab = SearchTab.EPISODES, hasPerformedSemanticSearch = true, semanticPodcastResults = shows, semanticSearchResults = episodes)
            "topic" -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, searchResults = shows)
            "empty-topic" -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true)
            else -> ExploreUiState.Success(currentVibe = "What's happening", isSearching = true, isLoading = true)
        }
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                ExploreContent(uiState = state, onSearchQueryChanged = {}, onCategorySelected = {}, onPodcastClick = { _, _, _, _ -> }, onEpisodeClick = { _, _ -> }, onTabSelected = {}, onVibeSelected = { _, _ -> }, onClearVibe = {})
            }
        }
        fixture.capture("explore-$scene")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf("show-search", "episode-search", "topic", "empty-topic", "loading-topic").flatMap { scene ->
            listOf(1280 to 800, 840 to 1280).flatMap { (width, height) -> listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) } }
        }
    }
}
