package cx.aswin.boxlore.feature.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
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

@Composable
internal fun EpisodeLinksSection(links: List<EpisodeLink>, showName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rows = remember(links) { episodeLinkRows(links) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.episode_info_links), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        // Rows scroll together, but pack independently so shorter bubbles leave no column gaps.
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { link ->
                        EpisodeLinkPill(link, showName, Modifier.widthIn(max = 300.dp)) {
                            openEpisodeLink(context, link.url)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeLinkPill(link: EpisodeLink, showName: String, modifier: Modifier, onClick: () -> Unit) {
    val label = episodeLinkLabel(link, showName)
    val profile = episodeLinkProfileHandle(link)
    val accessibleLabel = if (profile != null) {
        stringResource(R.string.episode_info_link_view_on, profile, link.platform ?: link.host)
    } else {
        "$label, ${link.host}"
    }
    val brand = episodeLinkBrandIcon(link.host)
    val colors = MaterialTheme.colorScheme
    val palette = brand?.let { episodeLinkPalette(it, colors.surfaceContainer, colors.onSurface) }
    Surface(
        shape = CircleShape,
        color = palette?.container ?: colors.surfaceContainer,
        modifier = modifier.semantics { contentDescription = accessibleLabel }
            .expressiveClickable(shape = CircleShape, onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EpisodeLinkIcon(link, palette?.icon ?: colors.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

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
