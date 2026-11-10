package cx.aswin.boxlore.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetShapesTest {
    @Test
    fun anUnstartedEpisodeShowsOnlyTheFullTrack() {
        val arcs = WidgetRingArcs.of(fraction = 0f, gapDegrees = 9f)
        assertNull(arcs.played)
        assertEquals(WidgetRingArcs.Arc(0f, 360f), arcs.remaining)
    }

    @Test
    fun thePlayedArcAndTheRestAreSeparatedByAGapAtBothEnds() {
        val gap = 9f
        val arcs = WidgetRingArcs.of(fraction = 0.4f, gapDegrees = gap)
        val played = arcs.played!!
        val remaining = arcs.remaining!!

        assertEquals(144f, played.sweepDegrees, 0.01f)
        assertEquals(played.sweepDegrees + gap, remaining.startDegrees, 0.01f)
        // The rest stops one gap short of where the played arc began.
        assertEquals(360f - gap, remaining.startDegrees + remaining.sweepDegrees, 0.01f)
    }

    @Test
    fun aSliverOfProgressStillShowsAndTheEndClosesTheRing() {
        val started = WidgetRingArcs.of(fraction = 0.0005f, gapDegrees = 9f)
        assertTrue(started.played!!.sweepDegrees >= 2f)
        assertNotNull(started.remaining)

        val nearlyDone = WidgetRingArcs.of(fraction = 0.99f, gapDegrees = 9f)
        assertEquals(WidgetRingArcs.Arc(0f, 360f), nearlyDone.played)
        assertNull(nearlyDone.remaining)
    }

    @Test
    fun badInputNeverDrawsOutsideTheRing() {
        assertNull(WidgetRingArcs.of(Float.NaN, 9f).played)
        assertEquals(WidgetRingArcs.Arc(0f, 360f), WidgetRingArcs.of(7f, 9f).played)
        assertNull(WidgetRingArcs.of(-3f, 9f).played)
    }

    @Test
    fun mixedShapesCycleAndNeverPutTheSameShapeSideBySide() {
        val firstRow = (0 until 4).map(WidgetShapes::mixedShapeAt)
        assertEquals(4, firstRow.toSet().size)
        for (position in 0 until 48) {
            assertTrue(
                "tiles $position and ${position + 1} share a shape",
                WidgetShapes.mixedShapeAt(position) != WidgetShapes.mixedShapeAt(position + 1),
            )
        }
        assertEquals(WidgetShapes.mixedShapeAt(0), WidgetShapes.mixedShapeAt(12))
    }

    @Test
    fun wholeCoversAreTheDefaultTileAndShapesAreOptIn() {
        assertEquals(WidgetArtShape.ROUNDED, LibraryWidgetRemoteViewsFactory.tileShape(mixedShapes = false, position = 3))
        assertEquals(WidgetShapes.mixedShapeAt(3), LibraryWidgetRemoteViewsFactory.tileShape(mixedShapes = true, position = 3))
    }
}
