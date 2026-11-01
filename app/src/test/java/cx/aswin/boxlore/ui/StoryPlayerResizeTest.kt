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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ListeningDayEpisode
import cx.aswin.boxlore.core.model.ListeningPlayedShow
import cx.aswin.boxlore.feature.history.story.STORY_FRAME_TAG
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

    /** How far the frame is scrolled, or null when it doesn't scroll. */
    private fun SemanticsNodeInteraction.scroll(): Float? = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke()

    private companion object {
        /** A phone on its side: the frame at its 0.8 floor is taller than the window, so it scrolls. */
        val SHORT = DpSize(800.dp, 360.dp)

        /** Tall enough for the whole frame. */
        val TALL = DpSize(800.dp, 1_200.dp)
    }
}
