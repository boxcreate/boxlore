package cx.aswin.boxlore.feature.player.v2.logic

internal data class PlayerSheetReveal(
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullScale: Float,
    val entranceRemaining: Float,
)

/** The mini player's content has left by this point of the expansion. */
internal const val MiniContentExitEnd = 0.2f

private const val FullRevealStart = 0.16f
private const val FullRevealEnd = 0.56f
private const val CompactFullRevealStart = 0.32f
private const val CompactFullRevealEnd = 0.78f
private const val FullEntranceScale = 0.95f

/**
 * A fade-through container transform, one reversible function of how far the sheet is open: the
 * mini player's content leaves first (by 20%), then the full player arrives once the sheet has
 * grown enough to hold it. Opening from the compact cookie, the full player waits longer, until the
 * container is past a third of the screen, so it never appears cramped in a small box.
 * [compactness] is how far the mini player had shrunk into the cookie (0 for the regular bar).
 */
internal fun calculatePlayerSheetReveal(expansion: Float, compactness: Float = 0f): PlayerSheetReveal {
    val fraction = expansion.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val compact = compactness.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val start = FullRevealStart + (CompactFullRevealStart - FullRevealStart) * compact
    val end = FullRevealEnd + (CompactFullRevealEnd - FullRevealEnd) * compact
    val exit = smoothStep(fraction / MiniContentExitEnd)
    val reveal = smoothStep((fraction - start) / (end - start))
    return PlayerSheetReveal(
        miniAlpha = 1f - exit,
        fullAlpha = reveal,
        fullScale = FullEntranceScale + (1f - FullEntranceScale) * reveal,
        entranceRemaining = 1f - reveal,
    )
}

/**
 * How much of the collapsed corner rounding the sheet keeps: all of it through the first half of
 * the expansion, so the moving sheet still reads as one card, then easing to square at full screen.
 */
internal fun playerCornerHold(expansion: Float): Float {
    val fraction = expansion.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return 1f - smoothStep((fraction - 0.5f) / 0.5f)
}

/**
 * Horizontal growth runs ahead of the vertical, so the bar opens into a sheet rather than a
 * stretched bar; the height keeps following the finger exactly.
 */
internal fun playerSheetWiden(expansion: Float): Float {
    val fraction = expansion.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return 1f - (1f - fraction) * (1f - fraction)
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
