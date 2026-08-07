package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetRemoteViewsColorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun paletteRolesPointAtWidgetColorResources() {
        assertEquals(R.color.widget_surface, WidgetPalette.surface)
        assertEquals(R.color.widget_primary, WidgetPalette.primary)
        assertEquals(R.color.widget_secondary_container, WidgetPalette.secondaryContainer)
    }

    @Test
    fun setColorFilterAppliesWithoutThrowing() {
        val views = RemoteViews(context.packageName, R.layout.library_widget_list)
        WidgetRemoteViewsColors.setColorFilter(
            views,
            R.id.widget_surface_background,
            WidgetPalette.surface,
            context,
        )
        WidgetRemoteViewsColors.setTextColor(
            views,
            R.id.widget_list_header,
            WidgetPalette.onSurface,
            context,
        )
        views.apply(context, FrameLayout(context))
    }

    @Test
    fun resolveReturnsNonTransparentColor() {
        val color = WidgetRemoteViewsColors.resolve(context, WidgetPalette.primary)
        assertEquals(0xFF000000.toInt(), color and 0xFF000000.toInt())
    }
}
