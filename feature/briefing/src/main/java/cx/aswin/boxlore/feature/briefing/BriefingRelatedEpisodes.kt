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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
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
        color = relatedEpisodesDividerColor(state.isActive)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RelatedEpisodesHeader(state.isActive)
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

private fun relatedEpisodeKey(episode: Episode): String =
    "${episode.podcastId.orEmpty()}:${episode.id}:${episode.audioUrl}:${episode.title}"

@Composable
private fun relatedEpisodesDividerColor(isActive: Boolean): Color =
    if (isActive) {
        Color.White.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

@Composable
private fun RelatedEpisodesHeader(isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Podcasts,
            contentDescription = null,
            tint = relatedEpisodesHeaderColor(isActive, alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "LISTEN DEEPER",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontSize = 9.sp
            ),
            fontWeight = FontWeight.Bold,
            color = relatedEpisodesHeaderColor(isActive, alpha = 0.7f)
        )
    }
}

@Composable
private fun relatedEpisodesHeaderColor(
    isActive: Boolean,
    alpha: Float,
): Color =
    if (isActive) {
        Color.White.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
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
