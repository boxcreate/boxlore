package cx.aswin.boxlore.feature.info.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.branded.ActiveBrandedPage
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkiePage
import kotlin.math.roundToInt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Subscribe button returns to its resting shape after a press that changes the subscription.
 * A press held past the tap delay inside a scrolling page left it in the pressed shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscribePressShapeTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var view: View

    @Test
    fun `a held press that subscribes or unsubscribes leaves the resting shape`() = pressAndToggle(showcase = false)

    @Test
    fun `the same on a Showcase page, whose moment draws the button while it plays`() = pressAndToggle(showcase = true)

    @Test
    fun `a press cut off by a settings change leaves the resting shape once the finger lifts`() {
        // A theme switch, for one, restarts the button's touch handling: the press it was holding never
        // reported its end, and the button kept the pressed shape after the finger lifted.
        var settingsChanged by mutableStateOf(false)
        setToolbar(showcase = true, settingsChanged = { settingsChanged })
        rule.onNodeWithContentDescription("Subscribe").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        assertFalse("pressed while the finger is down", resting("Subscribe"))
        settingsChanged = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Subscribe").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertTrue("back at rest", resting("Subscribe"))
    }

    @Test
    fun `a press whose end never arrives can't hold the pressed shape without a finger on the button`() {
        // On the phone the button stayed in the pressed shape, untouched: a press had started and its
        // end was lost on the way, and the shape followed the press alone.
        val source = MutableInteractionSource()
        setToolbar(showcase = true, source = source)
        rule.runOnIdle { source.tryEmit(PressInteraction.Press(Offset.Zero)) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertTrue("at rest with no finger on it", resting("Subscribe"))
    }

    private var subscribed by mutableStateOf(false)

    private fun pressAndToggle(showcase: Boolean) {
        setToolbar(showcase)
        assertTrue("the button starts at rest", resting("Subscribe"))

        holdAndRelease("Subscribe")
        assertTrue("Subscribed, back at rest", resting("Unsubscribe"))

        holdAndRelease("Unsubscribe")
        assertTrue("Subscribe again, back at rest", resting("Subscribe"))
    }

    private fun setToolbar(showcase: Boolean, settingsChanged: () -> Boolean = { false }, source: MutableInteractionSource = MutableInteractionSource()) {
        rule.setContent {
            view = LocalView.current
            val style = CrimeJunkiePage.style(lightColorScheme(), dark = false)
            val base = LocalViewConfiguration.current
            val changed = remember(base) { object : ViewConfiguration by base {} }
            MaterialTheme {
                CompositionLocalProvider(
                    LocalBrandedPage provides if (showcase) ActiveBrandedPage(CrimeJunkiePage, style) else null,
                    LocalViewConfiguration provides if (settingsChanged()) changed else base,
                ) {
                    Column(Modifier.background(Color.White).verticalScroll(rememberScrollState())) {
                        EpisodeToolbar(
                            searchQuery = "",
                            onSearchChange = {},
                            isSearching = false,
                            currentSort = EpisodeSort.NEWEST,
                            onSortToggle = {},
                            isSubscribed = subscribed,
                            onSubscribeClick = { subscribed = !subscribed },
                            accentColor = Color.Red,
                            subscribeInteractionSource = source,
                        )
                    }
                }
            }
        }
    }

    /** A finger resting on the button past the tap delay before it lifts, as a real tap often does. */
    private fun holdAndRelease(description: String) {
        rule.onNodeWithContentDescription(description).performTouchInput {
            down(center)
            advanceEventTime(300)
            up()
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
    }

    /**
     * Whether the button's top trailing corner is the 8dp resting corner: 3px in from both edges is
     * inside an 8dp corner and outside the 18dp pressed corner.
     */
    private fun resting(description: String): Boolean {
        val bounds = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
        return rule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            try {
                val right = bounds.right.roundToInt() - 1
                val edge = bitmap.getPixel(right - 3, bounds.center.y.roundToInt())
                bitmap.getPixel(right - 3, bounds.top.roundToInt() + 3) == edge
            } finally {
                bitmap.recycle()
            }
        }
    }
}
