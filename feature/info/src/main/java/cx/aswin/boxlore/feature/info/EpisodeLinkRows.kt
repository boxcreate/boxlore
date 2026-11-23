package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.EpisodeLink

/** Links shown before the "Show all links" row. */
internal const val COLLAPSED_EPISODE_LINKS = 4

/**
 * The episode's links as list rows of [columns] links each: branded links first, in publisher order
 * within each group. Collapsed, only the first [COLLAPSED_EPISODE_LINKS] links are placed.
 */
internal fun episodeLinkListRows(links: List<EpisodeLink>, columns: Int, expanded: Boolean): List<List<EpisodeLink>> {
    val (branded, other) = links.partition { episodeLinkBrandIcon(it.host) != null }
    val ordered = branded + other
    val shown = if (expanded) ordered else ordered.take(COLLAPSED_EPISODE_LINKS)
    return shown.chunked(columns.coerceAtLeast(1))
}
