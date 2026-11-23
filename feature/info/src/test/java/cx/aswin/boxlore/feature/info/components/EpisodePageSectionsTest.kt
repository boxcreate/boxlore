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
import org.junit.Assert.assertFalse
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
    private val showTip = mutableStateOf(false)
    private var tipsDismissed = 0

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
    fun `links show four rows until opened, sit in two columns on wide pages, and open once per tap`() {
        render()
        val list = fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST)
        list.performScrollToKey("links")
        assertEquals(0, fixture.compose.onAllNodesWithText("Further reading 4").fetchSemanticsNodes().size)
        fixture.compose.onNodeWithText("Show all links").performClick()
        fixture.compose.waitForIdle()
        fixture.compose.walkWindows(window, BoxloreTestWindows.W599_H900, BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900) { size ->
            list.performScrollToKey("links")
            assertTrue("still open at $size", fixture.compose.onAllNodesWithText("Show fewer links").fetchSemanticsNodes().isNotEmpty())
        }
        fixture.compose.runOnIdle { window.value = BoxloreTestWindows.UPRIGHT_BOOK }
        list.performScrollToKey("links")
        // The episode's own page leads; a two-line name beside a one-line one keeps the row even.
        val first = fixture.compose.onNodeWithText(WEBSITE_TITLE).getBoundsInRoot()
        val second = fixture.compose.onNodeWithText("Further reading 1").getBoundsInRoot()
        assertEquals("two columns side by side", first.top.value, second.top.value, 1f)
        assertEquals("cells in a row share one height", first.bottom.value - first.top.value, second.bottom.value - second.top.value, 1f)
        fixture.compose.onNodeWithText("Further reading 1").performClick()
        fixture.compose.waitForIdle()
        val opened = org.robolectric.Shadows.shadowOf(fixture.compose.activity.application).nextStartedActivity
        assertEquals("https://site1.example.org", opened.data.toString())
        assertEquals(null, org.robolectric.Shadows.shadowOf(fixture.compose.activity.application).nextStartedActivity)
    }

    @Test
    fun `off the phone layout the cover, Play row, cards and More from share one column's edges`() {
        render()
        fixture.compose.walkWindows(window, BoxloreTestWindows.UPRIGHT_BOOK, BoxloreTestWindows.TABLET) { size ->
            val list = fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST)
            list.performScrollToKey("hero")
            val art = fixture.compose.onNodeWithTag(EPISODE_HERO_ART_TAG).getBoundsInRoot()
            val rail = fixture.compose.onNodeWithTag(EpisodeInfoTags.RAIL).getBoundsInRoot()
            list.performScrollToKey("about")
            val about = fixture.compose.onNodeWithTag(EpisodeInfoTags.ABOUT).getBoundsInRoot()
            list.performScrollToKey("more_from_podcast")
            val moreFrom = fixture.compose.onNodeWithTag(EpisodeInfoTags.MORE_FROM).getBoundsInRoot()
            assertEquals("cover and About start together at $size", about.left.value + 16f, art.left.value, 1f)
            assertEquals("Play row and About end together at $size", about.right.value - 16f, rail.right.value, 1f)
            assertEquals("More from and About end together at $size", about.right.value, moreFrom.right.value, 1f)
            assertTrue("one reading column at $size", about.right.value - art.left.value <= 720f)
        }
    }

    @Test
    fun `the people in an episode run to the About card's edges instead of stopping at its text margin`() {
        val people = listOf("Amara Okafor" to "host", "Lukas Brandt" to "guest", "Mei Chen" to "editor").map { (name, role) -> cx.aswin.boxlore.core.model.Person(name, role) }
        render(richState().let { it.copy(episode = it.episode.copy(persons = people)) })
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("about")
        val about = fixture.compose.onNodeWithTag(EpisodeInfoTags.ABOUT).getBoundsInRoot()
        val row = fixture.compose.onNodeWithTag(cx.aswin.boxlore.feature.info.EPISODE_PEOPLE_ROW_TAG).getBoundsInRoot()
        // The card sits 16dp inside its block on each side.
        assertEquals(about.left.value + 16f, row.left.value, 1f)
        assertEquals(about.right.value - 16f, row.right.value, 1f)
    }

    @Test
    fun `More from opens the show from its header only, and says See all only above listed episodes`() {
        val related = List(3) { TestFixtures.episode(id = "related-$it", title = "Episode ${10 + it}", podcastId = "1", podcastTitle = "Curious world") }
        val state = MutableStateFlow(richState().copy(relatedEpisodes = related))
        render(state)
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("more_from_podcast")
        assertTrue(fixture.compose.onAllNodesWithText("See all", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("no second Explore button", 0, fixture.compose.onAllNodesWithText("Explore podcast").fetchSemanticsNodes().size)
        fixture.compose.runOnIdle { state.value = richState() }
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey("more_from_podcast")
        assertTrue(fixture.compose.onAllNodesWithText("No other episodes available").fetchSemanticsNodes().isNotEmpty())
        assertEquals("no See all above an empty list", 0, fixture.compose.onAllNodesWithText("See all", useUnmergedTree = true).fetchSemanticsNodes().size)
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
        assertFalse("Mark played is whole", cutOff(textLayout(fixture.compose.onNode(hasText("Mark played"), useUnmergedTree = true))))
    }

    @Test
    fun `beside Archived and a season number on a regular phone, Mark played moves down whole instead of shrinking`() {
        val rich = richState().let { it.copy(episode = it.episode.copy(seasonNumber = 3, episodeNumber = 14, duration = 4_380)) }
        render(rich, archived = setOf(EPISODE_ID))
        val markPlayed = fixture.compose.onNode(hasText("Mark played"), useUnmergedTree = true)
        assertFalse("Mark played is whole", cutOff(textLayout(markPlayed)))
        assertFalse("Archived is whole", cutOff(textLayout(fixture.compose.onNode(hasText("Archived"), useUnmergedTree = true))))
        assertTrue("inside the page", markPlayed.getBoundsInRoot().right.value <= BoxloreTestWindows.PHONE.widthDp)
    }

    @Test
    fun `the Mark played tip points at the button without moving the Play row, then goes once`() {
        render()
        val railTop = fixture.compose.onNodeWithTag(EpisodeInfoTags.RAIL).getBoundsInRoot().top
        fixture.compose.mainClock.autoAdvance = false
        fixture.compose.runOnIdle { showTip.value = true }
        repeat(10) {
            fixture.compose.mainClock.advanceTimeBy(50)
            fixture.compose.waitForIdle()
        }
        assertTrue("the tip shows", fixture.compose.onAllNodesWithText(TIP).fetchSemanticsNodes().isNotEmpty())
        assertEquals("the Play row stays put", railTop, fixture.compose.onNodeWithTag(EpisodeInfoTags.RAIL).getBoundsInRoot().top)
        fixture.compose.mainClock.advanceTimeBy(4_000)
        fixture.compose.waitForIdle()
        assertEquals("dismissed once", 1, tipsDismissed)
        assertEquals(0, fixture.compose.onAllNodesWithText(TIP).fetchSemanticsNodes().size)
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

    private fun render(state: EpisodeInfoUiState.Success = richState(), archived: Set<String> = emptySet()) = render(MutableStateFlow(state), archived)

    private fun render(state: MutableStateFlow<EpisodeInfoUiState.Success>, archived: Set<String> = emptySet()) {
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(model, state, archived = MutableStateFlow(archived))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            EpisodeInfoScreen(
                                EPISODE_ID, "", "", "", "", 0, "1", "Curious world", model, {}, {}, {}, {},
                                showMarkPlayedTip = showTip.value,
                                onMarkPlayedTipDismissed = { tipsDismissed++ },
                            )
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
            EpisodeLink("https://curiousworld.example/14", "curiousworld.example", EpisodeLinkKind.WEBSITE, title = WEBSITE_TITLE),
            EpisodeLink("https://www.youtube.com/watch?v=abc", "youtube.com", EpisodeLinkKind.VIDEO, platform = "YouTube"),
        ) + (1..4).map { EpisodeLink("https://site$it.example.org", "site$it.example.org", EpisodeLinkKind.ARTICLE, title = "Further reading $it") }
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
        const val TIP = "Mark played to move this episode out of your unplayed list."
        const val WEBSITE_TITLE = "Episode 14 notes: the maps, the places and the full transcript"
    }
}

/** German on regular phones: the details wrap instead of scrolling, and the pills move below whole rather than shrink. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeDetailsLongTextTest(private val configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test
    fun `long German details stay still and push the pills below`() {
        val model = mock(EpisodeInfoViewModel::class.java)
        val episode = TestFixtures.episode(id = "de", title = "Wie wir nach Hause finden", podcastId = "1", podcastTitle = "Neugierige Welt", duration = 4_380, publishedDate = 1_731_000_000L)
            .copy(seasonNumber = 3, episodeNumber = 14)
        stubEpisodeInfoViewModel(model, MutableStateFlow(EpisodeInfoUiState.Success(episode, "1", "Neugierige Welt", relatedEpisodesLoading = false, similarEpisodesLoading = false)), archived = MutableStateFlow(setOf("de")))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        val window = BoxloreWindowLayout(configuration.width.toFloat(), configuration.height.toFloat())
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window, LocalBoxlorePaneWidth provides window.widthDp) {
                BoxLoreTheme(dynamicColor = false) {
                    Surface { EpisodeInfoScreen("de", "", "", "", "", 0, "1", "Neugierige Welt", model, {}, {}, {}, {}) }
                }
            }
        }
        fixture.compose.waitForIdle()
        // The details wrap as plain text: a ticker needs one unwrapped line that runs past its room.
        val detailsLayout = textLayout(fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG))
        assertTrue("the details wrap", detailsLayout.layoutInput.softWrap)
        assertFalse("the details are whole", cutOff(detailsLayout))
        val markPlayedNode = fixture.compose.onNode(hasText("Als gehört markieren"), useUnmergedTree = true)
        assertFalse("Mark played is whole", cutOff(textLayout(markPlayedNode)))
        val details = fixture.compose.onNodeWithTag(EPISODE_DETAILS_TAG).getBoundsInRoot()
        val markPlayed = markPlayedNode.getBoundsInRoot()
        if (configuration.fontScale > 1f) assertTrue("the pills sit below the details", markPlayed.top >= details.bottom)
        assertTrue("the details stay inside the page", details.right.value <= window.widthDp)
        assertTrue("Mark played stays inside the page", markPlayed.right.value <= window.widthDp)
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = listOf(
            arrayOf(PhoneGoldenConfiguration(360, 800, false, fontScale = 1.3f, locale = "de")),
            arrayOf(PhoneGoldenConfiguration(412, 915, false, locale = "de")),
        )
    }
}

/** True when [layout] lost text: more lines than it may show, or an ellipsis on a line. */
internal fun cutOff(layout: TextLayoutResult): Boolean =
    layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any(layout::isLineEllipsized)
