package cx.aswin.boxlore.feature.info.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.ShowNotes
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.info.EpisodeInfoScreen
import cx.aswin.boxlore.feature.info.EpisodeInfoTags
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState
import cx.aswin.boxlore.feature.info.EpisodeInfoViewModel
import cx.aswin.boxlore.feature.info.episodeHeaderColor
import cx.aswin.boxlore.feature.info.stubEpisodeInfoViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The episode page's sections: one heading style, About before Chapters, one expand row, show names on recommendations, a still details line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodePageSectionsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)

    @Test
    fun `sections share one heading style and About comes before Chapters`() {
        render()
        val list = fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST)
        list.performScrollToKey("about")
        val about = fixture.compose.onNode(hasText("About this episode") and isHeading())
        val chapters = fixture.compose.onNode(hasText("Chapters") and isHeading())
        assertTrue("About sits above Chapters", about.getBoundsInRoot().top < chapters.getBoundsInRoot().top)
        val aboutStyle = style(about)
        val chaptersStyle = style(chapters)
        list.performScrollToKey("links")
        val linksStyle = style(fixture.compose.onNode(hasText("Episode links") and isHeading()))
        list.performScrollToKey("more_like_this")
        val tryTheseStyle = style(fixture.compose.onNode(hasText("Try these episodes") and isHeading()))
        listOf(chaptersStyle, linksStyle, tryTheseStyle).forEach { assertEquals("same heading style", aboutStyle, it) }
    }

    @Test
    fun `chapters open with a full-height row that stays open across the 600dp gate`() {
        render()
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("chapters")
        val expander = fixture.compose.onNodeWithText("Show all chapters")
        val bounds = expander.getBoundsInRoot()
        assertTrue("48dp tall", bounds.bottom.value - bounds.top.value >= 47.5f)
        assertEquals(0, fixture.compose.onAllNodesWithText("Chapter 6", substring = true).fetchSemanticsNodes().size)
        expander.performClick()
        fixture.compose.waitForIdle()
        fixture.compose.walkWindows(window, BoxloreTestWindows.W599_H900, BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900) {
            fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("chapters")
            assertTrue("still open at $it", fixture.compose.onAllNodesWithText("Show fewer chapters").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun `recommended episodes from other shows name their show`() {
        render()
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("more_like_this")
        assertTrue(fixture.compose.onAllNodesWithText("Atlas Hour").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `in English on a regular phone the details and Mark played share a line`() {
        render()
        val details = fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG).getBoundsInRoot()
        val markPlayed = fixture.compose.onNode(hasText("Mark played")).getBoundsInRoot()
        val detailsCentre = (details.top.value + details.bottom.value) / 2
        val pillCentre = (markPlayed.top.value + markPlayed.bottom.value) / 2
        assertEquals(detailsCentre, pillCentre, 2f)
    }

    @Test
    fun `the top bar takes the page colour, then the container once content runs under it`() {
        val scheme = resolveFixedThemeColorScheme("violet", darkTheme = false, surfaceStyle = SurfaceStyles.STANDARD)
        assertEquals(scheme.surface.copy(alpha = 0f), episodeHeaderColor(scheme, 0f, contentUnder = false))
        assertEquals(scheme.surface, episodeHeaderColor(scheme, 1f, contentUnder = false))
        assertEquals(scheme.surfaceContainer, episodeHeaderColor(scheme, 1f, contentUnder = true))
        // A fade from the page colour, never from transparent black.
        assertNotEquals(0f, episodeHeaderColor(scheme, 0.5f, contentUnder = false).red + episodeHeaderColor(scheme, 0.5f, contentUnder = false).green)
    }

    private fun style(node: SemanticsNodeInteraction) = textLayout(node).layoutInput.style.let { it.fontSize to it.fontWeight }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    private fun render() {
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(model, MutableStateFlow(richState()))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            EpisodeInfoScreen(EPISODE_ID, "", "", "", "", 0, "1", "Curious world", model, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun richState(): EpisodeInfoUiState.Success {
        val episode = TestFixtures.episode(id = EPISODE_ID, title = "How we find our way home", podcastId = "1", podcastTitle = "Curious world", publishedDate = 1_731_000_000L)
        val chapters = List(6) { Chapter(it * 300.0, "Chapter ${it + 1}") }
        val links = listOf(
            EpisodeLink("https://curiousworld.example/14", "curiousworld.example", EpisodeLinkKind.WEBSITE),
            EpisodeLink("https://www.youtube.com/watch?v=abc", "youtube.com", EpisodeLinkKind.VIDEO, platform = "YouTube"),
        )
        val notes = ShowNotes("Amara talks with a cartographer about the places we grew up in. ".repeat(6), links, chapters, "")
        val similar = listOf("History Snacks", "Atlas Hour").mapIndexed { i, show ->
            TestFixtures.episode(id = "similar-$i", title = "Episode $i", podcastId = "${i + 2}", podcastTitle = show)
        }
        return EpisodeInfoUiState.Success(
            episode = episode,
            podcastId = "1",
            podcastTitle = "Curious world",
            relatedEpisodesLoading = false,
            similarEpisodes = similar,
            similarEpisodesLoading = false,
            showNotes = notes,
            chapters = chapters,
        )
    }

    private companion object {
        const val EPISODE_ID = "episode-sections"
    }
}

/** German at 130% text on a small phone: the details wrap instead of scrolling, and the pills move below. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeDetailsLongTextTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test
    fun `long German details stay still and push the pills below`() {
        val model = mock(EpisodeInfoViewModel::class.java)
        val episode = TestFixtures.episode(id = "de", title = "Wie wir nach Hause finden", podcastId = "1", podcastTitle = "Neugierige Welt", duration = 4_380, publishedDate = 1_731_000_000L)
            .copy(seasonNumber = 3, episodeNumber = 14)
        stubEpisodeInfoViewModel(model, MutableStateFlow(EpisodeInfoUiState.Success(episode, "1", "Neugierige Welt", relatedEpisodesLoading = false, similarEpisodesLoading = false)), archived = MutableStateFlow(setOf("de")))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        val window = BoxloreWindowLayout(360f, 800f)
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window, LocalBoxlorePaneWidth provides window.widthDp) {
                BoxLoreTheme(dynamicColor = false) {
                    Surface { EpisodeInfoScreen("de", "", "", "", "", 0, "1", "Neugierige Welt", model, {}, {}, {}, {}) }
                }
            }
        }
        fixture.compose.waitForIdle()
        // Nothing moves on its own any more: no ticker after a few seconds.
        val before = fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG).getBoundsInRoot()
        fixture.compose.mainClock.advanceTimeBy(5_000)
        assertEquals(before, fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG).getBoundsInRoot())
        val details = fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG).getBoundsInRoot()
        val markPlayed = fixture.compose.onNode(hasText("Als gehört markieren")).getBoundsInRoot()
        assertTrue("the pills sit below the details", markPlayed.top >= details.bottom)
        assertTrue("the details stay inside the page", details.right.value <= 360f)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = listOf(arrayOf(PhoneGoldenConfiguration(360, 800, false, fontScale = 1.3f, locale = "de")))
    }
}
