package cx.aswin.boxlore.feature.home.logic

/** Keep one clock running only while a loading section intersects the feed viewport. */
internal fun shouldAnimateHomeShimmer(
    visibleKeys: Set<String>,
    initialLoading: Boolean,
    discoveryLoading: Boolean,
    editorialLoading: Boolean,
    recommendationsLoading: Boolean,
    selectedShowLoading: Boolean,
): Boolean {
    if (visibleKeys.isEmpty()) {
        return booleanArrayOf(initialLoading, discoveryLoading, editorialLoading, recommendationsLoading, selectedShowLoading).any { it }
    }
    return visibleKeys.any { key ->
        when {
            key == "hero" -> initialLoading
            key == "your_shows" -> initialLoading || selectedShowLoading
            key.startsWith("discover_grid_") -> initialLoading || discoveryLoading
            key.startsWith("editorial_") -> editorialLoading
            key == "for_you_hero" || key.startsWith("for_you_body_") -> recommendationsLoading
            else -> false
        }
    }
}
