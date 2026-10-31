package cx.aswin.boxlore.feature.info.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
    isSectionScrollEngaged: Boolean,
    onRelatedEpisodeClicked: () -> Unit,
) {
    LaunchedEffect(isSectionScrollEngaged, state.episode.id) {
        if (isSectionScrollEngaged && state.relatedEpisodes.isNotEmpty()) onRelatedEpisodesScrolled()
    }
    MoreFromEpisodeSection(
        showName = state.podcastTitle,
        episodes = state.relatedEpisodes,
        loading = state.relatedEpisodesLoading,
        fallbackImageUrl = moreFromShowPicture(state.episode, state.relatedEpisodes),
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

/** The show's cover for the "More from" row: the page episode's, else one from the show's other episodes. */
internal fun moreFromShowPicture(episode: Episode, related: List<Episode>): String? =
    episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: related.firstNotNullOfOrNull { it.podcastImageUrl?.takeIf(String::isNotBlank) }
