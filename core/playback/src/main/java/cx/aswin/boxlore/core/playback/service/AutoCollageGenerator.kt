package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import cx.aswin.boxlore.core.playback.AutoArtworkFetchLogic
import cx.aswin.boxlore.core.playback.AutoCollageFreshnessLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Generates composite collage bitmaps from multiple podcast cover art images
 * for Android Auto grid tiles.
 *
 * Layout adapts to the number of available images via [AutoCollageLayouts].
 */
object AutoCollageGenerator {
    private const val TAG = "AutoCollage"
    private const val COLLAGE_SIZE = 512 // px, square

    data class CollageResult(
        val uri: Uri,
        val signature: String,
        val loadedImageCount: Int,
        val expectedImageCount: Int,
    )

    /**
     * Pre-generate Home / Library / Discover collages and return folder ID → content URI.
     * URIs include a `v=` cache-buster so Android Auto reloads when content keys change.
     */
    suspend fun generateAllCollages(
        context: Context,
        folderImages: Map<String, List<String>>,
        folderContentKeys: Map<String, List<String>> = emptyMap(),
    ): Map<String, Uri> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, Uri>()
            val cacheDir = File(context.cacheDir, "auto_collages").apply { mkdirs() }
            for ((folderId, imageUrls) in folderImages) {
                try {
                    generateOneCollage(
                        context = context,
                        cacheDir = cacheDir,
                        folderId = folderId,
                        imageUrls = imageUrls,
                        contentKeys = folderContentKeys[folderId].orEmpty(),
                    )?.let { result -> results[folderId] = result.uri }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate collage for $folderId", e)
                }
            }
            results
        }

    private suspend fun generateOneCollage(
        context: Context,
        cacheDir: File,
        folderId: String,
        imageUrls: List<String>,
        contentKeys: List<String>,
    ): CollageResult? {
        val safeName = folderId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val outFile = File(cacheDir, "$safeName.png")
        val signatureFile = File(cacheDir, "$safeName.signature")
        val metaFile = File(cacheDir, "$safeName.meta")
        val urls = imageUrls.take(4).filter { it.isNotBlank() }
        val expected = urls.size
        val signatureValues = contentKeys.ifEmpty { urls }
        val cachedMeta = readMeta(metaFile)
        val provisionalSignature =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = signatureValues,
                loadedImageCount = cachedMeta?.first ?: expected,
                expectedImageCount = cachedMeta?.second ?: expected,
            )
        val ageMs =
            if (outFile.exists()) {
                System.currentTimeMillis() - outFile.lastModified()
            } else {
                Long.MAX_VALUE
            }
        val storedSignature = signatureFile.takeIf { it.isFile }?.readText()
        if (
            outFile.exists() &&
            AutoCollageFreshnessLogic.isFresh(
                ageMs = ageMs,
                storedSignature = storedSignature,
                currentSignature = provisionalSignature,
                loadedImageCount = cachedMeta?.first ?: expected,
                expectedImageCount = cachedMeta?.second ?: expected,
            )
        ) {
            return CollageResult(
                uri = AutoCollageProvider.getUri(context, "$safeName.png", provisionalSignature),
                signature = provisionalSignature,
                loadedImageCount = cachedMeta?.first ?: expected,
                expectedImageCount = cachedMeta?.second ?: expected,
            )
        }

        val built = createCollageBitmap(context, urls, folderId) ?: return null
        val loaded = built.loadedImageCount
        val signature =
            AutoCollageFreshnessLogic.buildSignature(
                contentKeysOrUrls = signatureValues,
                loadedImageCount = loaded,
                expectedImageCount = expected,
            )
        FileOutputStream(outFile).use { out ->
            built.bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        runCatching { signatureFile.writeText(signature) }
        runCatching { metaFile.writeText("$loaded\n$expected") }
        built.bitmap.recycle()
        Log.d(TAG, "Generated collage for $folderId (loaded=$loaded/$expected)")
        return CollageResult(
            uri = AutoCollageProvider.getUri(context, "$safeName.png", signature),
            signature = signature,
            loadedImageCount = loaded,
            expectedImageCount = expected,
        )
    }

    private fun readMeta(metaFile: File): Pair<Int, Int>? {
        if (!metaFile.isFile) return null
        val lines = runCatching { metaFile.readLines() }.getOrNull() ?: return null
        val loaded = lines.getOrNull(0)?.toIntOrNull() ?: return null
        val expected = lines.getOrNull(1)?.toIntOrNull() ?: return null
        return loaded to expected
    }

    private data class BuiltCollage(
        val bitmap: Bitmap,
        val loadedImageCount: Int,
    )

    private suspend fun createCollageBitmap(
        context: Context,
        imageUrls: List<String>,
        folderId: String,
    ): BuiltCollage? =
        coroutineScope {
            val size = COLLAGE_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bitmaps =
                imageUrls
                    .map { url -> async(Dispatchers.IO) { downloadBitmap(url) } }
                    .awaitAll()
                    .filterNotNull()
            AutoCollageLayouts.draw(context, canvas, size, folderId, bitmaps)
            bitmaps.forEach { it.recycle() }
            BuiltCollage(bitmap = bitmap, loadedImageCount = bitmaps.size)
        }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            if (urlString.startsWith("/") || urlString.startsWith("file://")) {
                return BitmapFactory.decodeFile(urlString.removePrefix("file://"))
            }
            val url = AutoArtworkDownloader.parseHttpsUrl(urlString) ?: return null
            val bytes = AutoArtworkDownloader.downloadHttpsBytes(url) ?: return null
            if (!AutoArtworkFetchLogic.looksLikeImage(bytes.copyOf(minOf(12, bytes.size)))) {
                return null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 300) sampleSize *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download: $urlString", e)
            null
        }
    }
}
