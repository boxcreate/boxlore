package cx.aswin.boxlore.core.playback

/**
 * Pure collage cache-key / freshness policy for Android Auto folder tiles.
 */
object AutoCollageFreshnessLogic {
    const val SIGNATURE_VERSION = "collage-v4"

    /** Full collages with real tiles stay fresh this long unless content keys change. */
    const val FULL_TTL_MS = 2L * 60L * 60L * 1_000L

    /** Partial / fallback collages expire sooner so the next prewarm can recover art. */
    const val PARTIAL_TTL_MS = 20L * 60L * 1_000L

    fun buildSignature(
        contentKeysOrUrls: List<String>,
        loadedImageCount: Int,
        expectedImageCount: Int,
    ): String {
        val values =
            contentKeysOrUrls
                .take(4)
                .filter(String::isNotBlank)
                .joinToString("\n")
        return buildString {
            append(SIGNATURE_VERSION)
            append('\n')
            append(values)
            append("\nloaded=")
            append(loadedImageCount.coerceAtLeast(0))
            append("\nexpected=")
            append(expectedImageCount.coerceAtLeast(0))
        }.hashCode().toString()
    }

    fun isFresh(
        ageMs: Long,
        storedSignature: String?,
        currentSignature: String,
        loadedImageCount: Int,
        expectedImageCount: Int,
    ): Boolean {
        if (storedSignature.isNullOrBlank() || storedSignature != currentSignature) return false
        if (ageMs < 0L) return false
        val ttl =
            if (expectedImageCount > 0 && loadedImageCount < expectedImageCount) {
                PARTIAL_TTL_MS
            } else if (loadedImageCount <= 0 && expectedImageCount > 0) {
                PARTIAL_TTL_MS
            } else {
                FULL_TTL_MS
            }
        return ageMs < ttl
    }
}
