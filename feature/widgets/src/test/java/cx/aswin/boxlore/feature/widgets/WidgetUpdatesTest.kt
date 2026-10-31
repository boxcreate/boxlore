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
    // Android 17 throws this for apps targeting API 37 (and drops the widget's stored views); earlier
    // releases only logged it.
    private val overCap = IllegalArgumentException("RemoteViews for widget update exceeds maximum bitmap memory usage")

    @Test
    fun `an update the launcher accepts is pushed without building the lighter one`() {
        val pushed = mutableListOf<Pair<Int, String>>()
        val ok = pushWidgetUpdate(7, "views", lighter = { error("not needed") }) { id, views -> pushed += id to views }
        assertTrue(ok)
        assertEquals(listOf(7 to "views"), pushed)
    }

    @Test
    fun `an update over the bitmap cap is replaced by the lighter one instead of crashing`() {
        val pushed = mutableListOf<String>()
        val ok = pushWidgetUpdate(7, "views", lighter = { "without artwork" }) { _, views ->
            if (views == "views") throw overCap
            pushed += views
        }
        assertTrue(ok)
        assertEquals(listOf("without artwork"), pushed)
    }

    @Test
    fun `when the lighter update is rejected too the caller still doesn't crash`() {
        assertFalse(pushWidgetUpdate(7, "views", lighter = { "without artwork" }) { _, _ -> throw overCap })
        assertFalse(pushWidgetUpdate(7, "views", lighter = null) { _, _ -> throw overCap })
    }

    @Test(expected = IllegalStateException::class)
    fun `other failures still surface`() {
        pushWidgetUpdate(7, "views", lighter = null) { _, _ -> throw IllegalStateException("unexpected") }
    }
}
