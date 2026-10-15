package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class TabletHomeLeadingSectionsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 1_280, false))

    @Test
    fun `setup and horizontal Brief share the tablet row and resizing retains their owners`() {
        val width = mutableIntStateOf(1_200)
        fixture.prepare()
        fixture.setContent {
            Box(Modifier.width(width.intValue.dp)) {
                TabletHomeLeadingSections(
                    showLibrary = true,
                    library = {
                        val count = rememberSaveable { mutableIntStateOf(0) }
                        Box(Modifier.fillMaxWidth().height(300.dp).testTag("setup")) { Button(onClick = { count.intValue++ }) { Text("Count ${count.intValue}") } }
                    },
                    briefing = { Box(Modifier.fillMaxWidth().height(500.dp).testTag("brief")) },

                )
            }
        }
        advance()
        val setup = fixture.compose.onNodeWithTag("setup").fetchSemanticsNode().boundsInRoot
        val brief = fixture.compose.onNodeWithTag("brief").fetchSemanticsNode().boundsInRoot
        assertTrue(setup.width <= 720f)
        assertTrue(brief.width <= 720f)
        assertTrue(brief.left > setup.right)
        fixture.compose.onNodeWithText("Count 0").performClick()
        fixture.compose.runOnUiThread { width.intValue = 720 }
        advance()
        val portraitBrief = fixture.compose.onNodeWithTag("brief").fetchSemanticsNode().boundsInRoot
        val portraitSetup = fixture.compose.onNodeWithTag("setup").fetchSemanticsNode().boundsInRoot
        assertEquals(portraitBrief.left, portraitSetup.left, 1f)
        assertEquals(portraitSetup.bottom + 24f, portraitBrief.top, 1f)
        fixture.compose.onNodeWithText("Count 1").assertExists()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
