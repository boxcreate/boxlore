package cx.aswin.boxlore.core.playback

/**
 * Pure helpers for Android Auto remote artwork fetch validation.
 * Keeps ContentProvider networking policy hermetically testable.
 */
object AutoArtworkFetchLogic {
    const val MAX_ARTWORK_BYTES = 8L * 1024L * 1024L
    const val MAX_REDIRECTS = 5
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 12_000

    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG =
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    private val GIF87A = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val GIF89A = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)

    fun isAcceptableImageContentType(contentType: String?): Boolean {
        val normalized =
            contentType
                .orEmpty()
                .substringBefore(';')
                .trim()
                .lowercase()
        if (normalized.isEmpty()) return true
        if (normalized.startsWith("image/")) return true
        // Many podcast CDNs omit a precise image type.
        return normalized == "application/octet-stream" ||
            normalized == "binary/octet-stream" ||
            normalized == "application/binary"
    }

    fun looksLikeImage(header: ByteArray): Boolean {
        if (header.size < 3) return false
        if (startsWith(header, JPEG)) return true
        if (header.size >= 8 && startsWith(header, PNG)) return true
        if (header.size >= 6 && (startsWith(header, GIF87A) || startsWith(header, GIF89A))) {
            return true
        }
        return header.size >= 12 &&
            startsWith(header, RIFF) &&
            header.copyOfRange(8, 12).contentEquals(WEBP)
    }

    fun shouldAcceptArtwork(
        responseCode: Int,
        contentType: String?,
        contentLength: Long,
        headerBytes: ByteArray? = null,
    ): Boolean {
        if (responseCode !in 200..299) return false
        if (contentLength > MAX_ARTWORK_BYTES) return false
        if (!isAcceptableImageContentType(contentType)) return false
        if (headerBytes != null && headerBytes.isNotEmpty() && !looksLikeImage(headerBytes)) {
            return false
        }
        return true
    }

    fun isRedirect(responseCode: Int): Boolean = responseCode in setOf(301, 302, 303, 307, 308)

    private fun startsWith(
        bytes: ByteArray,
        prefix: ByteArray,
    ): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return false
        }
        return true
    }
}
