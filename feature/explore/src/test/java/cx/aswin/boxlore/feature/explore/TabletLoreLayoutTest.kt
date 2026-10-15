package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletLoreLayoutTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    @Test
    fun `tight tablet viewport cannot stretch the reading deck and its play action stays visible`() {
        var plays = 0
        val card = LearnCuriosityCard("one", "Why do familiar places feel different when we return?", "Memory and attention change how we experience the world around us.", 8, "How we find our way", "Curious world", PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 3_600, null)
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1_280f, 800f)) {
                BoxLoreTheme(dynamicColor = false) {
                    CuriosityCardStack(listOf(card), rememberSwipeableCardState(card.episodeId, onSwiped = {}), PlayerState(), { action, _ -> if (action == CardAction.Play) plays++ }, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize())
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
}
