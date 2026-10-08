package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardSkeleton
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.R

internal data class EpisodeRecommendationState(
    val title: String,
    val icon: ImageVector,
    val episodes: List<Episode>,
    val loading: Boolean,
    val accentColor: Color,
    val fallbackImageUrl: String?,
    val emptyMessage: String? = null,
)

internal object EpisodeRecommendationSectionLogic {
    fun filterEpisodes(episodes: List<Episode>): List<Episode> = LazyListKeyPolicy.deduplicateById(episodes) { it.id }

    fun shouldRender(isLoading: Boolean, hasEpisodes: Boolean, emptyMessage: String?): Boolean = isLoading || hasEpisodes || emptyMessage != null
}

@Composable
internal fun EpisodeRecommendationSection(
    state: EpisodeRecommendationState,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    onScrollStarted: (() -> Unit)? = null,
) {
    val episodes = remember(state.episodes) { EpisodeRecommendationSectionLogic.filterEpisodes(state.episodes) }
    if (!EpisodeRecommendationSectionLogic.shouldRender(state.loading, episodes.isNotEmpty(), state.emptyMessage)) return
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onScrollStarted?.invoke()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .then(if (onHeaderClick != null) Modifier.expressiveClickable(onClick = onHeaderClick) else Modifier)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(state.icon, null, tint = state.accentColor, modifier = Modifier.size(22.dp))
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onHeaderClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
        LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading -> items(4) { RecommendationSkeleton() }
                episodes.isNotEmpty() -> items(episodes, key = { LazyListKeyPolicy.safeKey(it.id, prefix = "rec_ep") }) { episode ->
                    val showName = episode.podcastTitle?.takeIf(String::isNotBlank)
                    val accessibleTitle = showName?.let { stringResource(R.string.episode_info_episode_from_show, episode.title, it) } ?: episode.title
                    FeedMediaCard(
                        imageUrl = episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: state.fallbackImageUrl.orEmpty(),
                        title = episode.title,
                        subtitle = null,
                        onClick = { onEpisodeClick(episode) },
                        presentation = FeedMediaCardPresentation.ExpressivePoster,
                        modifier = Modifier.width(160.dp).semantics { contentDescription = accessibleTitle },
                        imageChrome = {
                            val duration = formatEpisodeDuration(episode.duration)
                            if (duration.isNotBlank()) {
                                Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                    shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(duration, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            }
                        },
                    )
                }
                state.emptyMessage != null -> item {
                    Text(
                        state.emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    FeedMediaCardSkeleton(Modifier.width(160.dp)) { modifier ->
        androidx.compose.foundation.layout.Box(modifier.background(base).m3Shimmer(base, highlight))
    }
}
