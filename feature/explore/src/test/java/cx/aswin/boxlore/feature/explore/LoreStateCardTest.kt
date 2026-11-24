package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.DpRect
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_443
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_475
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.DESKTOP
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.FOLD_TABLETOP
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.SPLIT_HALF
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET_PORTRAIT
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lore's states share one header, and the caught-up and failed panels keep their buttons reachable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoreStateCardTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_280, false))

    private val host = LoreDeckHost(fixture)

    @Test
    fun `caught up and failed panels keep their buttons reachable above the mini player in short spaces`() {
        host.playerState.value = PlayerState(currentEpisode = Episode(id = "playing", title = "Playing", description = "", audioUrl = ""))
        host.state.value = LearnUiState.CaughtUp()
        host.render(miniPlayer = true)
        for ((window, pane) in ShortSpaces) {
            host.show(LearnUiState.CaughtUp())
            host.moveTo(window, pane)
            val refreshes = calls("refresh")
            tap(host.string(R.string.lore_check_new_cards))
            assertEquals("$window: Check for new cards", refreshes + 1, calls("refresh"))
            val opened = host.historyOpened
            tap(host.string(R.string.lore_open_history))
            assertEquals("$window: Open Lore history", opened + 1, host.historyOpened)

            host.show(LearnUiState.Error(UiText.res(R.string.lore_error_load_failed)))
            tap(host.string(R.string.explore_try_again))
            assertEquals("$window: Try again", refreshes + 2, calls("refresh"))
            fixture.compose.onNodeWithContentDescription(host.string(R.string.lore_history)).performClick()
            assertEquals("$window: the header's History works when loading failed", opened + 2, host.historyOpened)
        }
    }

    @Test
    fun `on a phone the caught up panel fits without scrolling and pulling down checks again`() {
        host.state.value = LearnUiState.CaughtUp()
        host.render()
        val range = fixture.compose.onNode(panelScroller()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), 0.5f)

        val refreshes = calls("refresh")
        fixture.compose.onNode(panelScroller()).performTouchInput { swipeDown() }
        host.settle()
        assertEquals(refreshes + 1, calls("refresh"))
    }

    @Test
    fun `the header stays in one place for every state on phones and tablets`() {
        host.render()
        // Tall tablet panes centre the header with the deck, so every state's panel must take the deck's frame.
        for (window in listOf(PHONE, TABLET, TABLET_PORTRAIT, BoxloreWindowLayout(600f, 960f), DESKTOP)) {
            host.moveTo(window)
            // Every state after every other, both ways round.
            val bounds = (States + States.reversed()).map { state ->
                host.show(state)
                header()
            }
            bounds.forEach { assertEquals("$window header", bounds.first(), it) }
        }
    }

    @Test
    fun `the caught up panel survives rotation both ways, and loading again leaves the header still`() {
        host.state.value = LearnUiState.CaughtUp()
        host.render()
        for (window in listOf(PHONE, PHONE_LAND, PHONE)) {
            host.moveTo(window)
            fixture.compose.onNodeWithText(host.string(R.string.lore_caught_up_title)).assertExists()
        }
        host.show(LearnUiState.Error(UiText.res(R.string.lore_error_load_failed)))
        val failed = header()
        host.show(LearnUiState.Success(LoreDeckHost.cards()))
        assertEquals(failed, header())
        host.activeCard().assertIsDisplayed()
    }

    @Test
    fun `loading shows a card-shaped placeholder exactly where the first card lands`() {
        host.render()
        for (window in listOf(PHONE, COVER_475, PHONE_LAND, TABLET)) {
            host.moveTo(window)
            host.show(LearnUiState.Success(LoreDeckHost.cards()))
            val card = host.bounds(host.activeCard())
            host.show(LearnUiState.Loading)
            fixture.compose.onNode(hasTestTag(LoreDeckHost.ACTIVE_CARD), useUnmergedTree = true).assertDoesNotExist()
            // Screen readers still hear that the deck is loading.
            fixture.compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertExists()
            val placeholder = host.bounds(fixture.compose.onNode(hasTestTag(LORE_CARD_SKELETON_TAG), useUnmergedTree = true))
            assertEquals("$window left", card.left.value, placeholder.left.value, 1f)
            assertEquals("$window top", card.top.value, placeholder.top.value, 1f)
            assertEquals("$window right", card.right.value, placeholder.right.value, 1f)
            assertEquals("$window bottom", card.bottom.value, placeholder.bottom.value, 1f)
        }
        for (window in listOf(PHONE, TABLET, PHONE)) {
            host.moveTo(window)
            fixture.compose.onNode(hasTestTag(LORE_CARD_SKELETON_TAG), useUnmergedTree = true).assertExists()
        }
    }

    private fun header(): DpRect = host.bounds(fixture.compose.onNode(hasTestTag(LORE_DECK_HEADER_TAG)))

    private fun panelScroller() = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
        hasAnyDescendant(hasText(host.string(R.string.lore_caught_up_title)))

    private fun tap(text: String) {
        val node = fixture.compose.onNodeWithText(text)
        node.performScrollTo()
        val button = host.bounds(node)
        val root = host.rootBounds()
        assertTrue("$text sits inside the window", button.top >= root.top && button.bottom <= root.bottom)
        node.performClick()
        host.settle()
    }

    private fun calls(name: String) = mockingDetails(host.viewModel).invocations.count { it.method.name == name }

    private companion object {
        val States = listOf(
            LearnUiState.Loading,
            LearnUiState.Success(LoreDeckHost.cards()),
            LearnUiState.CaughtUp(),
            LearnUiState.Error(UiText.res(R.string.lore_error_load_failed)),
        )

        /** A sideways phone, a split-screen half, the Fold 8 cover and a tabletop lower half beside the rail. */
        val ShortSpaces = listOf(
            PHONE_LAND to null,
            SPLIT_HALF to null,
            COVER_443 to null,
            FOLD_TABLETOP to BoxloreLayoutBounds(80f, 336f, 932f, 650f),
        )
    }
}
