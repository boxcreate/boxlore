package cx.aswin.boxlore.feature.info.logic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import cx.aswin.boxlore.core.designsystem.theme.artworkContrast

/** Quintic falloff has zero slope and curvature at both ends, with a clear tail before clipping. */
internal fun artworkBackdropAlpha(position: Float): Float {
    if (!position.isFinite()) return 0f
    val t = ((position - 0.08f) / 0.84f).coerceIn(0f, 1f)
    val fade = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (1f - fade).coerceIn(0f, 1f)
}

/** The backdrop's strength before a cover's colours are known, and for covers without a palette. */
internal const val DefaultInfoBackdropStrength = 0.48f
private const val StrongestBackdrop = 0.75f
private const val FaintestBackdrop = 0.3f
private const val BackdropStep = 0.05f
private const val HeaderTextContrast = 4.5f

/**
 * How strongly the blurred cover shows behind the header: as strong as it can be while header text
 * over the cover's mean colour still clears 4.5:1, and never fainter than a hint of the art.
 */
internal fun artworkBackdropStrength(average: Color, background: Color, text: Color): Float {
    var strength = StrongestBackdrop
    while (strength > FaintestBackdrop && artworkContrast(text, lerp(background, average, strength)) < HeaderTextContrast) {
        strength -= BackdropStep
    }
    return strength.coerceAtLeast(FaintestBackdrop)
}
