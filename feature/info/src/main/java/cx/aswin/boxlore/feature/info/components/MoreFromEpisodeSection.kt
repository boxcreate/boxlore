package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun MoreFromEpisodeSection(
    showName: String,
    episodes: List<Episode>,
    loading: Boolean,
    fallbackImageUrl: String?,
    onEpisodeClick: (Episode) -> Unit,
    onShowClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoreFromHeader(showName, onShowClick)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                loading -> repeat(5) { index -> MoreFromRowSkeleton(moreFromRowShape(index, 5)) }
                episodes.isNotEmpty() -> episodes.forEachIndexed { index, episode ->
                    MoreFromEpisodeRow(episode, showName, fallbackImageUrl, moreFromRowShape(index, episodes.size)) { onEpisodeClick(episode) }
                }
                else -> Text(
                    stringResource(R.string.episode_info_no_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        FilledTonalButton(
            onClick = onShowClick,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.episode_info_explore_podcast))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreFromHeader(showName: String, onShowClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.episode_info_more_from, showName),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = GoogleSansWeight.bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        val shape = MaterialShapes.Cookie4Sided.toShape()
        FilledTonalIconButton(
            onClick = onShowClick,
            modifier = Modifier.size(48.dp),
            shapes = IconButtonDefaults.shapes(shape = shape, pressedShape = shape),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.episode_info_open_show, showName), Modifier.size(24.dp))
        }
    }
}

private fun moreFromRowShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) 24.dp else 6.dp,
    topEnd = if (index == 0) 24.dp else 6.dp,
    bottomStart = if (index == count - 1) 24.dp else 6.dp,
    bottomEnd = if (index == count - 1) 24.dp else 6.dp,
)

@Composable
private fun MoreFromEpisodeRow(episode: Episode, showName: String, fallbackImageUrl: String?, shape: RoundedCornerShape, onClick: () -> Unit) {
    val date = remember(episode.publishedDate) {
        episode.publishedDate.takeIf { it > 0L }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1_000L)) }
    }
    val metadata = listOfNotNull(date, formatEpisodeDuration(episode.duration).takeIf(String::isNotBlank)).joinToString(" · ")
    val accessibleTitle = stringResource(R.string.episode_info_episode_from_show, episode.title, showName)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = accessibleTitle }
            .expressiveClickable(shape = shape, onClick = onClick),
    ) {
        Row(Modifier.heightIn(min = 88.dp).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptimizedImage(
                url = episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: fallbackImageUrl,
                proxyWidth = 240,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(episode.title, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MoreFromRowSkeleton(shape: RoundedCornerShape) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.fillMaxWidth().height(88.dp).clip(shape).background(base).m3Shimmer(base, highlight))
}
