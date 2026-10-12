package cx.aswin.boxlore.feature.onboarding

/** Continuous cover travel: a fast entrance settles into the same clock's steady drift. */
internal object WelcomeReelMotion {
    const val IntroDurationMillis = 2400L

    private const val InitialHoldMillis = 600L
    private const val InitialSpeedDpPerSecond = 240.0
    private const val SteadySpeedDpPerSecond = 16.0
    private const val TextRevealEndMillis = 1300L

    /** Revealed copy never hides again if the system's animation setting changes mid-entry. */
    fun isRevealComplete(elapsedMillis: Long, animationsDisabled: Boolean, alreadyComplete: Boolean): Boolean =
        alreadyComplete || animationsDisabled || elapsedMillis >= TextRevealEndMillis

    fun distanceDp(elapsedMillis: Long): Double {
        val elapsedSeconds = elapsedMillis.coerceAtLeast(0L) / 1000.0
        val introSeconds = IntroDurationMillis / 1000.0
        val holdSeconds = InitialHoldMillis / 1000.0
        val decaySeconds = introSeconds - holdSeconds
        val holdDistance = elapsedSeconds.coerceAtMost(holdSeconds) * InitialSpeedDpPerSecond
        val decayProgress = ((elapsedSeconds - holdSeconds) / decaySeconds).coerceIn(0.0, 1.0)
        val progressSquared = decayProgress * decayProgress
        // After the hold, integrate v(t) = steady + (initial - steady) * (1 - t / decay)^2.
        val decayDistance =
            decaySeconds * (
                SteadySpeedDpPerSecond * decayProgress +
                    (InitialSpeedDpPerSecond - SteadySpeedDpPerSecond) *
                    (decayProgress - progressSquared + progressSquared * decayProgress / 3.0)
                )
        val steadyDistance =
            (elapsedSeconds - introSeconds).coerceAtLeast(0.0) * SteadySpeedDpPerSecond
        return holdDistance + decayDistance + steadyDistance
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
