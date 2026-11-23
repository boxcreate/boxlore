package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.shownotes.EpisodeLinkTitles
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

/** A row's name, without the "Visit" or "Read" the old pills started with: the link's title, profile, platform or site. */
internal fun episodeLinkRowTitle(link: EpisodeLink): String =
    EpisodeLinkTitles.destinationTitle(link.title, link.platform)
        ?: episodeLinkProfileHandle(link)?.let { if (it.startsWith("@")) it else "@$it" }
        ?: link.platform?.takeIf(String::isNotBlank)
        ?: link.host

/** A link row's corners in the connected group: round at the group's top and bottom, tight between rows. */
internal fun linkRowShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 24.dp else 5.dp,
    topEnd = if (first) 24.dp else 5.dp,
    bottomStart = if (last) 24.dp else 5.dp,
    bottomEnd = if (last) 24.dp else 5.dp,
)
