package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.catalog.logic.GroupedShowSearchResult
import cx.aswin.boxlore.core.model.Podcast

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
