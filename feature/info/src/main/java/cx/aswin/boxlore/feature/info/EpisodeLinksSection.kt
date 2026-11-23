package cx.aswin.boxlore.feature.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.shownotes.EpisodeLinkTitles
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.feature.info.components.EpisodeExpandRow
import cx.aswin.boxlore.feature.info.components.EpisodeSectionHeader

/**
 * The episode's links as a list, like its chapters: each row has the site's icon, the link's name,
 * the site under it and an open-in-new arrow. The first four show, then a row to show the rest. On
 * pages 600dp and wider the rows sit in two columns.
 */
@Composable
internal fun EpisodeLinksSection(links: List<EpisodeLink>, showName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by rememberSaveable(links.map(EpisodeLink::url)) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EpisodeSectionHeader(stringResource(R.string.episode_info_links), Modifier.padding(horizontal = 20.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val columns = if (maxWidth >= TWO_COLUMN_LINKS_WIDTH) 2 else 1
            val rows = remember(links, columns, expanded) { episodeLinkListRows(links, columns, expanded) }
            val expandable = links.size > COLLAPSED_EPISODE_LINKS
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { link ->
                            val shape = linkRowShape(first = rowIndex == 0, last = rowIndex == rows.lastIndex && !expandable)
                            EpisodeLinkRow(link, showName, shape, Modifier.weight(1f)) { openEpisodeLink(context, link.url) }
                        }
                        // An odd last link keeps its column's width.
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (expandable) {
                    val shape = linkRowShape(first = false, last = true)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
                        EpisodeExpandRow(
                            label = stringResource(if (expanded) R.string.episode_info_fewer_links else R.string.episode_info_all_links),
                            expanded = expanded,
                            onClick = { expanded = !expanded },
                            shape = shape,
                        )
                    }
                }
            }
        }
    }
}

private val TWO_COLUMN_LINKS_WIDTH = 568.dp

private fun linkRowShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 24.dp else 5.dp,
    topEnd = if (first) 24.dp else 5.dp,
    bottomStart = if (last) 24.dp else 5.dp,
    bottomEnd = if (last) 24.dp else 5.dp,
)

