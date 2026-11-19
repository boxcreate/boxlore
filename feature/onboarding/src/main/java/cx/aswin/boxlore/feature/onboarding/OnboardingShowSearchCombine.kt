package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.catalog.logic.GroupedShowSearchResult
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException

/**
 * Merge Meili/hybrid grouped search with eager local substring matches for onboarding.
 * Catalog (Meili + local) first; also-found stays separate and de-duped.
 */
internal fun combineOnboardingShowSearch(
    grouped: GroupedShowSearchResult,
    localMatches: List<Podcast>,
): Pair<List<Podcast>, List<Podcast>> {
    val seenIds = mutableSetOf<String>()
    val catalog = mutableListOf<Podcast>()
    grouped.catalog.forEach {
        if (seenIds.add(it.id)) catalog.add(it)
    }
    localMatches.forEach {
        if (seenIds.add(it.id)) catalog.add(it)
    }
    val alsoFound = grouped.alsoFound.filter { seenIds.add(it.id) }
    return catalog to alsoFound
}

/**
 * The search screen after [grouped] answered: its results with [localMatches], marked
 * [OnboardingUiState.searchFailed] when neither search could be read, so the screen offers Try
 * again instead of saying nothing matched.
 */
internal fun OnboardingUiState.withShowSearch(
    grouped: GroupedShowSearchResult,
    localMatches: List<Podcast>,
): OnboardingUiState {
    val (catalog, alsoFound) = combineOnboardingShowSearch(grouped, localMatches)
    return copy(searchResults = catalog, alsoFoundResults = alsoFound, isSearching = false, searchFailed = grouped.failed)
}

/**
 * The search screen after the search threw. A cancelled search (a newer query took over) changes
 * nothing, so it can't end the newer search's loader and show "No Podcasts Found" while that runs.
 */
internal fun OnboardingUiState.withShowSearchError(error: Exception): OnboardingUiState =
    if (error is CancellationException) this else copy(isSearching = false, searchFailed = true)

/** Try again after a failed show search: searches the same text again. */
internal fun OnboardingViewModel.retrySearch() = updateSearchQuery(_uiState.value.searchQuery)
