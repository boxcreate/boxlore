package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.feature.home.HomeUiState

/** Base Home updates must not overwrite the independent selected-category request. */
internal fun HomeUiState.withCurrentCategoryDiscovery(previous: HomeUiState): HomeUiState {
    if (selectedCategory == null) return this
    return copy(
        discoverPodcasts = if (selectedCategory == previous.selectedCategory) previous.discoverPodcasts else emptyList(),
        isFilterLoading = selectedCategory != previous.selectedCategory || previous.isFilterLoading,
    )
}

/**
 * The selected show's list has one writer, the selected-show collector, which hides completed
 * episodes. Base Home updates run on every playback tick and must not write the raw list back,
 * or the filtered list flips between the two and rows jump.
 */
internal fun HomeUiState.withSelectedShowFrom(previous: HomeUiState): HomeUiState = copy(
    selectedPodcastId = previous.selectedPodcastId,
    selectedPodcastEpisodes = previous.selectedPodcastEpisodes,
    isSelectedPodcastLoading = previous.isSelectedPodcastLoading,
    isSelectedRssRefreshing = previous.isSelectedRssRefreshing,
)

/** A refresh indicator must not replace already usable discovery cards with skeletons. */
internal fun showHomeDiscoveryContent(initialLoading: Boolean, hasContent: Boolean): Boolean = !initialLoading && hasContent
