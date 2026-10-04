package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** JVM drawing regression: advancing frames must visibly change a loading block. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class HomeShimmerAnimationTest {
    private lateinit var view: View

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `short startup wait already has a visible moving highlight`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            ShimmerScope(active = true) {
                Box(
                    Modifier.size(120.dp, 240.dp).background(Color.Black).testTag("shimmer")
                        .m3Shimmer(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.18f)),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val first = samplePixel(0.25f, 0.5f)
        val base = samplePixel(0.9f, 0.5f)
        assertNotEquals("The first drawn skeleton must already have a highlight", base, first)
        composeRule.mainClock.advanceTimeBy(160)
        assertNotEquals("Short cached startup must show movement", first, samplePixel(0.25f, 0.5f))
    }

    @Test
    fun `shared shimmer visibly moves while loading`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            ShimmerScope(active = true) {
                Box(
                    Modifier.size(120.dp, 40.dp).background(Color.White).testTag("shimmer")
                        .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val initial = centerPixel()
        composeRule.mainClock.advanceTimeBy(736)
        assertNotEquals(initial, centerPixel())
    }

    @Test
    fun `sweep keeps moving during the last third of its cycle`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            ShimmerScope(active = true) {
                Box(
                    Modifier.size(120.dp, 40.dp).background(Color.White).testTag("shimmer")
                        .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1200)
        val before = samplePixel(horizontalFraction = 0.15f, verticalFraction = 0.5f)
        composeRule.mainClock.advanceTimeBy(192)
        assertNotEquals(before, samplePixel(horizontalFraction = 0.15f, verticalFraction = 0.5f))
    }

    @Test
    fun `tall placeholder has no rectangle seam through its highlight`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            ShimmerScope(active = true) {
                Box(
                    Modifier.size(120.dp, 240.dp).background(Color.White).testTag("shimmer")
                        .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(736)
        assertEquals(
            samplePixel(horizontalFraction = 0.6f, verticalFraction = 0.1f),
            samplePixel(horizontalFraction = 0.6f, verticalFraction = 0.9f),
        )
    }

    @Test
    fun `animation frames do not recompose skeleton content or rebuild its drawing cache`() {
        var compositions = 0
        var cacheBuilds = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            ShimmerScope(active = true) {
                SideEffect { compositions++ }
                Box(
                    Modifier.size(120.dp, 40.dp).testTag("shimmer")
                        .drawWithCache {
                            cacheBuilds++
                            onDrawWithContent { drawContent() }
                        }
                        .m3Shimmer(),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        centerPixel()
        val initialCompositions = compositions
        val initialCacheBuilds = cacheBuilds
        repeat(10) {
            composeRule.mainClock.advanceTimeBy(96)
            centerPixel()
        }
        assertTrue(initialCacheBuilds > 0)
        assertEquals(initialCompositions, compositions)
        assertEquals(initialCacheBuilds, cacheBuilds)
    }

    @Test
    fun `visible skeleton animates before navigation lifecycle reaches started`() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ShimmerScope(active = true) {
                    Box(
                        Modifier.size(120.dp, 40.dp).background(Color.White).testTag("shimmer")
                            .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val initial = centerPixel()
        composeRule.mainClock.advanceTimeBy(736)
        assertNotEquals(initial, centerPixel())
    }

    @Test
    fun `existing skeleton resumes its sweep when loading is enabled again`() {
        val active = mutableStateOf(false)
        var renderedActive = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            view = LocalView.current
            val isActive = active.value
            SideEffect { renderedActive = isActive }
            ShimmerScope(active = isActive) {
                Box(
                    Modifier.size(120.dp, 40.dp).background(Color.White).testTag("shimmer")
                        .m3Shimmer(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.12f)),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val stopped = centerPixel()
        composeRule.mainClock.advanceTimeBy(736)
        assertEquals(stopped, centerPixel())
        composeRule.runOnIdle {
            active.value = true
            Snapshot.sendApplyNotifications()
        }
        settleComposition()
        assertTrue("Loading flag was recomposed", renderedActive)
        val samples = List(24) {
            composeRule.mainClock.advanceTimeBy(96)
            centerPixel()
        }
        assertTrue("A restarted loading clock must visibly move", samples.any { it != stopped })
    }

    private fun settleComposition() {
        // Auto-advance cancels infinite animations in Compose's test policy.
        // Pump composition explicitly while keeping the animation clock manual.
        repeat(2) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun centerPixel(): Color = samplePixel(0.5f, 0.5f)

    private fun samplePixel(horizontalFraction: Float, verticalFraction: Float): Color {
        val bounds = composeRule.onNodeWithTag("shimmer").fetchSemanticsNode().boundsInRoot
        var pixel = 0
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            pixel = bitmap.getPixel(
                (bounds.left + bounds.width * horizontalFraction).toInt(),
                (bounds.top + bounds.height * verticalFraction).toInt(),
            )
            bitmap.recycle()
        }
        return Color(pixel)
    }
}
