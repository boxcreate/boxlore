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
        val (blushSpacing, blushRadius) = BlobAvatarGeometry.computeBlushParams(width, bodyWidth)

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

        val (cupSpacing, cupWidth) = BlobAvatarGeometry.computeCupParams(width, bodyWidth, maxPulse)
        val outerEarcupExtent = cupSpacing + (cupWidth * 0.50f)

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

    @Test
    fun blobAvatar_soundwaveRadius_andAlpha_behaveDeterministically() {
        val width = 200f
        val r0 = BlobAvatarGeometry.computeSoundwaveRadius(width, 0f)
        val r1 = BlobAvatarGeometry.computeSoundwaveRadius(width, 1f)
        assertTrue("Soundwave radius at 1f ($r1) must exceed radius at 0f ($r0)", r1 > r0)

        val a0 = BlobAvatarGeometry.computeSoundwaveAlpha(0f)
        val a1 = BlobAvatarGeometry.computeSoundwaveAlpha(1f)
        assertEquals(0.65f, a0, 0.001f)
        assertEquals(0.0f, a1, 0.001f)

        // Middle progress
        val rMid = BlobAvatarGeometry.computeSoundwaveRadius(width, 0.5f)
        assertTrue(rMid > r0 && rMid < r1)
    }

    @Test
    fun blobAvatar_squishScaleX_preservesVolume() {
        val normalX = BlobAvatarGeometry.computeSquishScaleX(1.0f)
        assertEquals(1.0f, normalX, 0.001f)

        val squishedX = BlobAvatarGeometry.computeSquishScaleX(0.80f)
        assertTrue("When squished vertically (0.8f), scaleX must expand (> 1.0f)", squishedX > 1.0f)

        val stretchedX = BlobAvatarGeometry.computeSquishScaleX(1.15f)
        assertTrue("When stretched vertically (1.15f), scaleX must contract (< 1.0f)", stretchedX < 1.0f)
    }

    @Test
    fun blobAvatar_genreMoods_provideDistinctPalettesAndCycleCleanly() {
        val moods = BlobAvatarGenreMood.entries
        assertEquals("Must support 7 distinct podcast genre moods", 7, moods.size)

        for (mood in moods) {
            assertTrue("Mood ID must not be blank", mood.id.isNotBlank())
            assertTrue("Display name must not be blank", mood.displayName.isNotBlank())
            val nextMood = BlobAvatarGenreMood.next(mood)
            assertTrue("Next mood must be valid", moods.contains(nextMood))
        }

        // Verify full cycle returns to original
        var current = moods.first()
        repeat(moods.size) {
            current = BlobAvatarGenreMood.next(current)
        }
        assertEquals("Full cycle must return to start", moods.first(), current)
    }
}
