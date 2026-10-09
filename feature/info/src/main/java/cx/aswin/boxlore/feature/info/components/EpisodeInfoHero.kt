package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

@Composable
internal fun EpisodeInfoHero(
    episode: Episode,
    podcastTitle: String,
    onPodcastClick: () -> Unit,
    completionState: EpisodeCompletionState,
    onToggleCompletion: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkPlayedTipDismissed: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Surface(
            modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(Modifier.fillMaxSize()) {
                OptimizedImage(
                    url = episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl,
                    proxyWidth = 640,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                EpisodeArtworkTags(episode, Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).expressiveClickable(onClick = onPodcastClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                podcastTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.episode_info_open_show, podcastTitle), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        EpisodeExpandableTitle(episode.id, episode.title)
        Spacer(Modifier.height(8.dp))
        EpisodeMetadata(episode, completionState.isCompleted, onToggleCompletion)
        if (completionState.showTip) {
            Text(stringResource(R.string.episode_info_mark_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LaunchedEffect(episode.id) {
                delay(4_000)
                onMarkPlayedTipDismissed()
            }
        }
    }
}

@Composable
private fun EpisodeMetadata(episode: Episode, isCompleted: Boolean, onToggleCompletion: () -> Unit) {
    val date = remember(episode.publishedDate) {
        episode.publishedDate.takeIf { it > 0L }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1_000L)) }
    }
    val number = episodeNumberLabel(episode)
    val metadata = listOfNotNull(date, formatEpisodeDuration(episode.duration).takeIf(String::isNotBlank), number).joinToString(" · ")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (metadata.isNotEmpty()) {
            Text(
                metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f).clipToBounds().basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2_500,
                    repeatDelayMillis = 2_000,
                    spacing = MarqueeSpacing(32.dp),
                    velocity = 24.dp,
                ),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        EpisodeCompletionPill(isCompleted, onToggleCompletion)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeArtworkTags(episode: Episode, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val type = when (episode.episodeType?.lowercase()) {
            "bonus" -> R.string.episode_info_bonus
            "trailer" -> R.string.episode_info_trailer
            else -> null
        }
        type?.let { QuietMetadataChip(stringResource(it)) }
        if (episode.enclosureType?.startsWith("video/") == true) QuietMetadataChip(stringResource(R.string.episode_info_video))
    }
}

@Composable
private fun episodeNumberLabel(episode: Episode): String? {
    val season = episode.seasonNumber ?: 0
    val number = episode.episodeNumber ?: 0
    return when {
        season > 0 && number > 0 -> stringResource(R.string.episode_info_season_episode, season, number)
        number > 0 -> stringResource(R.string.episode_info_number, number)
        season > 0 -> stringResource(R.string.episode_info_season, season)
        else -> null
    }
}

@Composable
private fun QuietMetadataChip(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 1.dp) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
