package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.feature.player.formatTime

internal fun chapterAtPosition(chapters: List<Chapter>, positionMs: Long): Chapter? = chapters.lastOrNull { (it.startTime * 1000).toLong() <= positionMs }

internal fun seekPosition(fraction: Float, durationMs: Long): Long = (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong()

internal fun seekPreviewText(positionMs: Long, chapter: Chapter?): String {
    val time = formatTime(positionMs)
    return chapter?.let { "$time • ${it.title}" } ?: time
}

internal fun playbackFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

internal data class SeekbarThumbBounds(val left: Float, val width: Float)

/** The track can temporarily be narrower than its thumb while the player opens. */
internal fun seekbarThumbBounds(trackWidth: Float, thumbWidth: Float, centerX: Float): SeekbarThumbBounds {
    val available = trackWidth.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val fittedWidth = thumbWidth.takeIf { it.isFinite() }?.coerceIn(0f, available) ?: 0f
    val center = centerX.takeIf { it.isFinite() } ?: 0f
    return SeekbarThumbBounds((center - fittedWidth / 2f).coerceIn(0f, available - fittedWidth), fittedWidth)
}
