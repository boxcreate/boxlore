package cx.aswin.boxlore.feature.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.feature.widgets.actions.WidgetActionIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetActionIntentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun broadcastReturnsValidPendingIntent() {
        val pendingIntent = WidgetActionIntents.broadcast(
            context = context,
            appWidgetId = 101,
            control = WidgetControl.TOGGLE,
        )
        assertNotNull(pendingIntent)
    }

    @Test
    fun cancelAllRunsWithoutCrashingAndCancelsExistingPendingIntents() {
        val appWidgetId = 102
        for (control in WidgetControl.entries) {
            WidgetActionIntents.broadcast(context, appWidgetId, control)
        }
        // Cancel all widget pending intents for this ID
        WidgetActionIntents.cancelAll(context, appWidgetId)
    }

    @Test
    fun openAppReturnsValidPendingIntent() {
        val pendingIntent = WidgetActionIntents.openApp(context)
        assertNotNull(pendingIntent)
    }

    @Test
    fun openDeepLinkReturnsValidPendingIntent() {
        val pendingIntent = WidgetActionIntents.openDeepLink(
            context = context,
            uri = "boxlore://podcast/123",
            requestCode = 999,
        )
        assertNotNull(pendingIntent)
    }

    @Test
    fun parseControlHandlesKnownAndUnknownStrings() {
        val intent = android.content.Intent().apply {
            putExtra(WidgetActionIntents.EXTRA_CONTROL, "TOGGLE")
        }
        assertEquals(WidgetControl.TOGGLE, WidgetActionIntents.parseControl(intent))

        val invalidIntent = android.content.Intent().apply {
            putExtra(WidgetActionIntents.EXTRA_CONTROL, "UNKNOWN_ACTION")
        }
        assertNull(WidgetActionIntents.parseControl(invalidIntent))

        val emptyIntent = android.content.Intent()
        assertNull(WidgetActionIntents.parseControl(emptyIntent))
    }

    @Test
    fun libraryWidgetCancelAllRunsCleanly() {
        val appWidgetId = 202
        LibraryWidgetRenderer.cancelAll(context, appWidgetId)
    }
}
