package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YourShowsSelectorShapeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `shape choice is stable for normal and RSS IDs with varied silhouettes`() {
        val identities = listOf("show-0", "show-1", "show-2", "show-3", "rss:https://example.com/feed", "-123", "polygenelubricants")
        val initial = identities.associateWith(::selectorSilhouette)
        assertEquals(4, identities.take(4).map(::selectorSilhouette).toSet().size)
        assertEquals(initial, identities.reversed().associateWith(::selectorSilhouette))
    }

    @Test fun `morphing and reversed selection preserve centered unclipped outlines`() {
        val selected = mutableStateOf(false)
        val identity = mutableStateOf("show-0")
        var displayed: Shape = RectangleShape
        composeRule.setContent {
            val shape = rememberYourShowsSelectorShape(identity.value, selected.value)
            displayed = shape
            Box(Modifier.size(54.dp).clip(shape).background(Color.Black))
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        for (id in listOf("show-0", "show-1", "show-2", "show-3")) {
            composeRule.runOnUiThread {
                identity.value = id
                selected.value = true
                Snapshot.sendApplyNotifications()
            }
            composeRule.mainClock.advanceTimeBy(96)
            assertCentered(displayed)
            composeRule.runOnUiThread {
                selected.value = false
                Snapshot.sendApplyNotifications()
            }
            composeRule.mainClock.advanceTimeBy(48)
            assertCentered(displayed)
            composeRule.mainClock.advanceTimeBy(1200)
            assertCentered(displayed)
        }
    }

    private fun assertCentered(shape: Shape) {
        for (direction in LayoutDirection.entries) {
            val bounds = (shape.createOutline(Size(54f, 54f), direction, Density(1f)) as Outline.Generic).path.getBounds()
            assertTrue(bounds.left >= -0.1f && bounds.top >= -0.1f && bounds.right <= 54.1f && bounds.bottom <= 54.1f)
            assertEquals(27f, bounds.center.x, 0.1f)
            assertEquals(27f, bounds.center.y, 0.1f)
        }
    }
}
