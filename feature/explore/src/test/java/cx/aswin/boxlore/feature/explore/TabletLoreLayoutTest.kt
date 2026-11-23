package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_443
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_475
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.DESKTOP
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.FOLD_FLAT
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.FOLD_TABLETOP
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.SPLIT_HALF
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET_PORTRAIT
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W599_H900
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W600_H900
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W900_H479
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W900_H480
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletLoreLayoutTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_280, false))

    @Test
    fun `tight tablet viewport cannot stretch the reading deck and its play action stays visible`() {
        var plays = 0
        val card = LearnCuriosityCard("one", "Why do familiar places feel different when we return?", "Memory and attention change how we experience the world around us.", 8, "How we find our way", "Curious world", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 3_600, null)
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(1_280.dp, 800.dp)) {
                        CuriosityCardStack(listOf(card), rememberSwipeableCardState(card.episodeId, onSwiped = {}), PlayerState(), { action, _ -> if (action == CardAction.Play) plays++ }, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize())
                    }
                }
            }
        }
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
        val bounds = fixture.compose.onNodeWithTag("lore_active_card").getUnclippedBoundsInRoot()
        assertEquals(520f, (bounds.right - bounds.left).value, 1f)
        assertTrue((bounds.bottom - bounds.top).value <= 640f)
        fixture.compose.onNodeWithContentDescription("Play episode").assertIsDisplayed().performClick()
        assertEquals(1, plays)
    }

    @Test
    fun `a long card keeps its question, the end of its explanation and Play in short spaces`() {
        val host = LoreDeckHost(fixture)
        host.state.value = LearnUiState.Success(LoreDeckHost.cards(question = { LONG_QUESTION }, explanation = LONG_EXPLANATION))
        host.render()
        for ((window, pane, scale) in ShortSpaces) {
            host.fontScale.floatValue = scale
            host.moveTo(window, pane)
            val label = "$window pane=$pane scale=$scale"
            host.reveal(host.activeText(LONG_QUESTION))
            host.reveal(host.activeText(LONG_EXPLANATION))
            val play = host.bounds(host.play().assertHasClickAction())
            val root = host.rootBounds()
            assertTrue("$label: Play inside the window", play.bottom <= root.bottom + 0.5.dp && play.top >= root.top)
            val explanation = host.bounds(host.activeText(LONG_EXPLANATION))
            assertTrue("$label: the explanation's last line clears Play ($explanation, $play)", explanation.bottom <= play.top)
            host.reveal(host.activeText(LONG_QUESTION))
            val question = host.bounds(host.activeText(LONG_QUESTION))
            assertTrue("$label: the question's first line is back in view", question.top >= host.bounds(host.activeCard()).top)
        }
    }

    @Test
    fun `a sideways phone gets the cover's card width and phones keep theirs`() {
        val host = LoreDeckHost(fixture)
        host.render()
        host.moveTo(PHONE_LAND)
        val sideways = host.bounds(host.activeCard())
        assertTrue("sideways card ${sideways.right - sideways.left}", (sideways.right - sideways.left) <= 520.dp + 0.5.dp)
        assertEquals("sideways card is centred", PHONE_LAND.widthDp / 2, ((sideways.left + sideways.right) / 2).value, 1f)
        host.moveTo(PHONE)
        val phone = host.bounds(host.activeCard())
        assertEquals(PHONE.widthDp - 2 * 20f, (phone.right - phone.left).value, 1f)
        host.moveTo(COVER_443)
        val cover = host.bounds(host.activeCard())
        assertEquals(COVER_443.widthDp - 2 * 12f, (cover.right - cover.left).value, 1f)
    }

    @Test
    fun `sideways drags on scrolling text skip the card, upward drags only scroll the text`() {
        val host = LoreDeckHost(fixture)
        host.state.value = LearnUiState.Success(LoreDeckHost.cards(question = { LONG_QUESTION }, explanation = LONG_EXPLANATION))
        host.render()
        host.moveTo(PHONE_LAND)
        val cardBefore = host.bounds(host.activeCard())
        val questionBefore = host.bounds(host.activeText(LONG_QUESTION))
        // Start on the question's first line, which is in view; the explanation is still below the fold.
        host.activeText(LONG_QUESTION).performTouchInput { swipeUp(startY = top + 10.dp.toPx(), endY = top - 110.dp.toPx()) }
        host.settle()
        assertEquals("the card stays put", cardBefore.left.value, host.bounds(host.activeCard()).left.value, 0.5f)
        assertTrue("the text scrolled", host.bounds(host.activeText(LONG_QUESTION)).top < questionBefore.top)
        assertEquals(0, dismissals(host))

        host.activeText(LONG_QUESTION).performTouchInput { swipeLeft(startX = centerX, endX = centerX - 200.dp.toPx()) }
        host.settle()
        assertEquals("one skip", 1, dismissals(host))
    }

    @Test
    fun `the active card and its rest position survive fold and rotation changes both ways`() {
        val host = LoreDeckHost(fixture)
        host.render()
        val tabletopLower = BoxloreLayoutBounds(80f, 336f, FOLD_TABLETOP.widthDp, FOLD_TABLETOP.heightDp)
        for ((window, pane) in listOf(FOLD_FLAT to null, FOLD_TABLETOP to tabletopLower, FOLD_FLAT to null, PHONE to null, PHONE_LAND to null, PHONE to null)) {
            host.moveTo(window, pane)
            host.activeText(host.questionOf(0)).assertExists()
            val card = host.bounds(host.activeCard())
            val root = host.rootBounds()
            assertEquals("$window: the card rests centred", ((root.left + root.right) / 2).value, ((card.left + card.right) / 2).value, 1f)
        }
        assertEquals(0, dismissals(host))
    }

    @Test
    fun `on tall tablet panes the header and card sit together in the middle`() {
        val host = LoreDeckHost(fixture)
        host.render()
        host.moveTo(TABLET)
        val landscapeGap = gap(host)
        for (window in listOf(BoxloreWindowLayout(600f, 960f), TABLET_PORTRAIT, DESKTOP)) {
            host.moveTo(window)
            val card = host.bounds(host.activeCard())
            val root = host.rootBounds()
            val third = (root.bottom - root.top) / 3
            val middle = (card.top + card.bottom) / 2
            assertTrue("$window: card centre $middle in the middle third of $root", middle >= root.top + third && middle <= root.bottom - third)
            assertEquals("$window: header to card gap", landscapeGap, gap(host), 1f)
            assertTrue("$window: the card grows no taller than 760dp", (card.bottom - card.top).value <= 760.5f)
            host.play().assertIsDisplayed()
        }
        // Each pair crosses a gate both ways; the active card and the gap under the header stay.
        for (pair in listOf(listOf(TABLET, TABLET_PORTRAIT, TABLET), listOf(W599_H900, W600_H900, W599_H900), listOf(W900_H479, W900_H480, W900_H479))) {
            val gaps = pair.map { window ->
                host.moveTo(window)
                host.activeText(host.questionOf(0)).assertExists()
                gap(host)
            }
            gaps.forEach { assertEquals("$pair: header to card gap", gaps.first(), it, 1f) }
        }
    }

    @Test
    fun `skipped and queued cards leave the window before the next card takes their place`() {
        val host = LoreDeckHost(fixture)
        // A phone-like density, where the old fixed travel stopped short on wide windows.
        host.density.floatValue = 2.625f
        host.render()
        for ((step, pair) in listOf(TABLET to true, PHONE_LAND to false).withIndex()) {
            val (window, skip) = pair
            // The ViewModel is a mock, so the deck moves on only when the test says so.
            host.show(LearnUiState.Success(LoreDeckHost.cards().drop(step)))
            host.moveTo(window)
            val question = host.questionOf(step)
            val before = dismissalsAndQueues(host)
            fixture.compose.mainClock.autoAdvance = false
            if (skip) {
                host.onActiveCard(androidx.compose.ui.test.hasContentDescription(host.string(R.string.lore_action_skip))).performClick()
            } else {
                host.activeText(question).performTouchInput { swipeRight(startX = centerX, endX = centerX + 200.dp.toPx()) }
            }
            var lastLeft = Float.NaN
            var lastRight = Float.NaN
            var frames = 0
            while (dismissalsAndQueues(host) == before && frames < 200) {
                fixture.compose.onNode(androidx.compose.ui.test.hasText(question), useUnmergedTree = true).fetchSemanticsNode().let {
                    lastLeft = it.positionInRoot.x
                    lastRight = it.positionInRoot.x + it.size.width
                }
                fixture.compose.mainClock.advanceTimeByFrame()
                frames++
            }
            fixture.compose.mainClock.autoAdvance = true
            assertEquals("$window: one card left the deck", before + 1, dismissalsAndQueues(host))
            val root = fixture.compose.onNode(hasTestTag(LoreDeckHost.ROOT)).fetchSemanticsNode()
            val rootLeft = root.positionInRoot.x
            val rootRight = rootLeft + root.size.width
            if (skip) {
                assertTrue("$window: a skipped card's text ended at $lastRight, the window starts at $rootLeft", lastRight <= rootLeft)
            } else {
                assertTrue("$window: a queued card's text started at $lastLeft, the window ends at $rootRight", lastLeft >= rootRight)
            }
        }
    }

    private fun dismissalsAndQueues(host: LoreDeckHost) =
        mockingDetails(host.viewModel).invocations.count { it.method.name == "dismissCuriosity" }

    private fun gap(host: LoreDeckHost): Float =
        (host.bounds(host.activeCard()).top - host.bounds(fixture.compose.onNode(hasTestTag(LORE_DECK_HEADER_TAG))).bottom).value

    private fun LoreDeckHost.questionOf(index: Int) = "Why do familiar places feel different when we return? ($index)"

    private fun dismissals(host: LoreDeckHost) = mockingDetails(host.viewModel).invocations.count { it.method.name == "dismissCuriosity" }

    companion object {
        const val LONG_QUESTION = "Why did a single faulty relay in a quiet substation leave fifty million people in the dark for days, and what did it teach engineers?"
        const val LONG_EXPLANATION = "Follow the minute-by-minute cascade of the 2003 blackout, from an overgrown tree line in Ohio to the control rooms that missed the warning signs, and the rules that changed afterwards."

        /** The tabletop lower half beside the rail (a detail open above), a sideways phone, a split-screen half and the cover at 130% text. */
        private val ShortSpaces = listOf(
            Triple(FOLD_TABLETOP, BoxloreLayoutBounds(80f, 336f, 932f, 650f), 1f),
            Triple(PHONE_LAND, null, 1f),
            Triple(SPLIT_HALF, null, 1f),
            Triple(COVER_475, null, 1.3f),
        )
    }
}
