package cx.aswin.boxlore.feature.player.v2

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full player swaps its phone and tablet layouts when a foldable folds or a window resizes. An
 * Android 17 local network answer that arrives after that swap must still open a picker, and a device
 * picked in the Cast sheet must still be followed to the end.
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

    private val session = CastSessionWatch(initial = null)
    private lateinit var pick: CastPickState
    private val closedBy = mutableListOf<String>()
    private val onConnected = mutableStateOf({ closedBy += "first" })

    /** The sheet's pick, hosted once above the phone and tablet layouts as the full player hosts the sheet. */
    private fun showPendingPick(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            pick = rememberCastPickState()
            followCastPick(pick = pick, session = session, onConnected = onConnected.value)
            if (tablet.value) Box {} else Column {}
        }
        if (restoration == null) composeRule.setContent(content) else restoration.setContent(content)
        composeRule.waitForIdle()
    }

    /** A write has to be handed to Compose and recomposed before the clock moves on. */
    private fun write(change: () -> Unit) {
        change()
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    @Test
    fun `a pick survives a layout swap and closes the sheet once with the current callback`() {
        showPendingPick()
        write { pick.start("route-tv", "Bedroom TV", fromDeviceId = null) }
        write { tablet.value = true }
        advance(10_000)
        assertEquals("route-tv", pick.routeId)

        write { session.connected = ConnectedCastDevice("tv", "Bedroom TV") }
        advance(100)
        // The host recomposes with a new callback during the beat on "Connected".
        write {
            onConnected.value = { closedBy += "current" }
            tablet.value = false
        }
        advance(5_000)
        assertEquals(listOf("current"), closedBy)
    }

    @Test
    fun `a pick that times out or fails across a layout swap lets the listener pick again`() {
        tablet.value = true
        showPendingPick()
        write { pick.start("route-tv", "Bedroom TV", fromDeviceId = null) }
        advance(20_000)
        write { tablet.value = false }
        advance(9_000)
        assertEquals("route-tv", pick.routeId)
        // The wait is not restarted by the swap.
        advance(2_000)
        assertNull(pick.routeId)

        write {
            pick.start("route-tv", "Bedroom TV", fromDeviceId = null)
            tablet.value = true
        }
        write { session.startFailed = true }
        advance(100)
        assertNull(pick.routeId)
        assertTrue(closedBy.isEmpty())
    }

    @Test
    fun `a pick and the device it started from survive recreation`() {
        val restoration = StateRestorationTester(composeRule)
        session.connected = ConnectedCastDevice("kitchen", "Kitchen speaker")
        showPendingPick(restoration)
        write { pick.start("route-tv", "Bedroom TV", fromDeviceId = "kitchen") }

        restoration.emulateSavedInstanceStateRestore()

        assertEquals("route-tv", pick.routeId)
        assertEquals("Bedroom TV", pick.routeName)
        assertEquals("kitchen", pick.fromDeviceId)
        // Still casting to the speaker it started from: keep waiting for the TV.
        advance(1_000)
        assertTrue(closedBy.isEmpty())
        write { session.connected = ConnectedCastDevice("tv", "Bedroom TV") }
        advance(1_000)
        assertEquals(listOf("first"), closedBy)
    }
}
