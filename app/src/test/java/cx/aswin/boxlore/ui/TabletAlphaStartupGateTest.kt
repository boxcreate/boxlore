package cx.aswin.boxlore.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletAlphaStartupGateTest(private val configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun `opaque notice does not initialize Welcome until OK`() {
        val pending = mutableStateOf(true)
        var initialized = 0
        var acknowledgments = 0
        var surface = 0
        lateinit var view: View
        fixture.prepare()
        fixture.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(configuration.width.toFloat(), configuration.height.toFloat())) {
                Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                    TabletAlphaStartupGate(pending.value, {
                        acknowledgments++
                        pending.value = false
                    }, noticeTheme = { content ->
                        BoxLoreTheme(darkTheme = configuration.dark, dynamicColor = false) {
                            surface = MaterialTheme.colorScheme.surface.toArgb()
                            content()
                        }
                    }) {
                        remember { initialized++ }
                        Text("Welcome")
                    }
                }
            }
        }
        settle()
        fixture.compose.onNodeWithText("Welcome").assertDoesNotExist()
        fixture.compose.onNodeWithText("Tablet layouts are in alpha").assertIsDisplayed()
        fixture.compose.onNodeWithTag("tablet_alpha_ok").assertIsDisplayed()
        assertEquals(0, initialized)
        assertEquals(0, acknowledgments)
        fixture.compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(surface, bitmap.getPixel(0, 0))
            assertEquals(surface, bitmap.getPixel(view.width - 1, view.height - 1))
            bitmap.recycle()
        }
        if (System.getProperty("boxlore.tabletCaptures") == "after") fixture.capture("tablet-alpha-notice")
        fixture.compose.onNodeWithTag("tablet_alpha_ok").performClick()
        settle()
        fixture.compose.onNodeWithTag("tablet_alpha_notice").assertDoesNotExist()
        fixture.compose.onNodeWithText("Welcome").assertIsDisplayed()
        assertEquals(1, initialized)
        assertEquals(1, acknowledgments)
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = listOf(1280 to 800, 840 to 1280).flatMap { (width, height) ->
            listOf(false, true).map { dark -> arrayOf<Any>(PhoneGoldenConfiguration(width, height, dark)) }
        }
    }
}
