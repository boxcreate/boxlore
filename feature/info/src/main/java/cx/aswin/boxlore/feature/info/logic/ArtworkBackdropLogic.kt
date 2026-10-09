package cx.aswin.boxlore.feature.info.logic

/** Quintic falloff has zero slope and curvature at both ends, with a clear tail before clipping. */
internal fun artworkBackdropAlpha(position: Float): Float {
    if (!position.isFinite()) return 0f
    val t = ((position - 0.08f) / 0.84f).coerceIn(0f, 1f)
    val fade = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (1f - fade).coerceIn(0f, 1f)
}
