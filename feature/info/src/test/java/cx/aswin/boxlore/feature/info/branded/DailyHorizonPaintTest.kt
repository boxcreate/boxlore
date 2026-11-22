package cx.aswin.boxlore.feature.info.branded

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import cx.aswin.boxlore.feature.info.branded.daily.DailyColors
import cx.aswin.boxlore.feature.info.branded.daily.DailyHorizon
import cx.aswin.boxlore.feature.info.branded.daily.DailyHorizon.drawFirstLight
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyHorizonPaintTest {
    /** The description and chips sit below the curve; nothing of the sky may show through there. */
    @Test
    fun `below the curve the page is one flat colour in both editions`() {
        val cases = listOf(DailyColors.Light.page, DailyColors.Dark.page).flatMap { page ->
            listOf(2f, 2.625f, 2.75f, 3f, 3.5f).flatMap { density -> listOf(320f, 360f, 412f, 673f, 840f).map { Triple(page, density, it) } }
        }
        val leaks = cases.mapNotNull { (page, density, widthDp) ->
            firstLeakingRow(page, density, widthDp)?.let { "page ${Integer.toHexString(page.toArgb())} at ${widthDp}dp × $density: row ${it}dp" }
        }
        assertTrue("The sky shows below the curve:\n" + leaks.joinToString("\n"), leaks.isEmpty())
    }

    /** The first row, in dp from the header's top, below the curve that is not the plain page. */
    private fun firstLeakingRow(page: Color, density: Float, widthDp: Float): Float? {
        val width = (widthDp * density).roundToInt()
        val height = ((DailyHorizon.PageTop.value + 120f) * density).roundToInt()
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(page)
            drawFirstLight(skyTop = 0f, page = page)
        }
        val pixels = bitmap.toPixelMap()
        val belowCurve = ceil((DailyHorizon.Apex.value + DailyHorizon.drop(widthDp) + 2f) * density).toInt()
        return (belowCurve until height).firstOrNull { y -> (0 until width).any { x -> pixels[x, y].toArgb() != page.toArgb() } }?.let { it / density }
    }
}
