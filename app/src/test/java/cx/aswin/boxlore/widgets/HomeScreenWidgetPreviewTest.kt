package cx.aswin.boxlore.widgets

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.widgets.HomeScreenWidgets
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widget pictures Settings shows. Set `BOXLORE_WIDGET_RENDERS=1` to save them under
 * `build/reports/widget-previews/` for a look.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h2000dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenWidgetPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** How big each frame is, as a share of its widget's own size: a narrow Settings card is smaller. */
    private val fit = mutableFloatStateOf(1f)

    private fun render(dark: Boolean) {
        compose.setContent {
            BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (widget in HomeScreenWidgets.all) {
                        val size = DpSize(widget.previewWidthDp.dp, widget.previewHeightDp.dp)
                        Box(Modifier.background(MaterialTheme.colorScheme.outlineVariant).padding(14.dp)) {
                            HomeScreenWidgetPreview(widget.key, size, look = dark, Modifier.size(size * fit.floatValue))
                        }
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { hosts().size == HomeScreenWidgets.all.size && hosts().all { it.childCount == 1 } }
        compose.waitForIdle()
    }

    private fun hosts(): List<AppWidgetHostView> {
        val found = mutableListOf<AppWidgetHostView>()

        fun walk(view: View) {
            if (view is AppWidgetHostView) {
                found += view
            } else if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(compose.activity.window.decorView)
        return found
    }

    @Test
    fun everyWidgetIsLaidOutAtItsOwnSizeHoweverSmallItsFrame() {
        render(dark = false)
        for (share in listOf(1f, 0.8f)) {
            fit.floatValue = share
            compose.waitForIdle()
            val density = compose.activity.resources.displayMetrics.density
            hosts().zip(HomeScreenWidgets.all).forEach { (host, widget) ->
                val root = host.getChildAt(0)
                assertNotNull("${widget.key} holds its widget", root)
                assertEquals("${widget.key} fills its host", host.width, root.width)
                // A smaller frame scales the picture; the widget itself keeps its size, so nothing reflows.
                assertEquals("${widget.key} is laid out as wide as it is", widget.previewWidthDp * density, host.width.toFloat(), 2f)
                assertEquals("${widget.key} is laid out as tall as it is", widget.previewHeightDp * density, host.height.toFloat(), 2f)
            }
            save(if (share == 1f) "light" else "light-narrow")
        }
    }

    @Test
    fun aPictureIsShrunkToFitItsFrameAndNeverEnlarged() {
        val widget = DpSize(320.dp, 170.dp)
        assertEquals(1f, widgetPictureScale(widget, maxWidth = 320.dp, maxHeight = 170.dp))
        assertEquals(1f, widgetPictureScale(widget, maxWidth = 700.dp, maxHeight = 400.dp))
        assertEquals(0.8f, widgetPictureScale(widget, maxWidth = 256.dp, maxHeight = 170.dp), 0.001f)
        assertEquals(0.5f, widgetPictureScale(widget, maxWidth = 320.dp, maxHeight = 85.dp), 0.001f)
    }

    @Test
    fun aPictureNeverPassesATouchToTheWidgetsButtons() {
        render(dark = true)
        for (host in hosts()) {
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, host.width / 2f, host.height / 2f, 0)
            assertFalse("a tap is not handled inside the picture", host.dispatchTouchEvent(down))
            down.recycle()
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, host.importantForAccessibility)
        }
        save("dark")
    }

    private fun save(name: String) {
        if (System.getenv("BOXLORE_WIDGET_RENDERS") != "1") return
        val content = compose.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        val directory = File("build/reports/widget-previews").apply { mkdirs() }
        File(directory, "previews-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
