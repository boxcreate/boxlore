package cx.aswin.boxlore.core.designsystem.components

/** Callers already supply pixels; use the same bounded target for CDN and decode. */
internal fun imageTargetPixels(requestedPixels: Int): Int = requestedPixels.coerceIn(10, 2048)
