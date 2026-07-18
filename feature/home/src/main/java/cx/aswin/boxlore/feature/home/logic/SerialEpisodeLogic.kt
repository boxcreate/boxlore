package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode

internal fun resolveNextSerialEpisode(
    allEpisodes: List<Episode>,
    ongoingId: String?,
    lastCompletedId: String?,
    completedEpIdsForResolve: Set<String>,
    inProgressEpIdsForResolve: Set<String>,
): Episode? {
    val nextEp =
        when {
            ongoingId != null -> {
                val ongoingIndex = allEpisodes.indexOfFirst { it.id == ongoingId }
                if (ongoingIndex != -1 && ongoingIndex < allEpisodes.lastIndex) {
                    allEpisodes[ongoingIndex + 1]
                } else {
                    null
                }
            }
            lastCompletedId != null -> {
                val completedIndex = allEpisodes.indexOfFirst { it.id == lastCompletedId }
                if (completedIndex != -1 && completedIndex < allEpisodes.lastIndex) {
                    allEpisodes[completedIndex + 1]
                } else {
                    null
                }
            }
            else -> {
                allEpisodes.firstOrNull()
            }
        }
    return nextEp ?: allEpisodes.firstOrNull { ep ->
        ep.id !in completedEpIdsForResolve && ep.id !in inProgressEpIdsForResolve
    }
}
