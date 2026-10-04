package cx.aswin.boxlore.feature.player.v2.logic

internal data class PlayerSheetReveal(
    val miniAlpha: Float,
    val fullAlpha: Float,
    val fullScale: Float,
    val entranceRemaining: Float,
)

/** One reversible reveal follows the sheet, with no independent animations lagging a drag. */
internal fun calculatePlayerSheetReveal(expansion: Float): PlayerSheetReveal {
    val fraction = expansion.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val phase = ((fraction - 0.06f) / 0.38f).coerceIn(0f, 1f)
    val reveal = phase * phase * (3f - 2f * phase)
    return PlayerSheetReveal(
        miniAlpha = 1f - reveal,
        fullAlpha = reveal,
        fullScale = 0.975f + 0.025f * reveal,
        entranceRemaining = 1f - reveal,
    )
}
