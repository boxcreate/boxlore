package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.EpisodeLink

/** Branded links first, in publisher order within each group; fill top/bottom before moving across. */
internal fun episodeLinkRows(links: List<EpisodeLink>): List<List<EpisodeLink>> {
    val (branded, other) = links.partition { episodeLinkBrandIcon(it.host) != null }
    val ordered = branded + other
    return (0 until minOf(2, ordered.size)).map { row ->
        ordered.filterIndexed { index, _ -> index % 2 == row }
    }
}
