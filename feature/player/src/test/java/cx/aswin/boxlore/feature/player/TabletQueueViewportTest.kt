package cx.aswin.boxlore.feature.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.SameShowContinuationState
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletQueueViewportTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(600, 960, false))

    @Test
    fun `expanded continuation and smart queue recovery remain reachable in a short support viewport`() {
        var additions = 0
        var enables = 0
        val episodes = List(5) { TestFixtures.episode(id = "next-$it", title = "Next discovery $it") }
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Box(Modifier.width(420.dp).height(200.dp)) {
                    QueueSheetContent(
                        queue = emptyList(),
                        currentPodcast = TestFixtures.podcast(),
                        colorScheme = MaterialTheme.colorScheme,
                        actions = QueueSheetActions({}, {}, {}, onAddSameShowEpisodes = { additions++ }, onEnableSmartQueue = { enables++ }),
                        smartQueueEnabled = false,
                        sameShowContinuation = SameShowContinuationState(true, "Curious world", 5, episodes),
                        showHeader = false,
                        scrollContinuation = true,
                    )
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        try {
            fixture.compose.onNodeWithText("Preview 5 upcoming episodes").performScrollTo().performClick()
            fixture.compose.onNodeWithText("Add next 5 from this show").performScrollTo().performClick()
            assertEquals(1, additions)
            fixture.compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
            fixture.compose.onNodeWithText("Turn on").performScrollTo().performClick()
            assertEquals(1, enables)
        } finally {
            fixture.compose.mainClock.autoAdvance = false
        }
    }
}
