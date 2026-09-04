package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetTextBitmapRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun renderedLabelHasExactBoundsAndVisiblePixels() {
        val bitmap =
            WidgetTextBitmapRenderer.render(
                context = context,
                spec =
                WidgetTextBitmapRenderer.Spec(
                    text = "A rounded episode title",
                    widthDp = 180,
                    heightDp = 48,
                    preferredSizeSp = 22f,
                    minSizeSp = 14f,
                    weight = 600,
                    maxLines = 2,
                ),
                colorRes = R.color.widget_on_surface,
            )
        val density = context.resources.displayMetrics.density

        assertEquals((180 * density).toInt(), bitmap.width)
        assertEquals((48 * density).toInt(), bitmap.height)
    }
}
