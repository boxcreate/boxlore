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

/** A refresh indicator must not replace already usable discovery cards with skeletons. */
internal fun showHomeDiscoveryContent(initialLoading: Boolean, hasContent: Boolean): Boolean = !initialLoading && hasContent
