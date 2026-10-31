package cx.aswin.boxlore.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetUpdatesTest {
    @Test
    fun `an update the launcher accepts is pushed`() {
        val pushed = mutableListOf<Pair<Int, String>>()
        assertTrue(pushWidgetUpdate(7, "views") { id, views -> pushed += id to views })
        assertEquals(listOf(7 to "views"), pushed)
    }

    @Test
    fun `an update over the bitmap memory cap leaves the widget as it was instead of crashing`() {
        // Android 17 throws this for apps targeting API 37; earlier releases only logged it.
        val overCap = IllegalArgumentException("RemoteViews for widget update exceeds maximum bitmap memory usage")
        assertFalse(pushWidgetUpdate(7, "views") { _, _ -> throw overCap })
    }

    @Test(expected = IllegalStateException::class)
    fun `other failures still surface`() {
        pushWidgetUpdate(7, "views") { _, _ -> throw IllegalStateException("unexpected") }
    }
}
