package cx.aswin.boxlore.core.designsystem.components

/** Callers already supply pixels; use the same bounded target for CDN and decode. */
internal fun imageTargetPixels(requestedPixels: Int): Int = requestedPixels.coerceIn(10, 2048)

/** Bucket upgrades avoid making a new network/cache entry for every resize pixel. */
internal fun layoutImageTargetPixels(requestedPixels: Int, measuredPixels: Int): Int {
    val requested = imageTargetPixels(requestedPixels)
    if (measuredPixels <= requested) return requested
    return listOf(128, 256, 400, 512, 640, 800, 1024, 1536, 2048)
        .firstOrNull { it >= measuredPixels } ?: 2048
}
