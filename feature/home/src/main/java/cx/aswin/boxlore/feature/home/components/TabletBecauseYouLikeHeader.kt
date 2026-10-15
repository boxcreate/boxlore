package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.R

@Composable
internal fun TabletBecauseYouLikeHeader(
    podcast: Podcast,
    title: String,
    icon: ImageVector,
    onPodcastClick: (Podcast) -> Unit,
    onChangePodcastClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeChildSectionHeader(title, icon, Modifier.weight(1f))
                TabletRecommendationSeed(podcast, onPodcastClick, onChangePodcastClick, Modifier.width(320.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeChildSectionHeader(title, icon)
                TabletRecommendationSeed(podcast, onPodcastClick, onChangePodcastClick, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TabletRecommendationSeed(podcast: Podcast, onPodcastClick: (Podcast) -> Unit, onChangePodcastClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.testTag("tablet_recommendation_seed"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { onPodcastClick(podcast) }, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OptimizedImage(url = podcast.imageUrl, proxyWidth = 96, fallbackUrls = podcast.artworkUrls(), contentDescription = null, modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_because_you_like), style = MaterialTheme.typography.labelMedium)
                        Text(podcast.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            FilledTonalIconButton(onClick = onChangePodcastClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.home_change_recommendation_show))
            }
        }
    }
}
