package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Cover travel, restored from the pre-refresh welcome: each row glides a short distance on a soft
 * ease-out while a slow linear drift starts underneath it, so the motion settles without ever
 * stopping. Rows start a beat apart, and the covers fade up as they glide in.
 */
internal object WelcomeReelMotion {
    /** Entrance glide: a short, gentle travel (large travel read as a janky "loading" screen). */
    const val EntranceTravelDp = 120.0
    const val EntranceMillis = 1080L

    /** The drift starts while the glide is still settling, so velocity never reaches zero. */
    const val DriftDelayMillis = 700L

    /** One six-cover loop (732dp) every 40 seconds. */
    const val DriftSpeedDpPerSecond = 732.0 / 40.0

    /** Each following row starts its glide this much later. */
    const val RowStaggerMillis = 96L

    /** Covers fade up from this opacity over [FadeMillis]. */
    const val FadeFromAlpha = 0.55f
    const val FadeMillis = 960L

    /** After the last line of copy finishes its entry. */
    private const val TextRevealEndMillis = WelcomeEntryTiming.LastLineEndMillis

    private val GlideEasing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)
    private val FadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** Revealed copy never hides again if the system's animation setting changes mid-entry. */
    fun isRevealComplete(elapsedMillis: Long, animationsDisabled: Boolean, alreadyComplete: Boolean): Boolean =
        alreadyComplete || animationsDisabled || elapsedMillis >= TextRevealEndMillis

    fun distanceDp(elapsedMillis: Long, rowDelayMillis: Long = 0L): Double {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val glideProgress = ((elapsed - rowDelayMillis).toFloat() / EntranceMillis).coerceIn(0f, 1f)
        val glide = EntranceTravelDp * GlideEasing.transform(glideProgress)
        val drift = (elapsed - DriftDelayMillis).coerceAtLeast(0L) / 1000.0 * DriftSpeedDpPerSecond
        return glide + drift
    }

    fun coverAlpha(elapsedMillis: Long): Float {
        val progress = (elapsedMillis.coerceAtLeast(0L).toFloat() / FadeMillis).coerceIn(0f, 1f)
        return FadeFromAlpha + (1f - FadeFromAlpha) * FadeEasing.transform(progress)
    }

    /** Wraps a repeated row by whole cycles; the two endpoints show identical repeated content. */
    fun wrappedOffsetDp(
        distanceDp: Double,
        cycleWidthDp: Double,
        reverse: Boolean = false,
        phaseFraction: Double = 0.0,
    ): Float {
        if (!cycleWidthDp.isFinite() || cycleWidthDp <= 0.0 || cycleWidthDp > Float.MAX_VALUE) {
            return 0f
        }
        if (!distanceDp.isFinite() || !phaseFraction.isFinite()) {
            return 0f
        }

        val distanceWithinCycle = positiveRemainder(distanceDp, cycleWidthDp)
        val phaseWithinCycle = positiveRemainder(phaseFraction, 1.0) * cycleWidthDp
        val travelWithinCycle = (distanceWithinCycle + phaseWithinCycle) % cycleWidthDp
        val offset = if (reverse) {
            (travelWithinCycle - cycleWidthDp).toFloat()
        } else {
            -travelWithinCycle.toFloat()
        }
        return if (offset == 0f) 0f else offset
    }

    private fun positiveRemainder(
        value: Double,
        divisor: Double,
    ): Double {
        val remainder = value % divisor
        return if (remainder < 0.0) remainder + divisor else remainder
    }
}
