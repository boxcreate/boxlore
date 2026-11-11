package cx.aswin.boxlore.feature.widgets.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizesTest {
    private val fallback = WidgetSize(250, 110)

    @Test
    fun portraitIsNarrowAndTallLandscapeIsWideAndShort() {
        val sizes = WidgetSizes.of(minWidthDp = 350, maxWidthDp = 720, minHeightDp = 120, maxHeightDp = 182, fallback = fallback)
        assertEquals(WidgetSize(350, 182), sizes.portrait)
        assertEquals(WidgetSize(720, 120), sizes.landscape)
        // Pictures are drawn for the most space the widget may get.
        assertEquals(WidgetSize(720, 182), sizes.largest)
        assertEquals(182, sizes.largest.shortSide)
    }

    @Test
    fun missingValuesFallBackInsteadOfCollapsingTheWidget() {
        val nothing = WidgetSizes.of(0, 0, 0, 0, fallback)
        assertEquals(fallback, nothing.portrait)
        assertEquals(fallback, nothing.landscape)

        val onlyMinimums = WidgetSizes.of(minWidthDp = 300, maxWidthDp = 0, minHeightDp = 90, maxHeightDp = 0, fallback = fallback)
        assertEquals(WidgetSize(300, 90), onlyMinimums.portrait)
        assertEquals(WidgetSize(300, 90), onlyMinimums.landscape)
    }

    @Test
    fun swappedReportsAreStillReadTheRightWayRound() {
        val sizes = WidgetSizes.of(minWidthDp = 720, maxWidthDp = 350, minHeightDp = 182, maxHeightDp = 120, fallback = fallback)
        assertEquals(WidgetSize(350, 182), sizes.portrait)
        assertEquals(WidgetSize(720, 120), sizes.landscape)
    }
}
