package cx.aswin.boxlore.feature.info.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.EpisodeInfoUiState
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.components.EpisodeRecommendationSection
import cx.aswin.boxlore.feature.info.components.EpisodeRecommendationState
import cx.aswin.boxlore.feature.info.components.MoreFromEpisodeSection

@Composable
internal fun EpisodeInfoMoreLikeThisCard(state: EpisodeInfoUiState.Success, onEpisodeClick: (Episode) -> Unit) {
    EpisodeRecommendationSection(
        state = EpisodeRecommendationState(
            title = stringResource(R.string.episode_info_more_like_this),
            icon = Icons.Rounded.AutoAwesome,
            episodes = state.similarEpisodes,
            loading = state.similarEpisodesLoading,
            accentColor = MaterialTheme.colorScheme.primary,
            fallbackImageUrl = null,
        ),
        onEpisodeClick = onEpisodeClick,
    )
}

@Composable
internal fun EpisodeInfoMoreFromPodcastCard(
    state: EpisodeInfoUiState.Success,
    onPodcastClick: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onPodcastLinkClicked: () -> Unit,
    onRelatedEpisodesScrolled: () -> Unit,
    isPageScrolling: Boolean,
    onRelatedEpisodeClicked: () -> Unit,
) {
    LaunchedEffect(isPageScrolling, state.episode.id) {
        if (isPageScrolling && state.relatedEpisodes.isNotEmpty()) onRelatedEpisodesScrolled()
    }
    MoreFromEpisodeSection(
        showName = state.podcastTitle,
        episodes = state.relatedEpisodes,
        loading = state.relatedEpisodesLoading,
        fallbackImageUrl = state.episode.podcastImageUrl,
        onEpisodeClick = {
            onRelatedEpisodeClicked()
            onEpisodeClick(it)
        },
        onShowClick = {
            onPodcastLinkClicked()
            onPodcastClick(state.podcastId)
        },
    )
}
