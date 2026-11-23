package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.info.components.EPISODE_PLAY_BUTTON_TAG
import cx.aswin.boxlore.feature.info.components.METADATA_ACTION_CHIP_TAG
import cx.aswin.boxlore.feature.info.components.METADATA_CHIPS_ROW_TAG
import cx.aswin.boxlore.feature.info.components.METADATA_INFO_CHIP_TAG
import cx.aswin.boxlore.feature.info.components.PODCAST_INFO_TOP_BAR_TAG
import cx.aswin.boxlore.feature.info.components.SUBSCRIBED_CHECK_TAG
import cx.aswin.boxlore.feature.info.components.SUBSCRIBE_BUTTON_TAG
import cx.aswin.boxlore.feature.info.components.WIDE_EPISODE_CARD_TAG
import cx.aswin.boxlore.feature.info.sections.DESCRIPTION_CHEVRON_TAG
import cx.aswin.boxlore.feature.info.sections.LOCKED_FEED_ROW_TAG
import cx.aswin.boxlore.feature.info.sections.SHOW_HEADER_COVER_TAG
import cx.aswin.boxlore.feature.info.sections.SHOW_HEADER_HORIZONTAL_TAG
import cx.aswin.boxlore.feature.info.sections.SHOW_HEADER_STACKED_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The regular show page's frame, header, toolbar, cards and menus across window sizes, insets,
 * fold postures and recreation, on one live page in both directions across each gate.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowPageAdaptiveLayoutTest {
    @get:Rule val rule = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private val page = ShowPageTestFixture(rule)
    private val compose get() = rule.compose

    @Test
    fun `Back, Share and More stay clear of side system buttons and a cutout, and phones keep their place`() {
        page.render()
        // Portrait phones: 48dp buttons 4dp from each edge, as before.
        assertEquals(28f, page.bounds(hasContentDescription(BACK)).center.x, 0.5f)
        assertEquals(BoxloreTestWindows.PHONE.widthDp - 28f, page.bounds(hasContentDescription(MORE)).center.x, 0.5f)

        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
        val insets = SystemInsets.SIDEWAYS_3_BUTTON
        compose.walkWindows(page.window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_LAND, BoxloreTestWindows.PHONE_LAND) { size ->
            assertTrue("Back clears the cutout at $size", page.bounds(hasContentDescription(BACK)).left >= insets.cutoutStart)
            assertTrue("More clears the side buttons at $size", page.bounds(hasContentDescription(MORE)).right <= size.widthDp - insets.navEnd)
            assertTrue("Share clears the side buttons at $size", page.bounds(hasContentDescription(SHARE)).right <= size.widthDp - insets.navEnd)
        }
        compose.onNodeWithContentDescription(MORE).performClick()
        compose.onNodeWithText("Mark all as played").assertExists()
    }

    @Test
    fun `in a tabletop lower half the top bar starts at the half's top, and flat keeps the status bar`() {
        page.window.value = BoxloreTestWindows.FOLD_FLAT
        page.render()
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.TABLETOP_STATUS) }
        compose.waitForIdle()
        val lowerHalf = BoxloreLayoutBounds(0f, 336f, BoxloreTestWindows.FOLD_FLAT.widthDp, BoxloreTestWindows.FOLD_FLAT.heightDp)
        val flatTop = page.bounds(hasContentDescription(MORE)).top
        val barTop = flatTop - SystemInsets.TABLETOP_STATUS.statusTop
        assertTrue("flat leaves room for the status bar", barTop >= 0f)
        listOf(
            Triple(BoxloreTestWindows.FOLD_TABLETOP, lowerHalf, barTop),
            Triple(BoxloreTestWindows.FOLD_FLAT, null, flatTop),
            Triple(BoxloreTestWindows.FOLD_TABLETOP, lowerHalf, barTop),
        ).forEach { (size, bounds, top) ->
            page.resize(size, bounds)
            assertEquals("More's top at $size", top, page.bounds(hasContentDescription(MORE)).top, 0.5f)
        }
    }

    @Test
    fun `the header takes its form from the page's own size and keeps the open description both ways`() {
        page.render()
        cover(SHOW_HEADER_STACKED_TAG, 180f)
        compose.onNodeWithText(page.podcast.description!!.take(20), substring = true).performClick()
        val walk = arrayOf(
            BoxloreTestWindows.PHONE_LAND to (SHOW_HEADER_HORIZONTAL_TAG to 112f),
            BoxloreTestWindows.PHONE to (SHOW_HEADER_STACKED_TAG to 180f),
            BoxloreTestWindows.COVER_443 to (SHOW_HEADER_STACKED_TAG to 152f),
            BoxloreTestWindows.W900_H479 to (SHOW_HEADER_HORIZONTAL_TAG to 112f),
            BoxloreTestWindows.W900_H480 to (SHOW_HEADER_HORIZONTAL_TAG to 208f),
            BoxloreTestWindows.W900_H479 to (SHOW_HEADER_HORIZONTAL_TAG to 112f),
            BoxloreTestWindows.W599_H900 to (SHOW_HEADER_STACKED_TAG to 180f),
            BoxloreTestWindows.W600_H900 to (SHOW_HEADER_HORIZONTAL_TAG to 208f),
            BoxloreTestWindows.W599_H900 to (SHOW_HEADER_STACKED_TAG to 180f),
        )
        walk.forEach { (size, expected) ->
            page.resize(size)
            compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(0)
            cover(expected.first, expected.second)
            // The open description shows the creator's picks in every form.
            compose.onNodeWithText("History Snacks").assertExists()
        }
        // Sideways, Subscribe is on the first screen.
        page.resize(BoxloreTestWindows.PHONE_LAND)
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(0)
        assertTrue(page.bounds(hasTestTag(SUBSCRIBE_BUTTON_TAG)).bottom <= BoxloreTestWindows.PHONE_LAND.heightDp)
    }

    @Test
    fun `on a wide pane the top bar keeps to the reading column, and Subscribe and Play stop at their caps`() {
        page.window.value = BoxloreTestWindows.TABLET
        page.render()
        val bar = page.bounds(hasTestTag(PODCAST_INFO_TOP_BAR_TAG))
        assertEquals(720f, bar.width, 0.5f)
        assertEquals(BoxloreTestWindows.TABLET.widthDp / 2, bar.center.x, 0.5f)
        // A 688dp card puts everything on one line.
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(2)
        assertTrue("a wide card at 1280x800", compose.onAllNodesWithTag(WIDE_EPISODE_CARD_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        page.resize(BoxloreTestWindows.TABLET_PORTRAIT)
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        val wideSubscribe = page.bounds(hasTestTag(SUBSCRIBE_BUTTON_TAG)).width
        assertTrue("Subscribe stops at 320 ($wideSubscribe)", wideSubscribe <= 320.5f)
        page.resize(BoxloreTestWindows.W600_H900)
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(2)
        compose.onAllNodesWithTag(WIDE_EPISODE_CARD_TAG, useUnmergedTree = true).assertCountEquals(0)
        val play = page.bounds(hasTestTag(EPISODE_PLAY_BUTTON_TAG), unmerged = true).width
        assertTrue("Play stops at 220 ($play)", play <= 220.5f)

        // Back to a phone: the stacked card and a Subscribe under its cap, as before.
        page.resize(BoxloreTestWindows.PHONE)
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        val subscribe = page.bounds(hasTestTag(SUBSCRIBE_BUTTON_TAG)).width
        assertTrue("phone Subscribe keeps its width ($subscribe)", subscribe in 150f..320f)
        compose.onAllNodesWithTag(WIDE_EPISODE_CARD_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `information chips are outlined and do nothing, action chips act, and a long Support message is capped`() {
        page.render()
        val info = compose.onAllNodesWithTag(METADATA_INFO_CHIP_TAG, useUnmergedTree = true).onFirst().fetchSemanticsNode()
        assertTrue("an information chip has no click", SemanticsActions.OnClick !in info.config)
        val action = compose.onAllNodesWithTag(METADATA_ACTION_CHIP_TAG).onFirst().fetchSemanticsNode()
        assertTrue("an action chip clicks", SemanticsActions.OnClick in action.config)
        // The release chip (worked out off the main thread) reads in sentence case: this show's last
        // episode is long past. The genre shows its translated name.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("No new episodes")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Society", substring = true).assertExists()
        compose.onNodeWithTag(METADATA_CHIPS_ROW_TAG).performScrollToNode(hasText(page.podcast.fundingMessage!!))
        assertTrue(page.bounds(hasText(page.podcast.fundingMessage!!), unmerged = true).width <= 240f)
    }

    @Test
    fun `the description card says it holds more, counts the creator's picks and opens them`() {
        page.uiState.value = (page.uiState.value as PodcastInfoUiState.Success).copy(podcast = page.podcast.copy(isLocked = true))
        page.render()
        compose.onNodeWithTag(DESCRIPTION_CHEVRON_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("The creator recommends 3 shows").assertExists()
        compose.onNodeWithText("History Snacks").assertDoesNotExist()
        compose.onNodeWithText("The creator recommends 3 shows").performClick()
        compose.onNodeWithText("History Snacks").assertExists()
        val lock = page.bounds(hasTestTag(LOCKED_FEED_ROW_TAG))
        assertTrue("the lock row is a full touch target", lock.height >= 47.5f)
        // The locked-feed explanation stays open through recreation.
        compose.onNodeWithTag(LOCKED_FEED_ROW_TAG).performClick()
        page.restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Feed locked").assertExists()
    }

    @Test
    fun `Sort and view says the order, changes it once, and keeps the view choices out of More`() {
        page.render()
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        compose.onNode(hasContentDescription(SORT) and stateIs("Newest first")).assertExists()
        compose.onNodeWithContentDescription(SORT).performClick()
        compose.onNodeWithText("Oldest first").performClick()
        compose.waitForIdle()
        verify(page.model, times(1)).toggleSort()
        compose.onNode(hasContentDescription(SORT) and stateIs("Oldest first")).assertExists()
        // Choosing the order already shown changes nothing.
        compose.onNodeWithContentDescription(SORT).performClick()
        compose.onNodeWithText("Oldest first").performClick()
        verify(page.model, times(1)).toggleSort()

        compose.onNodeWithContentDescription(SORT).performClick()
        assertEquals(false, compose.menuRowChecked(HIDE_COMPLETED))
        compose.onNode(hasText(HIDE_COMPLETED) and hasClickAction()).performClick()
        verify(page.model, times(1)).toggleHideCompleted()
        assertEquals("an on/off row keeps the menu open and shows it on", true, compose.menuRowChecked(HIDE_COMPLETED))

        // The open menu survives recreation.
        page.restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasText(HIDE_COMPLETED) and hasClickAction()).assertExists()
    }

    @Test
    fun `More holds the show's actions in groups, stays open through recreation and names its buttons on long-press`() {
        page.render()
        compose.onNodeWithContentDescription(SHARE).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNode(hasText(SHARE), useUnmergedTree = true).assertExists()

        compose.onNodeWithContentDescription(MORE).performClick()
        listOf("Mark all as played", "Playback for this show", "Change tag / genre", "Hide show everywhere").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Hide completed episodes").assertDoesNotExist()
        compose.onNodeWithText("Episode titles only").assertDoesNotExist()
        page.restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Mark all as played").assertExists()
    }

    @Test
    fun `Subscribed shows its check on phones`() {
        page.render()
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        compose.onNodeWithTag(SUBSCRIBED_CHECK_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `in the other half of an upright book the toolbar keeps its full labels, and flat keeps the selection`() {
        page.window.value = BoxloreTestWindows.UPRIGHT_BOOK
        page.pane.value = BoxloreLayoutBounds(375f, 0f, 750f, 832f)
        page.render()
        compose.onNodeWithText("Episode 0").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Play selected episodes").assertExists()
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        compose.onNodeWithTag(SUBSCRIBED_CHECK_TAG, useUnmergedTree = true).assertExists()
        page.resize(BoxloreTestWindows.UPRIGHT_BOOK.copy(folds = emptyList()))
        compose.onNodeWithContentDescription("Play selected episodes").assertExists()
        page.resize(BoxloreTestWindows.UPRIGHT_BOOK, BoxloreLayoutBounds(375f, 0f, 750f, 832f))
        compose.onNodeWithContentDescription("Play selected episodes").assertExists()
    }

    @Test
    fun `the RSS question stays open through recreation`() {
        page.uiState.value = (page.uiState.value as PodcastInfoUiState.Success).copy(
            podcast = page.podcast.copy(sourceType = Podcast.SOURCE_RSS, feedUrl = "https://feeds.example/f.xml"),
        )
        page.render()
        compose.onNodeWithTag(PODCAST_INFO_EPISODE_LIST_TAG).performScrollToIndex(1)
        compose.onNodeWithContentDescription("Episode notifications").performClick()
        compose.onNodeWithText("Turn on episode notifications?").assertExists()
        page.restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Turn on episode notifications?").assertExists()
        compose.onNodeWithText("Keep notifications off").performClick()
        compose.onNodeWithText("Turn on episode notifications?").assertDoesNotExist()
    }

    private fun cover(tag: String, size: Float) {
        compose.onNodeWithTag(tag).assertExists()
        assertEquals("cover in $tag", size, page.bounds(hasTestTag(SHOW_HEADER_COVER_TAG)).width, 0.5f)
    }

    private fun stateIs(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private companion object {
        const val BACK = "Back"
        const val SHARE = "Share show"
        const val MORE = "More options"
        const val SORT = "Sort and view"
        const val HIDE_COMPLETED = "Hide completed episodes"
    }
}
