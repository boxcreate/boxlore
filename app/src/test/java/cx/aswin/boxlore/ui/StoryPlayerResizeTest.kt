package cx.aswin.boxlore.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.story.STORY_FRAME_TAG
import cx.aswin.boxlore.feature.history.story.STORY_SIDE_NEXT_TAG
import cx.aswin.boxlore.feature.history.story.STORY_SIDE_PAUSE_TAG
import cx.aswin.boxlore.feature.history.story.STORY_SIDE_PREVIOUS_TAG
import cx.aswin.boxlore.feature.history.story.StoryCard
import cx.aswin.boxlore.feature.history.story.StoryLogic
import cx.aswin.boxlore.feature.history.story.StoryPlayer
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The open story keeps its place while the window changes: scrolled down in a short window (a phone
 * on its side), it stops scrolling once the window is tall enough for the whole frame, and scrolls
 * back to the same place when the window is short again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryPlayerResizeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(SHORT)

    @Test fun `the story keeps its scroll position when a short window grows tall and shrinks again`() {
        val listening = (1..10).map { day -> ListeningDayEpisode(LocalDate.of(2026, 10, day).toEpochDay(), "dd-$day", "dd", 40 * 60_000L, 1, 0L, eveningMs = 40 * 60_000L) }
        val played = listOf(ListeningPlayedShow("dd", "Darknet Diaries", null, "True Crime", 0L, 0L))
        val story = requireNotNull(StoryLogic.build(YearMonth.of(2026, 10), emptyList(), listening, played, LocalDate.of(2026, 11, 2), ZoneOffset.UTC))
        compose.setContent {
            CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(darkTheme = true, dynamicColor = false) {
                    // The share card is the last: it never moves on by itself while the test runs.
                    Box(Modifier.requiredSize(window.value)) { StoryPlayer(story, StoryCard.SHARE, {}, {}, {}) }
                }
            }
        }
        val frame = compose.onNodeWithTag(STORY_FRAME_TAG)
        frame.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 200f) }
        compose.waitForIdle()
        val scrolled = requireNotNull(frame.scroll())
        assertTrue(scrolled > 0f)

        compose.runOnIdle { window.value = TALL }
        compose.waitForIdle()
        assertNull(frame.scroll())

        compose.runOnIdle { window.value = SHORT }
        compose.waitForIdle()
        assertEquals(scrolled, requireNotNull(frame.scroll()), 0.5f)
    }

    @Test fun `a card that waited while cut off keeps waiting once it fits, and the next card moves on by itself`() {
        val story = story()
        window.value = COVER_SHORT
        render(story, StoryCard.HELLO)
        compose.mainClock.autoAdvance = false
        waitPastCard()
        compose.onNodeWithContentDescription("Card 1 of", substring = true).assertExists()

        // Unfolded to see it bigger: the card the listener was reading stays until they move on.
        compose.runOnIdle { window.value = PHONE_TALL }
        waitPastCard()
        compose.onNodeWithContentDescription("Card 1 of", substring = true).assertExists()
        compose.runOnIdle { window.value = COVER_SHORT }
        waitPastCard()
        compose.onNodeWithContentDescription("Card 1 of", substring = true).assertExists()

        // A tap still moves on, and a card that fits from its start moves on by itself.
        compose.onNodeWithContentDescription("Next card").performSemanticsAction(SemanticsActions.OnClick)
        frames(3)
        compose.onNodeWithContentDescription("Card 2 of", substring = true).assertExists()
        compose.runOnIdle { window.value = PHONE_TALL }
        frames(3)
        compose.onNodeWithContentDescription("Next card").performSemanticsAction(SemanticsActions.OnClick)
        frames(3)
        compose.onNodeWithContentDescription("Card 3 of", substring = true).assertExists()
        waitPastCard()
        compose.onNodeWithContentDescription("Card 4 of", substring = true).assertExists()
    }

    @Test fun `on tablets the frame grows only a little and previous, pause and next sit beside it`() {
        val story = story()
        window.value = DpSize(800.dp, 1_280.dp)
        render(story, StoryCard.HELLO)
        compose.mainClock.autoAdvance = false
        frames(3)
        assertTrue(compose.onNodeWithTag(STORY_FRAME_TAG).fetchSemanticsNode().boundsInRoot.width <= 400f * 1.15f + 0.5f)

        compose.runOnIdle { window.value = DpSize(1_280.dp, 800.dp) }
        frames(3)
        compose.onNodeWithTag(STORY_SIDE_NEXT_TAG).performClick()
        frames(3)
        compose.onNodeWithContentDescription("Card 2 of", substring = true).assertExists()
        compose.onNodeWithTag(STORY_SIDE_PAUSE_TAG).assertContentDescriptionEquals("Pause story").performClick()
        waitPastCard()
        compose.onNodeWithContentDescription("Card 2 of", substring = true).assertExists()
        // Moving to another card plays it: its entrance draws and the pause button is back.
        compose.onNodeWithTag(STORY_SIDE_PREVIOUS_TAG).performClick()
        frames(3)
        compose.onNodeWithContentDescription("Card 1 of", substring = true).assertExists()
        compose.onNodeWithTag(STORY_SIDE_PAUSE_TAG).assertContentDescriptionEquals("Pause story").performClick()
        frames(3)

        // A phone has no room beside the frame: no buttons, the card is kept, and the story no longer
        // stays paused with no way to resume.
        compose.runOnIdle { window.value = PHONE_TALL }
        frames(3)
        compose.onNodeWithTag(STORY_SIDE_PAUSE_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Card 1 of", substring = true).assertExists()
        compose.runOnIdle { window.value = DpSize(1_280.dp, 800.dp) }
        frames(3)
        compose.onNodeWithTag(STORY_SIDE_PAUSE_TAG).assertContentDescriptionEquals("Pause story")
        compose.runOnIdle { window.value = PHONE_TALL }
        waitPastCard()
        compose.onNodeWithContentDescription("Card 2 of", substring = true).assertExists()
    }

    private fun story() = requireNotNull(
        StoryLogic.build(
            YearMonth.of(2026, 10),
            emptyList(),
            (1..10).map { day -> ListeningDayEpisode(LocalDate.of(2026, 10, day).toEpochDay(), "dd-$day", "dd", 40 * 60_000L, 1, 0L, eveningMs = 40 * 60_000L) },
            listOf(ListeningPlayedShow("dd", "Darknet Diaries", null, "True Crime", 0L, 0L)),
            LocalDate.of(2026, 11, 2),
            ZoneOffset.UTC,
        ),
    )

    private fun render(story: cx.aswin.boxlore.feature.history.story.MonthStory, card: StoryCard) {
        // A paused clock from the start: a running one would play every card through before the test looks.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalEchoMotion provides false,
                LocalBoxloreWindowLayout provides BoxloreWindowLayout(window.value.width.value, window.value.height.value),
            ) {
                BoxLoreTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.requiredSize(window.value)) { StoryPlayer(story, card, {}, {}, {}) }
                }
            }
        }
    }

    /** Runs the paused clock past a card's 8 seconds, a frame at a time. */
    private fun waitPastCard() = frames(CARD_MS.toInt() / 100 + 10, stepMs = 100)

    private fun frames(count: Int, stepMs: Long = 16) {
        repeat(count) {
            compose.mainClock.advanceTimeBy(stepMs)
            compose.waitForIdle()
        }
    }

    /** How far the frame is scrolled, or null when it doesn't scroll. */
    private fun SemanticsNodeInteraction.scroll(): Float? = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()

    private companion object {
        /** A phone on its side: the frame at its 0.8 floor is taller than the window, so it scrolls. */
        val SHORT = DpSize(800.dp, 360.dp)

        /** Tall enough for the whole frame. */
        val TALL = DpSize(800.dp, 1_200.dp)

        /** A Fold 8 cover with its bars: the frame at its fit would be under 0.8, so it scrolls. */
        val COVER_SHORT = DpSize(443.dp, 580.dp)

        /** How long a card shows before moving on by itself. */
        const val CARD_MS = 8_000L

        /** A regular phone: the whole frame fits. */
        val PHONE_TALL = DpSize(400.dp, 890.dp)
    }
}
