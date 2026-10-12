package cx.aswin.boxlore.feature.player.v2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinimalPlayerControlsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var background = 0
    private var primary = 0
    private var tertiary = 0
    private var neutral = 0
    private val state = mutableStateOf(PlayerState())

    private fun render(dark: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            BoxLoreTheme(darkTheme = dark) {
                background = MaterialTheme.colorScheme.surface.toArgb()
                primary = MaterialTheme.colorScheme.primary.toArgb()
                tertiary = minimalControlAccent(MaterialTheme.colorScheme).container.toArgb()
                neutral = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    MinimalPlayerControls(
                        state.value,
                        MaterialTheme.colorScheme,
                        MinimalPlayerActions(
                            { state.value = state.value.copy(isPlaying = !state.value.isPlaying) },
                            {}, {}, {}, {}, {}, {}, {}, {},
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `press changes the button silhouette and playback changes it to a circle`() {
        render()
        val beforePress = pixel("Play", 0.12f)
        val beforeActive = pixel("Play", 0.12f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Play").performTouchInput { down(center) }
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        assertNotEquals(beforePress, pixel("Play", 0.12f))
        compose.onNodeWithContentDescription("Play").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertNotEquals(beforeActive, pixel("Pause", 0.12f))
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(beforeActive, pixel("Play", 0.12f))
    }

    @Test
    fun `repeated fast taps settle on the correct paused and playing silhouettes`() {
        render()
        val resting = silhouette("Play")
        compose.mainClock.autoAdvance = false
        repeat(12) {
            val label = if (state.value.isPlaying) "Pause" else "Play"
            compose.onNodeWithContentDescription(label).performTouchInput { click(center) }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(false, state.value.isPlaying)
        assertEquals(resting, silhouette("Play"))
        compose.onNodeWithContentDescription("Play").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(true, state.value.isPlaying)
        assertNotEquals(resting, silhouette("Pause"))
    }

    @Test
    fun `buffering or playback changes during a press cannot leave a stuck pressed shape`() {
        render()
        val resting = silhouette("Play")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Play").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread {
            state.value = state.value.copy(isLoading = true)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Buffering").performTouchInput { up() }
        compose.runOnUiThread {
            state.value = state.value.copy(isLoading = false, isPlaying = true)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(600)
        assertNotEquals(resting, silhouette("Pause"))
        compose.onNodeWithContentDescription("Pause").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(background, pixel("Pause", 0.12f))
        compose.onNodeWithContentDescription("Pause").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(resting, silhouette("Play"))
    }

    @Test
    fun `active playback sleep and speed have distinct silhouettes`() {
        state.value = PlayerState(isPlaying = true, sleepAtEndOfEpisode = true, playbackSpeed = 1.5f)
        render()
        val shapes = listOf(silhouette("Pause"), silhouette("Sleep timer"), silhouette("Playback speed: 1.5×"))
        assertEquals(3, shapes.distinct().size)
    }

    @Test
    fun `light controls separate the playback accent from active secondary controls`() {
        assertControlAccents(dark = false)
    }

    @Test
    fun `dark controls separate the playback accent from active secondary controls`() {
        assertControlAccents(dark = true)
    }

    private fun assertControlAccents(dark: Boolean) {
        state.value = PlayerState(isPlaying = true, sleepAtEndOfEpisode = true, playbackSpeed = 1.5f, seekBackwardMs = 15_000L)
        render(dark)
        assertNotEquals(primary, tertiary)
        assertEquals(primary, pixel("Pause", 0.5f, 0.2f))
        assertEquals(tertiary, pixel("Sleep timer", 0.5f, 0.2f))
        assertEquals(tertiary, pixel("Playback speed: 1.5×", 0.5f, 0.2f))
        assertEquals(neutral, pixel("Seek back 15 seconds", 0.5f, 0.2f))
        compose.runOnUiThread { state.value = PlayerState() }
        compose.waitForIdle()
        assertEquals(primary, pixel("Play", 0.5f, 0.2f))
        assertEquals(neutral, pixel("Sleep timer", 0.5f, 0.2f))
        assertEquals(neutral, pixel("Playback speed: 1×", 0.5f, 0.2f))
    }

    private fun silhouette(label: String): List<Boolean> {
        val bounds = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
        return compose.runOnIdle {
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val mask = (1..19).flatMap { y ->
                (1..19).map { x ->
                    image.getPixel((bounds.left.value + (bounds.right.value - bounds.left.value) * x / 20).toInt(), (bounds.top.value + (bounds.bottom.value - bounds.top.value) * y / 20).toInt()) != background
                }
            }
            image.recycle()
            mask
        }
    }

    private fun pixel(label: String, fraction: Float, verticalFraction: Float = fraction): Int {
        val bounds = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
        return compose.runOnIdle {
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            image.getPixel((bounds.left.value + (bounds.right.value - bounds.left.value) * fraction).toInt(), (bounds.top.value + (bounds.bottom.value - bounds.top.value) * verticalFraction).toInt()).also { image.recycle() }
        }
    }
}
