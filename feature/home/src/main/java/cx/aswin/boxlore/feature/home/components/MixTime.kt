package cx.aswin.boxlore.feature.home.components

/** Total remaining whole minutes, including hours; a partial last minute still displays one. */
internal fun remainingMixMinutes(durationSeconds: Int, progress: Float): Int {
    val remainingSeconds = ((1f - progress) * durationSeconds).toInt()
    return (remainingSeconds / 60).coerceAtLeast(1)
}
