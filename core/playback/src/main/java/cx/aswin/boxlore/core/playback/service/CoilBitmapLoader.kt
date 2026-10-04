package cx.aswin.boxlore.core.playback.service

import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CoilBitmapLoader(private val context: android.content.Context, private val serviceScope: CoroutineScope, private val bitmapDispatcher: CoroutineDispatcher = Dispatchers.IO,) : androidx.media3.common.util.BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = serviceScope.future(bitmapDispatcher) {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        requireNotNull(bitmap) { "Could not decode bitmap" }
    }

    override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> = serviceScope.future(bitmapDispatcher) {
        try {
            android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap started for $uri")
            val loader = coil.Coil.imageLoader(context)
            val request =
                coil.request.ImageRequest
                    .Builder(context)
                    .data(uri)
                    .size(512, 512)
                    .allowHardware(false) // Required: system notifications cannot use hardware-backed bitmaps
                    .build()
            val result = loader.execute(request)
            val bitmap = (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
            if (bitmap != null) {
                android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap succeeded for $uri")
                bitmap
            } else {
                val errorMsg =
                    "CoilBitmapLoader: result is not a success or drawable could not be converted to bitmap for $uri"
                android.util.Log.e("BoxCastPlayer", errorMsg)
                throw IllegalArgumentException(errorMsg)
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastPlayer", "CoilBitmapLoader: loadBitmap failed for $uri", e)
            throw e
        }
    }
}
