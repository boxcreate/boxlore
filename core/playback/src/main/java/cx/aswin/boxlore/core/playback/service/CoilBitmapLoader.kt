package cx.aswin.boxlore.core.playback.service

import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CoilBitmapLoader(private val context: android.content.Context, private val serviceScope: CoroutineScope, private val bitmapDispatcher: CoroutineDispatcher = Dispatchers.IO, private val fallbackSources: suspend (android.net.Uri) -> List<String> = { emptyList() }, private val artworkFetcher: (suspend (String) -> android.graphics.Bitmap?)? = null,) : androidx.media3.common.util.BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = serviceScope.future(bitmapDispatcher) {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        requireNotNull(bitmap) { "Could not decode bitmap" }
    }

    override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = serviceScope.future(bitmapDispatcher) {
        val sources = cx.aswin.boxlore.core.model.artworkUrls(uri.toString(), *fallbackSources(uri).toTypedArray()).take(4)
        var retrySource: String? = null
        for (source in sources) {
            val result = runCatching { kotlinx.coroutines.withTimeoutOrNull(8_000) { fetchSource(source) } }
            result.getOrNull()?.let { return@future it }
            val error = result.exceptionOrNull()
            if (error is java.util.concurrent.CancellationException) throw error
            val transient = isTransientFailure(error)
            if (transient && retrySource == null) retrySource = source
        }
        retrySource?.let { source ->
            kotlinx.coroutines.delay(250)
            val bitmap = kotlinx.coroutines.withTimeoutOrNull(8_000) { fetchSource(source) }
            if (bitmap != null) return@future bitmap
        }
        throw IllegalArgumentException("Artwork unavailable")
    }
    private fun isTransientFailure(error: Throwable?): Boolean = when (error) {
        is coil.network.HttpException -> error.response.code == 408 || error.response.code >= 500
        is android.graphics.ImageDecoder.DecodeException -> false
        is java.io.IOException, null -> true
        else -> false
    }

    private suspend fun fetchSource(source: String): android.graphics.Bitmap? {
        artworkFetcher?.let { return it(source) }
        return when (
            val result = coil.Coil.imageLoader(context).execute(
            coil.request.ImageRequest.Builder(context).data(source).size(512, 512).allowHardware(false).build(),
        )
        ) {
            is coil.request.SuccessResult -> result.drawable.toBitmap()
            is coil.request.ErrorResult -> throw result.throwable
        }
    }
}
