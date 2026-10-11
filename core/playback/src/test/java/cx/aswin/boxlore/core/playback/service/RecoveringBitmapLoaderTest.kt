package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecoveringBitmapLoaderTest {
    @Test
    fun `failed results expire while successful and in flight results are reused`() {
        var now = 0L
        val delegate = FakeLoader()
        val loader = RecoveringBitmapLoader(delegate) { now }
        val uri = Uri.parse("https://art/episode")
        val first = loader.loadBitmap(uri)
        assertSame(first, loader.loadBitmap(uri))
        delegate.pending.setException(java.io.IOException("Offline"))
        now = 29_999
        assertSame(first, loader.loadBitmap(uri))
        assertEquals(1, delegate.calls)
        now = 30_000
        val second = loader.loadBitmap(uri)
        assertEquals(2, delegate.calls)
        delegate.pending.set(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        now = 300_000
        assertSame(second, loader.loadBitmap(uri))
        assertEquals(2, delegate.calls)
    }

    @Test
    fun `cancelled requests are retried and cache retains only one source`() {
        val delegate = FakeLoader()
        val loader = RecoveringBitmapLoader(delegate)
        loader.loadBitmap(Uri.parse("https://art/one"))
        delegate.pending.cancel(false)
        loader.loadBitmap(Uri.parse("https://art/one"))
        assertEquals(2, delegate.calls)
        loader.loadBitmap(Uri.parse("https://art/two"))
        loader.loadBitmap(Uri.parse("https://art/one"))
        assertEquals(4, delegate.calls)
    }

    @Test
    fun `byte artwork retains a snapshot and failed decoding also expires`() {
        var now = 0L
        val delegate = FakeLoader()
        val loader = RecoveringBitmapLoader(delegate) { now }
        val bytes = byteArrayOf(1, 2)
        val first = loader.decodeBitmap(bytes)
        bytes[0] = 4
        assertSame(first, loader.decodeBitmap(byteArrayOf(1, 2)))
        delegate.pending.setException(IllegalArgumentException("Not an image"))
        now = 30_000
        loader.decodeBitmap(byteArrayOf(1, 2))
        assertEquals(2, delegate.calls)
    }

    private class FakeLoader : BitmapLoader {
        var calls = 0
        var pending = SettableFuture.create<Bitmap>()
        override fun supportsMimeType(mimeType: String): Boolean = true
        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = request()
        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = request()
        private fun request(): ListenableFuture<Bitmap> {
            calls += 1
            pending = SettableFuture.create()
            return pending
        }
    }
}
