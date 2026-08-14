package cx.aswin.boxlore.core.prefs

/** Up to [MAX] subscribed shows that lead Your Shows on Home. */
object HomePinnedShows {
    const val MAX = 5

    enum class ToggleResult {
        Pinned,
        Unpinned,
        AtCapacity,
    }

    fun sanitize(ids: List<String>): List<String> {
        return ids
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX)
    }

    fun toggle(
        current: List<String>,
        podcastId: String,
    ): Pair<List<String>, ToggleResult> {
        val id = podcastId.trim()
        if (id.isEmpty()) return sanitize(current) to ToggleResult.Unpinned
        val sanitized = sanitize(current)
        if (id in sanitized) {
            return sanitized.filter { it != id } to ToggleResult.Unpinned
        }
        if (sanitized.size >= MAX) {
            return sanitized to ToggleResult.AtCapacity
        }
        return sanitize(sanitized + id) to ToggleResult.Pinned
    }
}
