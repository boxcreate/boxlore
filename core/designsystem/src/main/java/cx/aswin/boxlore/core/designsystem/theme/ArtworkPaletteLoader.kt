package cx.aswin.boxlore.core.designsystem.theme

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import cx.aswin.boxlore.core.designsystem.components.OnScreenArtwork
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PaletteSizePx = 128
private const val PaletteColours = 16
private const val AverageSizePx = 8

/** One process-wide cache keyed by artwork URL, so a cover read for a page also colours the player. */
private val artworkPalettes = LruCache<String, ArtworkPalette>(96)

/** The palette already read for this cover, without loading anything. */
fun cachedArtworkPalette(url: String): ArtworkPalette? = artworkPalettes[url]

/**
 * Reads a cover's palette, quantising off the main thread. The copy already on screen (in Coil's
 * memory cache, from the card the listener tapped) is used first, so this is usually immediate;
 * otherwise a small copy loads (the resized proxy, then the original). Null when the art cannot
 * load or has no meaningful colour.
 */
suspend fun loadArtworkPalette(context: Context, url: String): ArtworkPalette? {
    cachedArtworkPalette(url)?.let { return it }
    val onScreen = OnScreenArtwork.memoryKey(url)?.let { context.imageLoader.memoryCache?.get(it)?.bitmap }
    if (onScreen != null) return withContext(Dispatchers.Default) { readOnScreenPalette(onScreen) }?.also { artworkPalettes.put(url, it) }
    val proxy = url.optimizedImageUrl(PaletteSizePx)
    suspend fun request(data: String) =
        context.imageLoader.execute(ImageRequest.Builder(context).data(data).size(PaletteSizePx).allowHardware(false).build())
    val optimized = request(proxy)
    val response = if (optimized !is SuccessResult && proxy != url) request(url) else optimized
    if (response !is SuccessResult) return null
    return withContext(Dispatchers.Default) {
        readArtworkPalette(response.drawable.toBitmap(PaletteSizePx, PaletteSizePx))?.also { artworkPalettes.put(url, it) }
    }
}

/** On-screen bitmaps are usually hardware bitmaps, so read a small software copy, leaving the cached one untouched. */
private fun readOnScreenPalette(bitmap: Bitmap): ArtworkPalette? {
    val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null else bitmap
    val small = Bitmap.createScaledBitmap(software, PaletteSizePx, PaletteSizePx, true)
    return try {
        readArtworkPalette(small)
    } finally {
        if (small !== bitmap && small !== software) small.recycle()
        if (software !== bitmap) software.recycle()
    }
}

private fun readArtworkPalette(bitmap: Bitmap): ArtworkPalette? {
    // Palette's default filter already drops near-black and near-white swatches before the picker runs.
    val swatches = Palette.from(bitmap).maximumColorCount(PaletteColours).generate().swatches
    return extractArtworkPalette(swatches.map { ArtworkColorCandidate(it.rgb, it.population) }, bitmap.averageColour())
}

private fun Bitmap.averageColour(): Int {
    val small = Bitmap.createScaledBitmap(this, AverageSizePx, AverageSizePx, true)
    val pixels = IntArray(AverageSizePx * AverageSizePx).also { small.getPixels(it, 0, AverageSizePx, 0, 0, AverageSizePx, AverageSizePx) }
    if (small !== this) small.recycle()
    fun channel(shift: Int) = pixels.sumOf { (it shr shift) and 0xFF } / pixels.size
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
