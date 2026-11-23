package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.shownotes.EpisodeLinkTitles
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind

/** Links shown before the "Show all links" row. */
internal const val COLLAPSED_EPISODE_LINKS = 4

/**
 * The episode's links as list rows of [columns] links each. The episode's own pages (its website
 * and articles) come first, then videos and podcasts, then the show's profiles, support and contact
 * links; within each group branded links lead, otherwise publisher order holds. Collapsed, only the
 * first [COLLAPSED_EPISODE_LINKS] links are placed, so show-wide profiles never hide the episode's own pages.
 */
internal fun episodeLinkListRows(links: List<EpisodeLink>, columns: Int, expanded: Boolean): List<List<EpisodeLink>> {
    val ordered = links.sortedWith(compareBy({ episodeLinkGroup(it.kind) }, { if (episodeLinkBrandIcon(it.host) != null) 0 else 1 }))
    val shown = if (expanded) ordered else ordered.take(COLLAPSED_EPISODE_LINKS)
    return shown.chunked(columns.coerceAtLeast(1))
}

/** 0 for the episode's own pages, 1 for videos and podcasts, 2 for everything about the show. */
private fun episodeLinkGroup(kind: EpisodeLinkKind): Int = when (kind) {
    EpisodeLinkKind.WEBSITE, EpisodeLinkKind.ARTICLE -> 0
    EpisodeLinkKind.VIDEO, EpisodeLinkKind.PODCAST -> 1
    EpisodeLinkKind.SOCIAL, EpisodeLinkKind.SUPPORT, EpisodeLinkKind.COMMUNITY, EpisodeLinkKind.EMAIL -> 2
}

/** A row's name, without the "Visit" or "Read" the old pills started with: the link's title, profile, platform or site. */
internal fun episodeLinkRowTitle(link: EpisodeLink): String =
    EpisodeLinkTitles.destinationTitle(link.title, link.platform)
        ?: episodeLinkProfileHandle(link)?.let { if (it.startsWith("@")) it else "@$it" }
        ?: link.platform?.takeIf(String::isNotBlank)
        ?: link.host

/**
 * The corners of the link at [row] and [column] in a connected grid of [rows], whose row holds
 * [columns] links: only the grid's own outer corners are round, every inner corner is tight. Pass
 * one more row than the links fill when the expand row closes the grid.
 */
internal fun linkCellShape(row: Int, column: Int, rows: Int, columns: Int): RoundedCornerShape {
    fun corner(round: Boolean) = if (round) 24.dp else 5.dp
    val top = row == 0
    val bottom = row == rows - 1
    val start = column == 0
    val end = column == columns - 1
    return RoundedCornerShape(
        topStart = corner(top && start),
        topEnd = corner(top && end),
        bottomEnd = corner(bottom && end),
        bottomStart = corner(bottom && start),
    )
}
