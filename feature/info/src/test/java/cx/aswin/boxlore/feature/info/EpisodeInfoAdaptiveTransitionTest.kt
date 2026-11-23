package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.info.components.EPISODE_HERO_ART_TAG
import cx.aswin.boxlore.feature.info.components.EPISODE_PAGE_SKELETON_TAG
import cx.aswin.boxlore.feature.info.components.EPISODE_SKELETON_ART_TAG
import cx.aswin.boxlore.feature.info.components.EPISODE_SKELETON_RAIL_TAG
import cx.aswin.boxlore.feature.info.components.EpisodeLinkStatusTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives one live episode page opened from a shared link through its "finding this episode",
 * "not found" and loaded states across the shared window gates (600dp width, 480dp height, 840dp
 * landscape split, portrait/landscape and a fold posture change) in both directions, then recreates
 * it with saved state. The state lives in the page's ViewModel, so no window change or recreation
 * starts the feed read again.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeInfoAdaptiveTransitionTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(COMPACT)
    private val pane = mutableStateOf<BoxloreLayoutBounds?>(null)
    private val uiState = MutableStateFlow<EpisodeInfoUiState>(EpisodeInfoUiState.Loading)
    private val model = mock(EpisodeInfoViewModel::class.java)
    private val archived = MutableStateFlow(emptySet<String>())
    private lateinit var restoration: StateRestorationTester
    private val openedShows = mutableListOf<String>()
    private var backs = 0

    @Test
    fun `finding the episode survives window changes and recreation without a second load`() {
        render()
        uiState.value = EpisodeInfoUiState.ReadingFeed
        fixture.compose.waitForIdle()

        acrossWindowChanges { readingFeedShown() }
        recreate(FOLD_TABLETOP)
        readingFeedShown()
        acrossWindowChanges { readingFeedShown() }

        // One load for the first composition and one for the recreated page; window changes never
        // reload, and the ViewModel skips a repeat of a running load (EpisodeLoadRequestsTest).
        val loads = mockingDetails(model).invocations.count { it.method.name == "loadEpisode" && it.arguments.first() == LINK_ID }
        assertEquals(2, loads)
    }

    @Test
    fun `not found keeps its actions across window changes and calls them once`() {
        render()
        uiState.value = EpisodeInfoUiState.ReadingFeed
        fixture.compose.waitForIdle()
        window.value = TABLET
        uiState.value = EpisodeInfoUiState.NotFound("920666")
        fixture.compose.waitForIdle()

        acrossWindowChanges { notFoundShown(withShow = true) }
        fixture.compose.onNodeWithText("View show").performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf("920666"), openedShows)

        recreate(COMPACT)
        notFoundShown(withShow = true)
        fixture.compose.onNodeWithText("Back").performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(listOf("920666"), openedShows)
    }

    @Test
    fun `not found without a show offers only Back, and a later answer shows the episode`() {
        render()
        uiState.value = EpisodeInfoUiState.NotFound("")
        fixture.compose.waitForIdle()
        acrossWindowChanges { notFoundShown(withShow = false) }

        // Recovery: the page loads the episode (a retry with the show found it in the feed).
        val episode = TestFixtures.episode(id = LINK_ID, title = "Found in the feed", podcastId = "920666", podcastTitle = "Curious world")
        uiState.value = EpisodeInfoUiState.Success(episode, "920666", "Curious world", relatedEpisodesLoading = false, similarEpisodesLoading = false)
        fixture.compose.waitForIdle()
        acrossWindowChanges {
            fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.NOT_FOUND).assertDoesNotExist()
            val titles = fixture.compose.onAllNodesWithText("Found in the feed", useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("the loaded episode is shown", titles.isNotEmpty())
        }
        assertEquals(0, backs)
    }

    @Test
    fun `a link to another of the episode's IDs shows the phone's download across window changes`() {
        render()
        // After the page's own stubs, which answer every other ID with "not downloaded".
        `when`(model.isDownloaded("-9")).thenReturn(flowOf(true))
        `when`(model.isDownloading("-9")).thenReturn(flowOf(false))
        val episode = TestFixtures.episode(id = "-9", title = "Stored episode", podcastId = "920666", podcastTitle = "Curious world")
        uiState.value = EpisodeInfoUiState.Success(
            episode,
            "920666",
            "Curious world",
            relatedEpisodesLoading = false,
            similarEpisodesLoading = false,
            requestedEpisodeId = LINK_ID,
        )
        fixture.compose.waitForIdle()

        acrossWindowChanges {
            assertTrue("the stored download is shown", labelled("Remove download"))
            assertTrue("the link's own ID isn't read for downloads", !labelled("Download"))
        }
    }

    @Test
    fun `the episode page offers no Archive button and only marks an archived episode, in every layout`() {
        render()
        val episode = TestFixtures.episode(id = LINK_ID, title = "A trailer", podcastId = "920666", podcastTitle = "Curious world")
        uiState.value = EpisodeInfoUiState.Success(episode, "920666", "Curious world", relatedEpisodesLoading = false, similarEpisodesLoading = false)
        fixture.compose.waitForIdle()
        acrossWindowChanges {
            assertTrue("no Archive button", !labelled("Archive") && !labelled("Unarchive"))
            assertTrue("not marked yet", !labelled("Archived"))
        }

        // Archived from the show page: this page only says so.
        archived.value = setOf(LINK_ID)
        acrossWindowChanges {
            assertTrue("marked archived", labelled("Archived"))
            assertTrue("still no Unarchive button", !labelled("Unarchive"))
            assertTrue("Mark played is unchanged", labelled("Mark played"))
        }
        recreate(FOLD_TABLETOP)
        assertTrue(labelled("Archived"))
    }

    @Test
    fun `the top bar clears side system buttons and sits where the show page puts Back and Share`() {
        window.value = BoxloreTestWindows.PHONE
        render()
        showEpisode()
        // The show page's top bar: 48dp buttons 4dp from each edge, so their centres are 28dp in.
        assertEquals(28f, centreX(BACK), 0.5f)
        assertEquals(BoxloreTestWindows.PHONE.widthDp - 28f, centreX(SHARE), 0.5f)

        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        fixture.compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND) { size ->
            if (size == BoxloreTestWindows.PHONE_LAND) {
                val insets = SystemInsets.SIDEWAYS_3_BUTTON
                assertTrue("Back clears the camera cutout", bounds(BACK).left.value >= insets.cutoutStart)
                assertTrue("Share clears the side buttons", bounds(SHARE).right.value <= size.widthDp - insets.navEnd)
            }
        }
        fixture.compose.onNodeWithContentDescription(SHARE).performClick()
        fixture.compose.waitForIdle()
        assertTrue("Share is tappable sideways and opens the sheet", shareSheetShown())
    }

    @Test
    fun `in tabletop the lower half starts right at the top bar, and flat keeps the status bar`() {
        window.value = BoxloreTestWindows.FOLD_FLAT
        render()
        showEpisode()
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, SystemInsets.TABLETOP_STATUS) }
        fixture.compose.waitForIdle()
        val buttonTop = (64f - 48f) / 2
        val lowerHalf = BoxloreLayoutBounds(0f, 336f, BoxloreTestWindows.FOLD_FLAT.widthDp, BoxloreTestWindows.FOLD_FLAT.heightDp)
        listOf(
            Triple(BoxloreTestWindows.FOLD_FLAT, null, SystemInsets.TABLETOP_STATUS.statusTop + buttonTop),
            Triple(BoxloreTestWindows.FOLD_TABLETOP, lowerHalf, buttonTop),
            Triple(BoxloreTestWindows.FOLD_FLAT, null, SystemInsets.TABLETOP_STATUS.statusTop + buttonTop),
        ).forEach { (size, bounds, expectedTop) ->
            fixture.compose.runOnIdle {
                pane.value = bounds
                window.value = size
            }
            fixture.compose.waitForIdle()
            assertEquals("Back's top at $size", expectedTop, bounds(BACK).top.value, 0.5f)
        }
    }

    @Test
    fun `the hero changes form at the 600 and 480 gates and keeps the open title, and Play works after every step`() {
        window.value = BoxloreTestWindows.PHONE
        render()
        showEpisode(LONG_TITLE)
        val walk = arrayOf(
            BoxloreTestWindows.PHONE_LAND,
            BoxloreTestWindows.PHONE,
            BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.W600_H900,
            BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.W900_H479,
            BoxloreTestWindows.W900_H480,
            BoxloreTestWindows.W900_H479,
            BoxloreTestWindows.PHONE,
        )
        fixture.compose.walkWindows(window, *walk) { size ->
            val short = size.heightDp < 480f
            if (short) assertTrue("Play is on the first screen at $size", playBounds().bottom.value <= size.heightDp)
            val rail = fixture.compose.onNodeWithTag(EpisodeInfoTags.RAIL).getBoundsInRoot()
            if (short || size.widthDp >= 600f) {
                assertTrue("the Play row stops at a comfortable width at $size", rail.right.value - rail.left.value <= 480.5f)
            } else {
                assertTrue("the phone Play row spans the page at $size", rail.right.value - rail.left.value > 480.5f || size.widthDp < 530f)
            }
        }

        fixture.compose.onNodeWithContentDescription(FULL_TITLE).performClick()
        fixture.compose.waitForIdle()
        var plays = 0
        fixture.compose.walkWindows(window, *walk) { size ->
            assertTrue("the open title stays open at $size", labelled(SHOW_LESS))
            // The Play row is its own list item, or inside the hero's end column on a wide page.
            val twoColumn = size.widthDp >= 600f && size.heightDp >= 480f
            fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToKey(if (twoColumn) "hero" else "rail")
            fixture.compose.onNode(hasText(PLAY) and hasClickAction()).performScrollTo().performClick()
            plays++
            val calls = mockingDetails(model).invocations.count { it.method.name == "onMainActionClick" }
            assertEquals("one play per tap at $size", plays, calls)
            fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToIndex(0)
        }
    }

    @Test
    fun `in a tabletop half Play is on the first screen, and the page keeps its scroll through the fold`() {
        window.value = BoxloreTestWindows.FOLD_FLAT
        render()
        showEpisode()
        // Enough below the hero that the page can scroll in every layout.
        val chapters = List(8) { cx.aswin.boxlore.core.model.Chapter(it * 300.0, "Chapter ${it + 1}") }
        uiState.value = (uiState.value as EpisodeInfoUiState.Success).copy(chapters = chapters)
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToIndex(1)
        fixture.compose.waitForIdle()
        val lowerHalf = BoxloreLayoutBounds(0f, 336f, BoxloreTestWindows.FOLD_FLAT.widthDp, BoxloreTestWindows.FOLD_FLAT.heightDp)
        listOf(BoxloreTestWindows.FOLD_TABLETOP to lowerHalf, BoxloreTestWindows.FOLD_FLAT to null).forEach { (size, bounds) ->
            fixture.compose.runOnIdle {
                pane.value = bounds
                window.value = size
            }
            fixture.compose.waitForIdle()
            assertTrue("the list stays scrolled at $size", listScroll() > 0f)
        }
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).performScrollToIndex(0)
        fixture.compose.runOnIdle {
            pane.value = lowerHalf
            window.value = BoxloreTestWindows.FOLD_TABLETOP
        }
        fixture.compose.waitForIdle()
        assertTrue("Play is on the first screen of the lower half", playBounds().bottom.value <= lowerHalf.height)
    }

    @Test
    fun `an open Share sheet stays open through window changes, a reload and recreation`() {
        window.value = BoxloreTestWindows.W599_H900
        render()
        showEpisode()
        fixture.compose.onNodeWithContentDescription(SHARE).performClick()
        fixture.compose.waitForIdle()
        assertTrue(shareSheetShown())
        fixture.compose.walkWindows(
            window,
            BoxloreTestWindows.W600_H900,
            BoxloreTestWindows.W599_H900,
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(841f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreTestWindows.FOLD_FLAT,
            BoxloreTestWindows.FOLD_TABLETOP,
            BoxloreTestWindows.FOLD_FLAT,
        ) { size -> assertTrue("the sheet stays open at $size", shareSheetShown()) }

        // A reload passes through Loading; the sheet doesn't close with the loaded page.
        val loaded = uiState.value
        uiState.value = EpisodeInfoUiState.Loading
        fixture.compose.waitForIdle()
        assertTrue("open while loading", shareSheetShown())
        uiState.value = loaded
        fixture.compose.waitForIdle()
        assertTrue("open after loading", shareSheetShown())

        recreate(BoxloreTestWindows.W600_H900)
        assertTrue("open after recreation", shareSheetShown())
    }

    @Test
    fun `the loading outline sits where the loaded page will, with Back working and no second load`() {
        listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.UPRIGHT_BOOK).forEach { size ->
            window.value = size
            uiState.value = EpisodeInfoUiState.Loading
            if (size == BoxloreTestWindows.PHONE) render() else fixture.compose.waitForIdle()
            val skeletonArt = fixture.compose.onNodeWithTag(EPISODE_SKELETON_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
            val skeletonRail = fixture.compose.onNodeWithTag(EPISODE_SKELETON_RAIL_TAG, useUnmergedTree = true).getBoundsInRoot()
            showEpisode("How we find our way home again after a long winter away")
            val art = fixture.compose.onNodeWithTag(EPISODE_HERO_ART_TAG).getBoundsInRoot()
            val rail = fixture.compose.onNodeWithTag(EpisodeInfoTags.RAIL).getBoundsInRoot()
            assertEquals("art at $size", art, skeletonArt)
            assertEquals("rail start at $size", rail.left.value, skeletonRail.left.value, 0.5f)
            assertEquals("rail width at $size", rail.right.value - rail.left.value, skeletonRail.right.value - skeletonRail.left.value, 0.5f)
        }

        uiState.value = EpisodeInfoUiState.Loading
        fixture.compose.walkWindows(window, BoxloreTestWindows.W599_H900, BoxloreTestWindows.W600_H900, BoxloreTestWindows.W599_H900) { size ->
            fixture.compose.onNodeWithTag(EPISODE_PAGE_SKELETON_TAG, useUnmergedTree = true).assertExists()
        }
        val loads = mockingDetails(model).invocations.count { it.method.name == "loadEpisode" }
        assertEquals("window changes while loading don't load again", 1, loads)
        fixture.compose.onNodeWithContentDescription(BACK).performClick()
        fixture.compose.waitForIdle()
        assertEquals("Back works while loading", 1, backs)
    }

    @Test
    fun `a load error offers Try again and Back in short windows and keeps them across the 600dp gate`() {
        window.value = BoxloreTestWindows.PHONE_LAND
        render()
        uiState.value = EpisodeInfoUiState.Error
        fixture.compose.waitForIdle()
        var retries = 0
        fixture.compose.walkWindows(
            window,
            BoxloreTestWindows.PHONE_LAND,
            BoxloreTestWindows.SPLIT_HALF,
            BoxloreTestWindows.W599_H900,
            BoxloreTestWindows.W600_H900,
            BoxloreTestWindows.W599_H900,
        ) { size ->
            fixture.compose.onNodeWithText("Couldn’t load this episode").assertExists()
            fixture.compose.onNodeWithText(TRY_AGAIN).performScrollTo().performClick()
            retries++
            assertEquals("one retry per tap at $size", retries, mockingDetails(model).invocations.count { it.method.name == "retry" })
        }
        fixture.compose.onNodeWithText(BACK).performScrollTo().performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, backs)
    }

    private fun playBounds() = fixture.compose.onNode(hasText(PLAY) and hasClickAction()).getBoundsInRoot()

    private fun listScroll(): Float =
        fixture.compose.onNodeWithTag(EpisodeInfoTags.LIST).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun shareSheetShown() = fixture.compose.onAllNodesWithText(SHARE_SHEET_TITLE).fetchSemanticsNodes().isNotEmpty()

    private fun centreX(label: String) = bounds(label).let { (it.left.value + it.right.value) / 2 }

    private fun bounds(label: String) = fixture.compose.onNodeWithContentDescription(label).getBoundsInRoot()

    private fun showEpisode(title: String = "How we find our way home") {
        val episode = TestFixtures.episode(id = LINK_ID, title = title, podcastId = "920666", podcastTitle = "Curious world")
        uiState.value = EpisodeInfoUiState.Success(episode, "920666", "Curious world", relatedEpisodesLoading = false, similarEpisodesLoading = false)
        fixture.compose.waitForIdle()
    }

    private fun labelled(label: String): Boolean =
        fixture.compose.onAllNodes(hasContentDescription(label) or hasText(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun readingFeedShown() {
        fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.READING_FEED).assertIsDisplayed()
        fixture.compose.onNodeWithText("Finding this episode").assertIsDisplayed()
        fixture.compose.onNodeWithText("Checking the show’s feed. This can take a moment.").assertIsDisplayed()
        fixture.compose.onNodeWithText("Back").assertDoesNotExist()
    }

    private fun notFoundShown(withShow: Boolean) {
        fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.NOT_FOUND).assertIsDisplayed()
        fixture.compose.onNodeWithText("Episode not found").assertIsDisplayed()
        fixture.compose.onNodeWithText("Back").assertIsDisplayed()
        if (withShow) {
            fixture.compose.onNodeWithText("It may have been removed from the show.").assertIsDisplayed()
            fixture.compose.onNodeWithText("View show").assertIsDisplayed()
        } else {
            // Without a show the link may name an ID only another phone holds.
            fixture.compose.onNodeWithText("It may have been removed, or this link may not open on this phone.").assertIsDisplayed()
            fixture.compose.onNodeWithText("View show").assertDoesNotExist()
        }
    }

    /** Moves the same live page across every gate and back, asserting after each step. */
    private fun acrossWindowChanges(assertion: () -> Unit) {
        val start = window.value
        (TRANSITIONS + start).forEachIndexed { step, size ->
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            try {
                assertion()
            } catch (failure: AssertionError) {
                throw AssertionError("After window step $step ($size): ${failure.message}", failure)
            }
        }
    }

    private fun recreate(to: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = to }
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
    }

    private fun render() {
        stubEpisodeInfoViewModel(model, uiState, moreLikeThisOn = false, archived = archived)
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            val bounds = pane.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides (bounds?.width ?: size.widthDp),
                LocalBoxlorePaneBounds provides bounds,
            ) {
                Box(Modifier.width((bounds?.width ?: size.widthDp).dp).height((bounds?.height ?: size.heightDp).dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            EpisodeInfoScreen(
                                episodeId = LINK_ID,
                                episodeTitle = "",
                                episodeDescription = "",
                                episodeImageUrl = "",
                                episodeAudioUrl = "",
                                episodeDuration = 0,
                                podcastId = "",
                                podcastTitle = "Podcast",
                                viewModel = model,
                                onBack = { backs++ },
                                onPodcastClick = { openedShows += it },
                                onEpisodeClick = {},
                                onPlay = {},
                            )
                        }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private companion object {
        const val LINK_ID = "4503599627370500"
        const val BACK = "Back"
        const val SHARE = "Share episode"
        const val TRY_AGAIN = "Try again"
        const val SHARE_SHEET_TITLE = "Share this episode"
        const val FULL_TITLE = "Show full title"
        const val SHOW_LESS = "Show less"
        const val PLAY = "Play"
        const val LONG_TITLE = "How we find our way home again after a long winter away: memory, maps and the places that keep us, " +
            "with stories from listeners in Lagos, Leipzig and Lisbon about the streets they still walk in their sleep, " +
            "the kitchens they can still smell and the maps they drew from memory when they could not go back"
        val COMPACT = BoxloreWindowLayout(599f, 800f)
        val TABLET = BoxloreWindowLayout(1_280f, 800f)
        val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(
                    BoxloreFoldState.HalfOpened,
                    BoxloreFoldOrientation.Horizontal,
                    BoxloreLayoutBounds(0f, 410f, 673f, 431f),
                    true,
                    true,
                ),
            ),
        )

        /** Each gate is crossed in both directions; the walk ends where it started. */
        val TRANSITIONS = listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(600f, 960f),
            BoxloreWindowLayout(960f, 600f),
            BoxloreWindowLayout(600f, 960f),
            FOLD_FLAT,
            FOLD_TABLETOP,
            FOLD_FLAT,
        )
    }
}
