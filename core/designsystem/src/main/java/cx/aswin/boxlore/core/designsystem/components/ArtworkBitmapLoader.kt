package cx.aswin.boxlore.core.designsystem.components

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.model.artworkUrls
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Bounded loader shared by share cards, widgets and notification artwork. Cancellation propagates. */
suspend fun loadArtworkBitmap(
    context: Context,
    sources: List<String>,
    width: Int = 500,
    loader: ImageLoader = Coil.imageLoader(context),
    perAttemptTimeoutMillis: Long = 8_000,
    onSourceLoaded: (String) -> Unit = {},
): Bitmap? {
    require(perAttemptTimeoutMillis > 0)
    val pixels = imageTargetPixels(width)
    val attempts = ArtworkAttempts(artworkUrls(*sources.toTypedArray()), pixels)
    while (true) {
        val attempt = attempts.next() ?: return null
        if (attempt.recovery) delay(250)
        val result = withTimeoutOrNull(perAttemptTimeoutMillis) {
            loader.execute(
                ImageRequest.Builder(context).data(attempt.url).size(pixels, pixels)
                    .memoryCacheKey("${attempt.url}-w$pixels").allowHardware(false).build(),
            )
        }
        if (result is SuccessResult) {
            ArtworkLoadPolicy.Shared.recordSuccess(attempt.url, attempt.source)
            onSourceLoaded(attempt.source)
            return result.drawable.toBitmap(width = result.drawable.intrinsicWidth.coerceAtLeast(1), height = result.drawable.intrinsicHeight.coerceAtLeast(1))
        }
        attempts.failed(attempt, (result as? ErrorResult)?.throwable ?: java.io.IOException("Artwork request timed out"))
    }
}
