package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/** Retain one successful/in-flight result, but never retain a failed or cancelled request forever. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RecoveringBitmapLoader(
    private val delegate: BitmapLoader,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : BitmapLoader {
    private class Entry(val uri: Uri?, val data: ByteArray?, val future: ListenableFuture<Bitmap>, var failedAt: Long? = null)
    private var entry: Entry? = null

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    @Synchronized
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        entry?.takeIf { it.uri == uri && reusable(it) }?.let { return it.future }
        return store(Entry(uri, null, delegate.loadBitmap(uri)))
    }

    @Synchronized
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        entry?.takeIf { it.data?.contentEquals(data) == true && reusable(it) }?.let { return it.future }
        return store(Entry(null, data.copyOf(), delegate.decodeBitmap(data)))
    }

    private fun reusable(cached: Entry): Boolean {
        if (cached.future.isCancelled) return false
        return cached.failedAt?.let { clock() - it < FAILURE_COOLDOWN } ?: true
    }

    private fun store(next: Entry): ListenableFuture<Bitmap> {
        entry = next
        next.future.addListener({
            synchronized(this) {
                if (runCatching { Futures.getDone(next.future) }.isFailure) next.failedAt = clock()
            }
        }, MoreExecutors.directExecutor())
        return next.future
    }

    companion object {
        private const val FAILURE_COOLDOWN = 30_000L
    }
}
