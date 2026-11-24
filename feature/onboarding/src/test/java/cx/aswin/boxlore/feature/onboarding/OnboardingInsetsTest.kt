package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a sideways phone with 3-button navigation at the end and a camera cutout at the start, the
 * onboarding steps keep their controls clear of both; a portrait phone, with no side insets, keeps
 * its layout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreTestWindows.PHONE_LAND)
    private val callbacks = EchoCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun show(insets: SystemInsets) {
        // Echo idles without end; the clock is stepped by hand.
        compose.mainClock.autoAdvance = false
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        OnboardingStepTransition(OnboardingStep.AI_ONBOARDING) {
                            EchoOnboardingScreen(OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING), callbacks)
                        }
                    }
                }
            }
        }
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, insets) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `Echo's controls stay clear of the side buttons and the cutout`() {
        show(SystemInsets.SIDEWAYS_3_BUTTON)
        val width = window.value.widthDp
        listOf("echo_more", "echo_topics", "echo_primary", "echo_progress").forEach { tag ->
            val node = bounds(tag)
            assertTrue("$tag sits under the cutout ($node)", node.left >= 32f - 0.5f)
            assertTrue("$tag sits under the side buttons ($node)", node.right <= width - 48f + 0.5f)
        }
    }

    @Test
    fun `a portrait phone keeps its layout`() {
        window.value = BoxloreTestWindows.PHONE
        show(SystemInsets.PHONE_BARS)
        assertEquals(24f, bounds("echo_topics").left, 0.5f)
        assertEquals(window.value.widthDp - 24f, bounds("echo_topics").right, 0.5f)
    }
}
