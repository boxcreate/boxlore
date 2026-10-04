package cx.aswin.boxlore.feature.player.v2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerShapeTest {
    private val size = Size(52f, 52f)
    private val center = Offset(26f, 26f)

    @Test
    fun regularAndCompactArtworkKeepTheSameCookieContourThroughoutContraction() {
        val regularSize = MiniPlayerArtworkSize.value
        val compactSize = MiniPlayerCompactArtworkSize.value
        for (morphStep in 0..10) {
            val artworkSize = regularSize + (compactSize - regularSize) * morphStep / 10f
            for (step in 0..144) {
                val fraction = step / 144f
                val regular = compactPlayerContourPoint(Size(regularSize, regularSize), fraction, 1f) / regularSize
                val contracting = compactPlayerContourPoint(Size(artworkSize, artworkSize), fraction, 1f) / artworkSize
                assertEquals(regular.x, contracting.x, 0.0001f)
                assertEquals(regular.y, contracting.y, 0.0001f)
            }
        }
        val crest = compactPlayerContourPoint(Size(regularSize, regularSize), 0.25f, 1f)
        val valley = compactPlayerContourPoint(Size(regularSize, regularSize), 0.25f + 1f / 18f, 1f)
        val artworkCenter = Offset(regularSize / 2f, regularSize / 2f)
        assertTrue((crest - artworkCenter).getDistance() - (valley - artworkCenter).getDistance() > 2f)
    }

    @Test
    fun compactCookieHasNineShallowLobes() {
        for (lobe in 0..8) {
            val angle = lobe * 2f * PI.toFloat() / 9f
            val crest = compactPlayerContourPoint(size, 0.25f + lobe / 9f, 1f)
            assertEquals(26f + 26f * cos(angle), crest.x, 0.001f)
            assertEquals(26f + 26f * sin(angle), crest.y, 0.001f)
        }
        val valley = compactPlayerContourPoint(size, 0.25f + 1f / 18f, 1f)
        assertEquals(26f * 0.94f / 1.06f, (valley - center).getDistance(), 0.001f)
    }

    @Test
    fun scallopsGrowFromCircleDuringDescentWithoutChangingEnvelope() {
        val valleyProgress = 0.25f + 1f / 18f
        val circle = compactPlayerContourPoint(size, valleyProgress, 0f)
        val midway = compactPlayerContourPoint(size, valleyProgress, 0.5f)
        val cookie = compactPlayerContourPoint(size, valleyProgress, 1f)
        assertEquals(26f, (circle - center).getDistance(), 0.001f)
        assertTrue((midway - center).getDistance() < (circle - center).getDistance())
        assertTrue((cookie - center).getDistance() < (midway - center).getDistance())
        for (shapeSize in listOf(size, Size(48f, 48f), Size(80f, 52f), Size.Zero)) {
            for (step in 0..144) {
                val point = compactPlayerContourPoint(shapeSize, step / 144f, 1f)
                assertTrue(point.x >= -0.001f && point.x <= shapeSize.width + 0.001f)
                assertTrue(point.y >= -0.001f && point.y <= shapeSize.height + 0.001f)
            }
        }
    }

    @Test
    fun insetProgressOutlineKeepsSameLobeAnglesAsArtwork() {
        for (step in 0..144) {
            val fraction = step / 144f
            val art = compactPlayerContourPoint(size, fraction, 1f) - center
            val ring = compactPlayerContourPoint(size, fraction, 1f, inset = 1f) - center
            assertEquals(25f / 26f, ring.getDistance() / art.getDistance(), 0.001f)
            assertEquals(0f, art.x * ring.y - art.y * ring.x, 0.001f)
        }
        val start = compactPlayerContourPoint(size, 0f, 1f)
        val end = compactPlayerContourPoint(size, 1f, 1f)
        assertEquals(start.x, end.x, 0.001f)
        assertEquals(start.y, end.y, 0.001f)
        assertEquals(26f, start.x, 0.001f)
        assertTrue(start.y < 3f)
    }

    @Test
    fun compactArtworkLeavesVisibleSpaceForTheFullProgressStroke() {
        val artSize = MiniPlayerCompactArtworkSize.value
        val stroke = MiniPlayerCompactProgressStroke.value
        val artCenter = Offset(artSize / 2f, artSize / 2f)
        for (rotation in listOf(0f, CompactPlayerRotationPeriod / 4f, CompactPlayerRotationPeriod / 2f, CompactPlayerRotationPeriod * 0.75f)) {
            for (step in 0..144) {
                val fraction = step / 144f
                val art = compactPlayerContourPoint(Size(artSize, artSize), fraction, 1f, rotation = rotation) - artCenter
                val ring = compactPlayerContourPoint(size, fraction, 1f, inset = stroke / 2f + 0.25f, rotation = rotation) - center
                assertTrue(ring.getDistance() - stroke / 2f >= art.getDistance() + 1.5f)
                val surface = compactPlayerContourPoint(size, fraction, 1f, rotation = rotation) - center
                assertTrue(ring.getDistance() + stroke / 2f <= surface.getDistance() + 0.001f)
            }
        }
    }

    @Test
    fun movingLobesKeepProgressStartAtTwelveOClockAndArtworkUpright() {
        for (rotation in listOf(0f, CompactPlayerRotationPeriod / 4f, CompactPlayerRotationPeriod / 2f, CompactPlayerRotationPeriod)) {
            val start = compactPlayerContourPoint(size, 0f, 1f, rotation = rotation)
            val end = compactPlayerContourPoint(size, 1f, 1f, rotation = rotation)
            assertEquals(center.x, start.x, 0.001f)
            assertTrue(start.y < center.y)
            assertEquals(start.x, end.x, 0.001f)
            assertEquals(start.y, end.y, 0.001f)
        }
        val original = compactPlayerContourPoint(size, 0.3f, 1f)
        val moved = compactPlayerContourPoint(size, 0.3f, 1f, rotation = CompactPlayerRotationPeriod / 2f)
        assertTrue((original - moved).getDistance() > 1f)
        val completed = compactPlayerContourPoint(size, 0.3f, 1f, rotation = CompactPlayerRotationPeriod)
        assertEquals(original.x, completed.x, 0.001f)
        assertEquals(original.y, completed.y, 0.001f)
    }
}
