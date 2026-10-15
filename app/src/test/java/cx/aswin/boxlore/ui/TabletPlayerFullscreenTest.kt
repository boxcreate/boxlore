package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.player.v2.PlayerFullscreenOverlay
import cx.aswin.boxlore.feature.player.v2.PlayerPresentationController
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TabletPlayerFullscreenTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))
    private lateinit var navigation: TabletDetailNavigation
    private lateinit var request: (PlayerFullscreenOverlay, Boolean) -> Unit
    private val presentation = object : PlayerPresentationController {
        override var expanded = false
    }
    private var video = false

    @Test
    fun `video and transcript promote context or dock to the window and restore the original owner`() {
        render()
        for (docked in listOf(false, true)) {
            for (overlay in PlayerFullscreenOverlay.entries) {
                fixture.compose.runOnUiThread {
                    navigation.playerDocked = docked
                    presentation.expanded = docked
                    request(overlay, true)
                }
                advance()
                assertTrue(navigation.immersive)
                assertTrue(presentation.expanded)
                assertEquals(overlay == PlayerFullscreenOverlay.Video, video)
                fixture.compose.runOnUiThread {
                    // A remote hero can send this reset while a transcript is open.
                    if (overlay == PlayerFullscreenOverlay.Transcript) request(PlayerFullscreenOverlay.Video, false)
                }
                advance()
                assertTrue(navigation.immersive)
                fixture.compose.runOnUiThread { request(overlay, false) }
                advance()
                assertFalse(navigation.immersive)
                assertEquals(docked, navigation.playerDocked)
                assertEquals(docked, presentation.expanded)
                assertFalse(video)
            }
        }
    }

    @Test
    fun `overlay opened from immersive playback returns to immersive playback`() {
        render()
        fixture.compose.runOnUiThread {
            navigation.immersive = true
            presentation.expanded = true
            request(PlayerFullscreenOverlay.Video, true)
        }
        advance()
        fixture.compose.runOnUiThread { request(PlayerFullscreenOverlay.Video, false) }
        advance()
        assertTrue(navigation.immersive)
        assertTrue(presentation.expanded)
        assertFalse(video)
    }

    private fun render() {
        fixture.setContent {
            val primary = rememberNavController()
            val detail = rememberNavController()
            navigation = androidx.compose.runtime.remember { TabletDetailNavigation(primary, detail, mutableStateOf(false), mutableStateOf(true)) }
            request = rememberTabletPlayerFullscreenRequest("episode", navigation, presentation) { video = it }
        }
        advance()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }
}
