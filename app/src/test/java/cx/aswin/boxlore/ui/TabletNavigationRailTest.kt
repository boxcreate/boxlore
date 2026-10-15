package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationRail
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
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
class TabletNavigationRailTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(600, 960, false))

    @Test
    fun `four existing destinations share exactly one selection indicator`() {
        val selected = mutableStateOf("home")
        val routes = mutableListOf<String>()
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                BoxLoreNavigationRail(selected.value, {
                    routes += it
                    selected.value = it
                }, Modifier.width(80.dp).height(600.dp))
            }
        }
        listOf("Explore" to "explore", "Library" to "library", "Lore" to "learn", "Home" to "home").forEach { (label, route) ->
            fixture.compose.onNodeWithText(label).performClick()
            Snapshot.sendApplyNotifications()
            fixture.compose.mainClock.advanceTimeByFrame()
            fixture.compose.mainClock.advanceTimeBy(320)
            fixture.compose.waitForIdle()
            fixture.compose.onNodeWithText(label).assertIsSelected()
            fixture.compose.onAllNodesWithTag("navigation_rail_indicator", useUnmergedTree = true).assertCountEquals(1)
            assertEquals(route, routes.last())
        }
    }

    @Test
    fun `a moving indicator redraws the rail without recomposing it every frame`() {
        val selected = mutableStateOf("home")
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                BoxLoreNavigationRail(selected.value, {}, Modifier.width(80.dp).height(600.dp))
            }
        }
        fixture.compose.waitForIdle()
        fixture.compose.mainClock.autoAdvance = false
        fun applied() = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        for (route in listOf("library", "learn", "home")) {
            fixture.compose.runOnIdle { selected.value = route }
            repeat(3) { fixture.compose.mainClock.advanceTimeByFrame() }
            val settled = applied()
            repeat(12) { fixture.compose.mainClock.advanceTimeByFrame() }
            assertEquals("Indicator frames for $route must not recompose the rail", settled, applied())
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.onNodeWithText("Home").assertIsSelected()
    }
}
