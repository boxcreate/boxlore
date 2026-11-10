package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenWidgetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyWidgetInThePickerCanBeAddedFromTheAppUnderTheSameName() {
        val receivers = WidgetProviders.all.map { it.receiverClass } + LibraryWidgetProviders.all.map { it.receiverClass }
        assertEquals(receivers.toSet(), HomeScreenWidgets.all.map { it.receiver }.toSet())
        assertEquals(HomeScreenWidgets.all.size, HomeScreenWidgets.all.map { it.key }.toSet().size)
        // The same keys as the picker previews, so a widget is one thing wherever it is listed.
        assertEquals(WidgetPickerPreviews.previews().associate { it.key to it.receiver }, HomeScreenWidgets.all.associate { it.key to it.receiver })
        for (entry in HomeScreenWidgets.all) {
            val info = context.packageManager.getReceiverInfo(android.content.ComponentName(context, entry.receiver), 0)
            assertEquals("${entry.key} is named as the picker names it", context.getString(info.labelRes), context.getString(entry.label))
            assertTrue(context.getString(entry.description).isNotBlank())
        }
    }

    @Test
    fun aHomeScreenThatCannotAddWidgetsIsReportedAndNeverAsked() {
        shadowOf(context.getSystemService(AppWidgetManager::class.java)).setRequestPinAppWidgetSupported(false)
        assertFalse(HomeScreenWidgets.canAdd(context))
        assertFalse(HomeScreenWidgets.requestAdd(context, "full"))
    }

    @Test
    fun aHomeScreenThatCanAddWidgetsIsAskedForKnownWidgetsOnly() {
        shadowOf(context.getSystemService(AppWidgetManager::class.java)).setRequestPinAppWidgetSupported(true)
        assertTrue(HomeScreenWidgets.canAdd(context))
        assertTrue(HomeScreenWidgets.all.all { HomeScreenWidgets.requestAdd(context, it.key) })
        assertFalse(HomeScreenWidgets.requestAdd(context, "no-such-widget"))
    }

    @Test
    fun everyWidgetHasAPreviewDrawnForItsOwnSize() {
        for (entry in HomeScreenWidgets.all) {
            assertTrue("${entry.key} has a preview size", entry.previewWidthDp > 0 && entry.previewHeightDp > 0)
            val views = HomeScreenWidgets.preview(context, entry.key)
            assertTrue("${entry.key} draws", views != null)
            views!!.apply(context, android.appwidget.AppWidgetHostView(context))
        }
        assertEquals(null, HomeScreenWidgets.preview(context, "no-such-widget"))
        // Only Your Shows follows the cover setting, so only it carries that choice in Settings.
        assertEquals(listOf("shows"), HomeScreenWidgets.all.filter { it.usesShowsCoverStyle }.map { it.key })
    }
}
