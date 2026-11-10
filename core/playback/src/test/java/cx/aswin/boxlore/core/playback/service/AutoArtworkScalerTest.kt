package cx.aswin.boxlore.core.playback.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoArtworkScalerTest {
    @Test
    fun `a 3000 pixel cover is decoded at a quarter size for a 512 pixel target`() {
        // 3000 / 4 = 750 still covers 512; one more halving (375) would fall short.
        assertEquals(4, AutoArtworkScaler.sampleSizeFor(3000, 3000, 512))
    }

    @Test
    fun `a cover near the target is decoded whole`() {
        assertEquals(1, AutoArtworkScaler.sampleSizeFor(600, 600, 512))
        assertEquals(1, AutoArtworkScaler.sampleSizeFor(1023, 400, 512))
    }

    @Test
    fun `the longest side decides, so a wide banner is not left oversized`() {
        assertEquals(2, AutoArtworkScaler.sampleSizeFor(1400, 300, 512))
    }

    @Test
    fun `bytes that are not an image come back untouched`() {
        val notAnImage = "not an image".toByteArray()

        assertArrayEquals(notAnImage, AutoArtworkScaler.scaleForCar(notAnImage))
    }
}
