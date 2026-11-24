package cx.aswin.boxlore.feature.info.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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

/** The Bonus, Trailer and Video tag looks the same on the episode page and in the show's list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeTypeChipTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(412, 915, false))

    @Test
    fun `the list tag keeps its size, the page tag is larger, and video adds the camera`() {
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column {
                    EpisodeTypeChip("Bonus", modifier = Modifier.testTag("small"))
                    EpisodeTypeChip("Bonus", size = EpisodeTypeChipSize.Medium, modifier = Modifier.testTag("medium"))
                    EpisodeTypeChip("Video", modifier = Modifier.testTag("text-only"))
                    EpisodeTypeChip("Video", video = true, modifier = Modifier.testTag("video"))
                    Box(Modifier.width(60.dp)) {
                        EpisodeTypeChip("Bonusfolge für Abonnenten", size = EpisodeTypeChipSize.Medium, modifier = Modifier.testTag("long"))
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
        val small = bounds("small")
        val medium = bounds("medium")
        // The list's tag keeps the height it had before it was shared: a labelSmall line and 3dp above and below.
        assertEquals(20f, small.second, 0.5f)
        assertTrue("The page's tag is larger", medium.second > small.second && medium.first > small.first)
        assertTrue("The camera makes room for itself", bounds("video").first > bounds("text-only").first + 14f)
        assertTrue("A long name stays inside its room", bounds("long").first <= 60.5f)
    }

    private fun bounds(tag: String): Pair<Float, Float> {
        val rect = fixture.compose.onNodeWithTag(tag).getBoundsInRoot()
        return (rect.right - rect.left).value to (rect.bottom - rect.top).value
    }
}
