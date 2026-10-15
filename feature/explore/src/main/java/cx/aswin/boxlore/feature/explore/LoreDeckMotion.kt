package cx.aswin.boxlore.feature.explore

/** Cards drawn behind the active one. */
internal const val LoreDeckDepth = 2

internal data class LoreDeckPose(val translationYDp: Float, val scale: Float, val rotation: Float, val alpha: Float = 1f)

/** Resting poses by depth: the active card, two visible cards behind it, then a hidden waiting card. */
private val LoreDeckRestingPoses = listOf(
    LoreDeckPose(translationYDp = 0f, scale = 1f, rotation = 0f),
    LoreDeckPose(translationYDp = 13f, scale = 0.965f, rotation = -1.15f),
    LoreDeckPose(translationYDp = 24f, scale = 0.93f, rotation = 1.8f),
    LoreDeckPose(translationYDp = 32f, scale = 0.9f, rotation = 0f, alpha = 0f),
)

/**
 * A card behind the active one moves from its resting pose toward the pose of the card in front
 * as the active card is swiped ([progress] 0..1). At a full swipe it sits exactly where that card
 * rested, so when the deck advances nothing snaps; the hidden fourth card fades into third place.
 */
internal fun loreDeckPose(depth: Int, progress: Float): LoreDeckPose {
    val index = depth.coerceIn(0, LoreDeckRestingPoses.lastIndex)
    if (index == 0) return LoreDeckRestingPoses[0]
    val from = LoreDeckRestingPoses[index]
    val to = LoreDeckRestingPoses[index - 1]
    val t = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

    // Weighted form, so a full swipe lands exactly on the next resting pose.
    fun mix(a: Float, b: Float) = a * (1f - t) + b * t
    return LoreDeckPose(
        translationYDp = mix(from.translationYDp, to.translationYDp),
        scale = mix(from.scale, to.scale),
        rotation = mix(from.rotation, to.rotation),
        alpha = mix(from.alpha, to.alpha),
    )
}
