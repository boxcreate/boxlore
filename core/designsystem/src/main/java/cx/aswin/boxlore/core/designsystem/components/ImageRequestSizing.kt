package cx.aswin.boxlore.core.designsystem.components

import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.COMPACT_PHONE_MAX_WIDTH_DP

/** Callers already supply pixels; use the same bounded target for CDN and decode. */
internal fun imageTargetPixels(requestedPixels: Int): Int = requestedPixels.coerceIn(10, 2048)

/** Bucket upgrades avoid making a new network/cache entry for every resize pixel. */
internal fun layoutImageTargetPixels(requestedPixels: Int, measuredPixels: Int): Int {
    val requested = imageTargetPixels(requestedPixels)
    if (measuredPixels <= requested) return requested
    return listOf(128, 256, 400, 512, 640, 800, 1024, 1536, 2048)
        .firstOrNull { it >= measuredPixels } ?: 2048
}

/**
 * True where artwork is drawn larger than a portrait phone draws it (tablets, sideways phones, wide
 * split windows), so it loads at the measured size instead of the fixed phone request.
 */
internal fun artworkSizesToLayout(layout: BoxloreWindowLayout): Boolean =
    layout.usesTabletLayout || layout.widthDp > COMPACT_PHONE_MAX_WIDTH_DP

/** The pixels to request for artwork in [layout]: the measured bucket where it sizes to layout, else [requestedPixels]. */
internal fun artworkRequestPixels(layout: BoxloreWindowLayout, requestedPixels: Int, measuredPixels: Int): Int =
    if (artworkSizesToLayout(layout)) layoutImageTargetPixels(requestedPixels, measuredPixels) else imageTargetPixels(requestedPixels)
