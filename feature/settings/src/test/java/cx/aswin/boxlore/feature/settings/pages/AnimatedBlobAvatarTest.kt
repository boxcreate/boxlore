package cx.aswin.boxlore.feature.settings.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedBlobAvatarTest {

    @Test
    fun blobAvatar_proportions_remainWithinCanvasBounds() {
        val width = 300f
        val height = 300f

        val maxBreatheX = 1.03f
        val maxBreatheY = 1.03f
        val maxBodyWidth = width * 0.88f * maxBreatheX
        val maxBodyHeight = height * 0.82f * maxBreatheY

        // Maximum body dimensions must not exceed outer canvas bounds
        assertTrue("Max body width must be within canvas width", maxBodyWidth < width)
        assertTrue("Max body height must be within canvas height", maxBodyHeight < height)

        val maxFloatPx = height * 0.045f
        val maxCenterY = (height / 2f) + maxFloatPx
        val minCenterY = (height / 2f) - maxFloatPx

        val topEdge = minCenterY - (maxBodyHeight / 2f)
        val bottomEdge = maxCenterY + (maxBodyHeight / 2f)

        assertTrue("Top edge of blob must stay above 0", topEdge > 0f)
        assertTrue("Bottom edge of blob must stay below height", bottomEdge < height)
    }

    @Test
    fun blobAvatar_pupilOffset_neverEscapesEyeBoundary() {
        val width = 200f
        val eyeRadius = width * 0.105f
        val pupilRadius = eyeRadius * 0.58f

        val maxShiftRatioX = 0.35f
        val maxShiftRatioY = 0.22f

        val maxPupilShiftX = eyeRadius * maxShiftRatioX
        val maxPupilShiftY = eyeRadius * maxShiftRatioY

        // Even at maximum look shift (ratio = 1.0), the pupil outer edge must stay inside the eye
        val maxPupilExtentX = maxPupilShiftX + pupilRadius
        val maxPupilExtentY = maxPupilShiftY + pupilRadius

        assertTrue(
            "Pupil X extent ($maxPupilExtentX) must not exceed eye radius ($eyeRadius)",
            maxPupilExtentX <= eyeRadius,
        )
        assertTrue(
            "Pupil Y extent ($maxPupilExtentY) must not exceed eye radius ($eyeRadius)",
            maxPupilExtentY <= eyeRadius,
        )
    }

    @Test
    fun blobAvatar_blushSpacing_staysWithinBodyWidth() {
        val width = 250f
        val bodyWidth = width * 0.88f
        val blushSpacing = bodyWidth * 0.32f
        val blushRadius = width * 0.08f

        val blushOuterEdge = blushSpacing + blushRadius
        val bodyHalfWidth = bodyWidth / 2f

        assertTrue(
            "Blush outer edge ($blushOuterEdge) must stay within body half-width ($bodyHalfWidth)",
            blushOuterEdge < bodyHalfWidth,
        )
    }

    @Test
    fun blobAvatar_strokeWidth_scalesWithAvatarDimension() {
        val smallSize = 40f
        val standardSize = 200f
        val largeSize = 400f

        val smallStroke = (smallSize * 0.032f).coerceAtLeast(1.5f)
        val standardStroke = (standardSize * 0.032f).coerceAtLeast(1.5f)
        val largeStroke = (largeSize * 0.032f).coerceAtLeast(1.5f)

        assertEquals(1.5f, smallStroke, 0.001f)
        assertEquals(6.4f, standardStroke, 0.001f)
        assertEquals(12.8f, largeStroke, 0.001f)
        assertTrue(largeStroke > standardStroke)
        assertTrue(standardStroke > smallStroke)
    }

    @Test
    fun blobAvatar_headphones_remainWithinCanvasBounds() {
        val width = 200f
        val height = 200f
        val maxPulse = 1.06f
        val maxBreatheX = 1.02f
        val maxBreatheY = 1.02f

        val bodyWidth = width * 0.72f * maxBreatheX
        val bodyHeight = height * 0.68f * maxBreatheY

        val cupSpacingX = bodyWidth * 0.49f
        val cupWidth = width * 0.15f * maxPulse
        val outerEarcupExtent = cupSpacingX + (cupWidth * 0.50f)

        // Outer earcup edge must remain safely within half the canvas width
        assertTrue(
            "Outer earcup ($outerEarcupExtent) must stay within half canvas width (${width / 2f})",
            outerEarcupExtent < (width / 2f),
        )

        val maxFloatPx = height * 0.04f
        val minCenterY = (height / 2f) - maxFloatPx
        val bandTopY = minCenterY - (bodyHeight * 0.54f)
        val bandStroke = (width * 0.065f).coerceAtLeast(3f)
        val topBandEdge = bandTopY - (bandStroke / 2f)

        // Headband top must stay above zero (not clipped at top)
        assertTrue("Top edge of headband ($topBandEdge) must stay above 0", topBandEdge > 0f)
    }
}
