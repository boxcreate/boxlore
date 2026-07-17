package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.feature.player.formatTime

internal fun chapterAtPosition(chapters: List<Chapter>, positionMs: Long): Chapter? =
    chapters.lastOrNull { (it.startTime * 1000).toLong() <= positionMs }

internal fun seekPosition(fraction: Float, durationMs: Long): Long =
    (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong()

internal fun seekPreviewText(positionMs: Long, chapter: Chapter?): String {
    val time = formatTime(positionMs)
    return chapter?.let { "$time • ${it.title}" } ?: time
}

internal fun playbackFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
