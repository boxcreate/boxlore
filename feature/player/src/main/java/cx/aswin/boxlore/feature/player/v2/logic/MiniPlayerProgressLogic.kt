package cx.aswin.boxlore.feature.player.v2.logic

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Taper both ends onto the centerline so motion never shifts the progress endpoint. */
internal fun miniPlayerWaveOffset(
    distance: Float,
    playedLength: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
): Float {
    val validPosition = distance.isFinite() && playedLength.isFinite()
    val validWave = amplitude.isFinite() && wavelength.isFinite() && phase.isFinite()
    if (!validPosition || !validWave) {
        return 0f
    }
    if (playedLength <= 0f || wavelength <= 0f || amplitude <= 0f) return 0f
    val clampedDistance = distance.coerceIn(0f, playedLength)
    val taperLength = min(wavelength * 0.2f, playedLength * 0.5f)
    if (taperLength <= 0f) return 0f
    val taper = (min(clampedDistance, playedLength - clampedDistance) / taperLength).coerceIn(0f, 1f)
    return amplitude * taper * sin(clampedDistance.toDouble() / wavelength * 2 * PI - phase).toFloat()
}
