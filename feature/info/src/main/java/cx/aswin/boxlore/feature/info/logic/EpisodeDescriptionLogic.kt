package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Chapter

/** Generated links identify whole seconds; seeking retains the chapter's exact start. */
internal fun chapterLinkPositionMs(url: String, chapters: List<Chapter>): Long? {
    if (!url.startsWith("play-position:")) return null
    val seconds = url.removePrefix("play-position:").toLongOrNull() ?: return null
    return chapters.firstOrNull { it.startTime.isFinite() && it.startTime >= 0 && it.startTime.toLong() == seconds }
        ?.let { (it.startTime * 1_000).toLong() }
}

/** A passing sliver of the vertical section is not recommendation browsing. */
internal fun relatedSectionScrollEngaged(scrolling: Boolean, offset: Int?, size: Int, viewportStart: Int, viewportEnd: Int): Boolean {
    if (!scrolling || offset == null) return false
    if (size <= 0 || viewportEnd <= viewportStart) return false
    val visible = (minOf(offset + size, viewportEnd) - maxOf(offset, viewportStart)).coerceAtLeast(0)
    return visible.toFloat() / minOf(size, viewportEnd - viewportStart) >= .6f
}
