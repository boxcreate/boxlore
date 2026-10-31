package cx.aswin.boxlore.feature.player.v2

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full player swaps its phone and tablet layouts when a foldable folds or a window resizes. An
 * Android 17 local network answer that arrives after that swap must still open a picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastPickerReflowTest {
    @get:Rule val composeRule = createComposeRule()

    private class RecordingRegistry : ActivityResultRegistry() {
        var lastRequestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            lastRequestCode = requestCode
        }
    }

    private val registry = RecordingRegistry()
    private val tablet = mutableStateOf(false)
    private var systemPickerOpens = 0
    private lateinit var picker: CastPickerState

    private fun showPlayer() {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                picker = rememberCastPickerState(
                    sdkInt = 37,
                    hasLocalNetworkAccess = { false },
                    showSystemPicker = {
                        systemPickerOpens++
                        true
                    },
                )
                CompositionLocalProvider(LocalCastPicker provides picker) {
                    // The phone and tablet layouts place the Cast button in different trees.
                    if (tablet.value) {
                        Box { BoxLoreCastRouteButton(enabled = true, isCasting = false) }
                    } else {
                        Column { BoxLoreCastRouteButton(enabled = true, isCasting = false) }
                    }
                }
            }
        }
    }

    private fun tapCastThenSwapLayout(): Int {
        showPlayer()
        composeRule.onNodeWithContentDescription("Cast").performClick()
        val requestCode = registry.lastRequestCode
        assertNotNull("tapping Cast on Android 17 asks for local network access", requestCode)
        tablet.value = true
        composeRule.waitForIdle()
        return requireNotNull(requestCode)
    }

    @Test
    fun `declining after the layout swaps still opens the system picker`() {
        val requestCode = tapCastThenSwapLayout()
        composeRule.runOnIdle { registry.dispatchResult(requestCode, false) }
        composeRule.waitForIdle()
        assertEquals(1, systemPickerOpens)
        assertFalse(picker.showSheet)
    }

    @Test
    fun `allowing after the layout swaps opens the Cast sheet`() {
        val requestCode = tapCastThenSwapLayout()
        composeRule.runOnIdle { registry.dispatchResult(requestCode, true) }
        composeRule.waitForIdle()
        assertTrue(picker.showSheet)
        assertEquals(0, systemPickerOpens)
    }
}