/** One link: its brand or kind icon in a circle, its name, the site, and the open-in-new arrow. */
@Composable
private fun EpisodeLinkRow(link: EpisodeLink, showName: String, shape: RoundedCornerShape, modifier: Modifier, onClick: () -> Unit) {
    val label = episodeLinkLabel(link, showName)
    val profile = episodeLinkProfileHandle(link)
    val accessibleLabel = if (profile != null) {
        stringResource(R.string.episode_info_link_view_on, profile, link.platform ?: link.host)
    } else {
        stringResource(R.string.episode_info_link_description, label, link.host)
    }
    val brand = episodeLinkBrandIcon(link.host)
    val colors = MaterialTheme.colorScheme
    val palette = brand?.let { episodeLinkPalette(it, colors.surfaceContainerHigh, colors.onSurface) }
    Surface(
        shape = shape,
        color = colors.surfaceContainerLow,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = accessibleLabel }
            .expressiveClickable(shape = shape, onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = palette?.container ?: colors.surfaceContainerHigh) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { EpisodeLinkIcon(link, palette?.icon ?: colors.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f)) {
                Text(episodeLinkRowTitle(link), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(link.host, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** A row's name, without the "Visit" or "Read" the old pills started with: the link's title, profile, platform or site. */
internal fun episodeLinkRowTitle(link: EpisodeLink): String =
    EpisodeLinkTitles.destinationTitle(link.title, link.platform)
        ?: episodeLinkProfileHandle(link)?.let { if (it.startsWith("@")) it else "@$it" }
        ?: link.platform?.takeIf(String::isNotBlank)
        ?: link.host

@Composable
private fun EpisodeLinkIcon(link: EpisodeLink, tint: Color) {
    val brand = episodeLinkBrandIcon(link.host)
    if (brand != null) {
        Icon(painterResource(brand), null, tint = tint, modifier = Modifier.size(20.dp))
    } else {
        val icon = when (link.kind) {
            EpisodeLinkKind.WEBSITE -> Icons.Rounded.Language
            EpisodeLinkKind.ARTICLE -> Icons.AutoMirrored.Rounded.Article
            EpisodeLinkKind.VIDEO -> Icons.Rounded.PlayCircleOutline
            EpisodeLinkKind.SOCIAL -> Icons.Rounded.AlternateEmail
            EpisodeLinkKind.SUPPORT -> Icons.Rounded.VolunteerActivism
            EpisodeLinkKind.PODCAST -> Icons.Rounded.Podcasts
            EpisodeLinkKind.COMMUNITY -> Icons.Rounded.Forum
            EpisodeLinkKind.EMAIL -> Icons.Rounded.MailOutline
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun episodeLinkLabel(link: EpisodeLink, showName: String): String {
    episodeLinkActionTitle(link)?.let { return it }
    val spec = episodeLinkLabelSpec(link, showName)
    return stringResource(spec.resource, *spec.arguments.toTypedArray())
}

internal data class EpisodeLinkLabelSpec(val resource: Int, val arguments: List<String>)

internal fun episodeLinkProfileHandle(link: EpisodeLink): String? {
    if (link.kind != EpisodeLinkKind.SOCIAL || episodeLinkBrandIcon(link.host) == null) return null
    return link.handle?.trim()?.takeIf(String::isNotBlank)
}

internal fun episodeLinkActionTitle(link: EpisodeLink): String? {
    if (episodeLinkProfileHandle(link) != null) return null
    return EpisodeLinkTitles.destinationTitle(link.title, link.platform)
        ?.takeIf { Regex("(?i)^(read|watch|visit|listen|support|view|open|email)\\b").containsMatchIn(it) }
}

internal fun episodeLinkLabelSpec(link: EpisodeLink, showName: String): EpisodeLinkLabelSpec {
    val destination = link.copy(title = EpisodeLinkTitles.destinationTitle(link.title, link.platform))
    return destinationLabelSpec(destination, showName)
}

private fun destinationLabelSpec(link: EpisodeLink, showName: String): EpisodeLinkLabelSpec = when (link.kind) {
    EpisodeLinkKind.WEBSITE -> labelSpec(R.string.episode_info_link_visit, link.title ?: link.host)
    EpisodeLinkKind.ARTICLE -> labelSpec(R.string.episode_info_link_read, link.title ?: link.host)
    EpisodeLinkKind.VIDEO -> videoLabel(link)
    EpisodeLinkKind.SOCIAL -> socialLabel(link)
    EpisodeLinkKind.SUPPORT -> communityLabel(link, showName, R.string.episode_info_link_support)
    EpisodeLinkKind.PODCAST -> podcastLabel(link)
    EpisodeLinkKind.COMMUNITY -> communityLabel(link, showName, R.string.episode_info_link_join)
    EpisodeLinkKind.EMAIL -> labelSpec(R.string.episode_info_link_email, link.host)
}

private fun communityLabel(link: EpisodeLink, showName: String, resource: Int) = labelSpec(resource, link.title ?: link.handle ?: showName, link.platform ?: link.host)

private fun labelSpec(resource: Int, vararg arguments: String) = EpisodeLinkLabelSpec(resource, arguments.toList())
private fun videoLabel(link: EpisodeLink): EpisodeLinkLabelSpec = link.title?.let { labelSpec(R.string.episode_info_link_watch, it) }
    ?: labelSpec(R.string.episode_info_link_watch_on, link.platform ?: link.host)
private fun podcastLabel(link: EpisodeLink): EpisodeLinkLabelSpec = link.title?.let { labelSpec(R.string.episode_info_link_listen, it) }
    ?: labelSpec(R.string.episode_info_link_listen_on, link.platform ?: link.host)
private fun socialLabel(link: EpisodeLink): EpisodeLinkLabelSpec {
    episodeLinkProfileHandle(link)?.let { return labelSpec(R.string.episode_info_link_profile, it) }
    val destination = link.title ?: link.handle?.takeIf(String::isNotBlank)
    return if (destination == null) {
        labelSpec(R.string.episode_info_link_open_on, link.platform ?: link.host)
    } else {
        labelSpec(R.string.episode_info_link_view_on, destination, link.platform ?: link.host)
    }
}

internal fun openEpisodeLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
    if (scheme !in setOf("https", "http", "mailto")) return
    if (scheme != "mailto" && uri.host.isNullOrBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.episode_info_link_unavailable, Toast.LENGTH_SHORT).show()
    }
}
