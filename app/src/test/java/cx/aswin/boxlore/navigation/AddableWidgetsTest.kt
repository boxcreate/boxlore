package cx.aswin.boxlore.navigation

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.feature.widgets.HomeScreenWidgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AddableWidgetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun settingsOffersEveryWidgetUnderItsPickerName() {
        val offered = addableWidgets(context)

        assertEquals(HomeScreenWidgets.all.map { it.key }, offered.map { it.key })
        assertEquals("Now Playing · Full Controls", offered.first().name)
        assertEquals("Library · New Episodes", offered.last().name)
        assertTrue(offered.all { it.name.isNotBlank() && it.description.isNotBlank() })
        assertEquals("every name is told apart", offered.size, offered.map { it.name }.toSet().size)
        // The two round players are small enough to sit side by side in Settings.
        assertEquals(listOf("round", "round_next"), offered.filter { it.previewWidthDp <= 200 }.map { it.key })
    }
}
