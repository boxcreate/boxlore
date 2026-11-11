package cx.aswin.boxlore.feature.widgets.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresentationTest {
    private fun full(heightDp: Int, widthDp: Int = 350) = WidgetPresentation.fullControls(WidgetSize(widthDp, heightDp))

    private fun bar(heightDp: Int, widthDp: Int = 350) = WidgetPresentation.bar(WidgetSize(widthDp, heightDp))

    @Test
    fun fullControlsDropsTheShowNameFirstThenTheTimeAndNeverTheTitle() {
        // A roomy widget shows everything.
        assertEquals(WidgetPresentation.Meta.TIME_AND_SHOW, full(182).meta)
        assertEquals(2, full(182).titleLines)
        // A shorter one keeps the title and the time left.
        assertEquals(WidgetPresentation.Meta.TIME, full(110).meta)
        assertEquals(1, full(110).titleLines)
        // The shortest keeps only the episode title.
        assertEquals(WidgetPresentation.Meta.NONE, full(80).meta)
        assertEquals(1, full(80).titleLines)
    }

    @Test
    fun fullControlsNeverGainsLinesAsItShrinksAndNeverLosesThemAsItGrows() {
        for (width in listOf(245, 350, 620)) {
            var lines = Int.MAX_VALUE
            var meta = WidgetPresentation.Meta.TIME_AND_SHOW.ordinal
            for (height in 420 downTo 60) {
                val show = full(height, width)
                assertTrue("title lines grew while shrinking to ${width}x$height", show.titleLines <= lines)
                assertTrue("the line under the title grew while shrinking to ${width}x$height", show.meta.ordinal >= meta)
                assertTrue(show.titleLines >= 1)
                lines = show.titleLines
                meta = show.meta.ordinal
            }
        }
    }

    @Test
    fun fullControlsChangesShapeOnTheWayUpAndBackDown() {
        val regular = full(182)
        val tall = full(278)

        assertFalse(regular.tall)
        assertTrue(tall.tall)
        assertEquals(3, tall.titleLines)
        // Resizing back gives the same answer as before.
        assertEquals(regular, full(182))
    }

    @Test
    fun aNarrowFullControlsWidgetOnlyShowsWhatItsSmallerFrameHasRoomFor() {
        // In a narrow, tall cell the frame stops at 60% of the width, so the cell's height adds nothing.
        assertEquals(full(147, widthDp = 245), full(220, widthDp = 245))
        assertEquals(1, full(220, widthDp = 245).titleLines)
        assertEquals(2, full(220, widthDp = 350).titleLines)
    }

    @Test
    fun compactPlayerShowsTheTimeLeftOnlyWithRoom() {
        val slim = bar(52)
        val regular = bar(86)

        assertEquals(1, slim.titleLines)
        assertFalse(slim.showTimeLeft)
        assertEquals(2, regular.titleLines)
        assertTrue(regular.showTimeLeft)
        assertEquals(regular, bar(86))
    }

    @Test
    fun compactPlayerStaysSlimInTallAndNarrowCells() {
        // The pill is never taller than a quarter of its width, however tall the cell is.
        assertEquals(bar(87), bar(160))
        assertEquals(bar(61, widthDp = 245), bar(160, widthDp = 245))
        assertFalse("a narrow pill has no room for the time left", bar(160, widthDp = 245).showTimeLeft)
    }

    @Test
    fun everySizeStepShowsExactlyWhatAWidgetOfAnySizeInItsRangeShows() {
        // The launcher draws the largest step that fits, so that step must stand for every size it covers.
        for (width in 100..700 step 7) {
            for (height in 40..420 step 5) {
                val drawn = WidgetSize(width, height)
                val fullStep = WidgetPresentation.stepFor(WidgetPresentation.FULL_CONTROLS_STEPS, drawn)
                assertEquals("full controls at ${width}x$height", WidgetPresentation.fullControls(drawn), WidgetPresentation.fullControls(fullStep))
                val barStep = WidgetPresentation.stepFor(WidgetPresentation.BAR_STEPS, drawn)
                assertEquals("compact at ${width}x$height", WidgetPresentation.bar(drawn), WidgetPresentation.bar(barStep))
            }
        }
    }

    @Test
    fun sizeStepsGrowOneInsideTheNextSoTheLargestThatFitsIsAlwaysTheClosest() {
        for (steps in listOf(WidgetPresentation.FULL_CONTROLS_STEPS, WidgetPresentation.BAR_STEPS)) {
            assertEquals(WidgetSize(0, 0), steps.first())
            steps.zipWithNext { smaller, larger ->
                assertTrue("$smaller fits inside $larger", smaller.widthDp <= larger.widthDp && smaller.heightDp <= larger.heightDp && smaller != larger)
            }
        }
    }
}
