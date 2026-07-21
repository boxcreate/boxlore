package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Generates composite collage bitmaps from multiple podcast cover art images
 * for Android Auto grid tiles.
 *
 * Layout adapts to the number of available images:
 * - 4 images → 2×2 grid
 * - 3 images → 1 large left + 2 stacked right
 * - 2 images → side-by-side split
 * - 1 image  → full-bleed single cover
 * - 0 images → branded gradient with category label
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
        folderImages: Map<String, List<String>>, // folderId → list of image URLs (max 4)
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
                    )?.let { result ->
                        results[folderId] = result.uri
                    }
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

        // Probe cache freshness using the last known loaded count (stored in .meta).
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

        val built = createCollageBitmap(urls, folderId) ?: return null
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

    /**
     * Create a single collage bitmap from a list of image URLs.
     * Adapts layout based on how many images are available.
     */
    private suspend fun createCollageBitmap(
        imageUrls: List<String>,
        folderId: String,
    ): BuiltCollage? =
        coroutineScope {
            val size = COLLAGE_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bitmaps =
                imageUrls
                    .map { url ->
                        async(Dispatchers.IO) { downloadBitmap(url) }
                    }.awaitAll()
                    .filterNotNull()

            when (bitmaps.size) {
                0 -> drawBrandedFallback(canvas, size, folderId)
                1 -> drawSingleCover(canvas, size, bitmaps[0])
                2 -> drawTwoSplit(canvas, size, bitmaps)
                3 -> drawThreeLayout(canvas, size, bitmaps)
                else -> drawFourGrid(canvas, size, bitmaps)
            }
            when {
                folderId.contains("drive_mix") -> drawLabelBadge(canvas, size, "MIX")
                folderId.contains("continue") -> drawLabelBadge(canvas, size, "RESUME")
            }

            bitmaps.forEach { it.recycle() }
            BuiltCollage(bitmap = bitmap, loadedImageCount = bitmaps.size)
        }

    private fun drawLabelBadge(
        canvas: Canvas,
        size: Int,
        label: String,
    ) {
        val padding = size * 0.04f
        val badgeWidth = size * if (label.length > 3) 0.42f else 0.28f
        val badgeHeight = size * 0.14f
        val badge =
            RectF(
                size - badgeWidth - padding,
                size - badgeHeight - padding,
                size - padding,
                size - padding,
            )
        canvas.drawRoundRect(
            badge,
            size * 0.035f,
            size * 0.035f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D91A1A2E")
            },
        )
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size * 0.065f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        val centerY = badge.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, badge.centerX(), centerY, textPaint)
    }

    private fun drawSingleCover(
        canvas: Canvas,
        size: Int,
        bmp: Bitmap,
    ) {
        drawCenterCrop(canvas, bmp, Rect(0, 0, size, size))
    }

    private fun drawTwoSplit(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val gap = 3

        for (i in 0..1) {
            val srcBmp = bitmaps[i]
            val left = if (i == 0) 0 else halfW + gap
            drawCenterCrop(canvas, srcBmp, Rect(left, 0, size.takeIf { i == 1 } ?: halfW, size))
        }
    }

    private fun drawThreeLayout(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        drawCenterCrop(canvas, bitmaps[0], Rect(0, 0, halfW, size))
        drawCenterCrop(canvas, bitmaps[1], Rect(halfW + gap, 0, size, halfH))
        drawCenterCrop(canvas, bitmaps[2], Rect(halfW + gap, halfH + gap, size, size))
    }

    private fun drawFourGrid(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        val positions =
            listOf(
                0f to 0f,
                (halfW + gap).toFloat() to 0f,
                0f to (halfH + gap).toFloat(),
                (halfW + gap).toFloat() to (halfH + gap).toFloat(),
            )

        for (i in 0 until minOf(4, bitmaps.size)) {
            val (x, y) = positions[i]
            drawCenterCrop(
                canvas,
                bitmaps[i],
                Rect(x.toInt(), y.toInt(), (x + halfW).toInt(), (y + halfH).toInt()),
            )
        }
    }

    private fun drawBrandedFallback(
        canvas: Canvas,
        size: Int,
        folderId: String,
    ) {
        val gradient =
            LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.parseColor("#1a1a2e"),
                Color.parseColor("#16213e"),
                Shader.TileMode.CLAMP,
            )
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val label =
            when {
                folderId.contains("continue") -> "PLAY"
                folderId.contains("download") -> "OFFLINE"
                folderId.contains("drive_mix") -> "MIX"
                folderId.contains("time_picks") -> "NOW"
                folderId.contains("genres") -> "GENRES"
                folderId.contains("discover") -> "DISCOVER"
                folderId.contains("library") -> "LIBRARY"
                else -> "BOXLORE"
            }
        val textPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = size * 0.1f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
        canvas.drawText(label, size / 2f, size / 2f + textSize(textPaint) / 3f, textPaint)
    }

    private fun textSize(paint: Paint): Float = paint.textSize

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: Rect,
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width().toFloat() / destination.height().toFloat()
        val source =
            if (sourceRatio > destinationRatio) {
                val width = (bitmap.height * destinationRatio).toInt()
                val left = (bitmap.width - width) / 2
                Rect(left, 0, left + width, bitmap.height)
            } else {
                val height = (bitmap.width / destinationRatio).toInt()
                val top = (bitmap.height - height) / 2
                Rect(0, top, bitmap.width, top + height)
            }
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * Download a bitmap from URL with hardened timeouts + redirect validation.
     * Returns null on failure (never throws).
     */
    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            if (urlString.startsWith("/") || urlString.startsWith("file://")) {
                val path = urlString.removePrefix("file://")
                return BitmapFactory.decodeFile(path)
            }
            val bytes =
                downloadBytes(URL(urlString.replace("http://", "https://")))
                    ?: return null
            if (!AutoArtworkFetchLogic.looksLikeImage(bytes.copyOf(minOf(12, bytes.size)))) {
                return null
            }
            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 300) sampleSize *= 2
            val decodeOpts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download: $urlString", e)
            null
        }
    }

    private fun downloadBytes(startUrl: URL): ByteArray? {
        var current = startUrl
        var redirects = 0
        while (redirects <= AutoArtworkFetchLogic.MAX_REDIRECTS) {
            if (current.protocol != "https" || !isPublicHttpsUrl(current)) return null
            var connection: HttpURLConnection? = null
            try {
                connection = current.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = AutoArtworkFetchLogic.CONNECT_TIMEOUT_MS
                connection.readTimeout = AutoArtworkFetchLogic.READ_TIMEOUT_MS
                connection.doInput = true
                connection.connect()
                val code = connection.responseCode
                if (AutoArtworkFetchLogic.isRedirect(code)) {
                    val location = connection.getHeaderField("Location") ?: return null
                    val next =
                        runCatching {
                            java.net.URI(current.toURI().resolve(location).toString()).toURL()
                        }.getOrNull() ?: return null
                    redirects += 1
                    current = next
                    continue
                }
                if (
                    !AutoArtworkFetchLogic.shouldAcceptArtwork(
                        code,
                        connection.contentType,
                        connection.contentLengthLong,
                    )
                ) {
                    return null
                }
                return connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > AutoArtworkFetchLogic.MAX_ARTWORK_BYTES) return@use null
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray().takeIf { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download failed for $current", e)
                return null
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    private fun isPublicHttpsUrl(url: URL): Boolean {
        if (url.protocol != "https" || (url.port != -1 && url.port != 443)) return false
        val addresses =
            runCatching { InetAddress.getAllByName(url.host) }.getOrNull()
                ?: return false
        return addresses.isNotEmpty() &&
            addresses.all { address ->
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress &&
                    !address.isUniqueLocalIpv6()
            }
    }

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
