package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real images through the scaler: decoded, shrunk to the car's size and written back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoArtworkScalerImageTest {
    private fun cover(size: Int, format: Bitmap.CompressFormat, alpha: Boolean): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (alpha) Color.argb(128, 200, 40, 40) else Color.rgb(200, 40, 40))
        bitmap.setHasAlpha(alpha)
        return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
    }

    private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test
    fun aLargeCoverIsShrunkToTheCarsSizeAndGetsSmaller() {
        val original = cover(2_048, Bitmap.CompressFormat.JPEG, alpha = false)

        val scaled = AutoArtworkScaler.scaleForCar(original)

        assertEquals(AutoArtworkScaler.targetPx to AutoArtworkScaler.targetPx, sizeOf(scaled))
        assertTrue(scaled.size < original.size)
    }

    @Test
    fun aCoverWithTransparencyStaysAPng() {
        val scaled = AutoArtworkScaler.scaleForCar(cover(2_048, Bitmap.CompressFormat.PNG, alpha = true))

        assertEquals(AutoArtworkScaler.targetPx to AutoArtworkScaler.targetPx, sizeOf(scaled))
        // PNG files start with 0x89 'P' 'N' 'G'.
        assertEquals(listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), scaled.take(4))
    }

    @Test
    fun aCoverAlreadyAtTheCarsSizeIsLeftAsItIs() {
        val original = cover(AutoArtworkScaler.targetPx, Bitmap.CompressFormat.JPEG, alpha = false)

        assertArrayEquals(original, AutoArtworkScaler.scaleForCar(original))
    }
}
