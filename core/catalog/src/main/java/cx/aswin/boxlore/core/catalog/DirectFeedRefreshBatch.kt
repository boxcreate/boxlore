package cx.aswin.boxlore.core.catalog

/** Rotate past failed feeds too, so a large library cannot starve behind the first batch. */
internal class DirectFeedRefreshBatch(private val maximum: Int = 10) {
    private var cursor = 0

    suspend fun select(ids: List<String>, preferred: String?, due: suspend (String) -> Boolean): List<String> {
        val rest = ids.filter { it != preferred }
        val start = if (rest.isEmpty()) 0 else cursor % rest.size
        val rotated = rest.drop(start) + rest.take(start)
        val selected = mutableListOf<String>()
        if (preferred in ids && preferred != null && due(preferred)) selected += preferred
        var visited = 0
        for (id in rotated) {
            if (selected.size >= maximum) break
            visited++
            if (due(id)) selected += id
        }
        cursor = if (rest.isEmpty()) 0 else (start + visited) % rest.size
        return selected
    }
}
