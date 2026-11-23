package cx.aswin.boxlore.feature.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode

@Composable
internal fun RelatedEpisodesSection(
    state: BriefingStoryCardState,
    onEpisodeClick: (Episode) -> Unit,
) {
    val recs = state.chapter.relatedEpisodes
    if (recs.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        return
    }
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RelatedEpisodesHeader()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recs, key = ::relatedEpisodeKey) { episode ->
                CompactEpisodeChip(
                    episode = episode,
                    isActiveCard = state.isActive,
                    accentColor = state.accentColor,
                    onClick = {
                        trackRelatedEpisodeClick(state, episode)
                        onEpisodeClick(episode)
                    }
                )
            }
        }
    }
}

private fun relatedEpisodeKey(episode: Episode): String = "${episode.podcastId.orEmpty()}:${episode.id}:${episode.audioUrl}:${episode.title}"

@Composable
private fun RelatedEpisodesHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(R.string.briefing_screen_listen_further),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun trackRelatedEpisodeClick(
    state: BriefingStoryCardState,
    episode: Episode,
) {
    AnalyticsHelper.trackDailyBriefingRelatedEpisodeClicked(
        region = state.briefing.region,
        date = state.briefing.date,
        chapterIndex = state.page,
        episodeId = episode.id,
        episodeTitle = episode.title,
        podcastId = episode.podcastId.orEmpty(),
        podcastTitle = episode.podcastTitle.orEmpty()
    )
}
