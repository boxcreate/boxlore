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
        val maxBodyWidth = BlobAvatarGeometry.computeBodyWidth(width, maxBreatheX)
        val maxBodyHeight = BlobAvatarGeometry.computeBodyHeight(height, maxBreatheY)

        // Maximum body dimensions must not exceed outer canvas bounds
        assertTrue("Max body width must be within canvas width", maxBodyWidth < width)
        assertTrue("Max body height must be within canvas height", maxBodyHeight < height)

        val maxFloatPx = BlobAvatarGeometry.computeFloatOffset(height, 1f)
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
        val (eyeWidth, eyeHeight) = BlobAvatarGeometry.computeEyeDimensions(width)
        val (maxShiftX, maxShiftY) = BlobAvatarGeometry.computeEyeMaxShifts(eyeWidth, eyeHeight)

        // Eye shift must stay strictly within the half dimensions of the eye
        assertTrue(
            "Max shift X ($maxShiftX) must not exceed half eye width (${eyeWidth / 2f})",
            maxShiftX < eyeWidth / 2f,
        )
        assertTrue(
            "Max shift Y ($maxShiftY) must not exceed half eye height (${eyeHeight / 2f})",
            maxShiftY < eyeHeight / 2f,
        )
        assertTrue(
            "Shift X ratio must be <= 0.5f",
            BlobAvatarGeometry.EYE_MAX_SHIFT_X_RATIO <= 0.5f,
        )
        assertTrue(
            "Shift Y ratio must be <= 0.5f",
            BlobAvatarGeometry.EYE_MAX_SHIFT_Y_RATIO <= 0.5f,
        )
    }

    @Test
    fun blobAvatar_blushSpacing_staysWithinBodyWidth() {
        val width = 250f
        val bodyWidth = BlobAvatarGeometry.computeBodyWidth(width)
        val blushSpacing = BlobAvatarGeometry.computeBlushSpacing(bodyWidth)
        val blushRadius = BlobAvatarGeometry.computeBlushRadius(width)

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

        val smallStroke = BlobAvatarGeometry.computeStrokeWidth(smallSize)
        val standardStroke = BlobAvatarGeometry.computeStrokeWidth(standardSize)
        val largeStroke = BlobAvatarGeometry.computeStrokeWidth(largeSize)

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

        val bodyWidth = BlobAvatarGeometry.computeBodyWidth(width, maxBreatheX)
        val bodyHeight = BlobAvatarGeometry.computeBodyHeight(height, maxBreatheY)

        val outerEarcupExtent = BlobAvatarGeometry.computeOuterEarCupExtent(width, bodyWidth, maxPulse)

        // Outer earcup edge must remain safely within half the canvas width
        assertTrue(
            "Outer earcup ($outerEarcupExtent) must stay within half canvas width (${width / 2f})",
            outerEarcupExtent < (width / 2f),
        )

        val maxFloatPx = BlobAvatarGeometry.computeFloatOffset(height, 1f)
        val minCenterY = (height / 2f) - maxFloatPx
        val bandTopY = BlobAvatarGeometry.computeHeadbandTopY(minCenterY, bodyHeight)
        val bandStroke = BlobAvatarGeometry.computeHeadbandStroke(width)
        val topBandEdge = bandTopY - (bandStroke / 2f)

        // Headband top must stay above zero (not clipped at top)
        assertTrue("Top edge of headband ($topBandEdge) must stay above 0", topBandEdge > 0f)
    }
}
