package cx.aswin.boxlore.feature.home

/**
 * Home's last settled place in its grid and the column count it was measured with. Not state: it
 * never redraws anything. [exploreShow] is the Explore show the listener reached, kept while its row
 * is on top, so turning the phone back returns to the same show rather than the row's first one.
 */
internal class GridPlace(var columns: Int) {
    var index = 0
        private set
    var offset = 0
        private set
    var key: Any? = null
        private set
    var exploreShow = -1
        private set

    fun record(index: Int, offset: Int, key: Any?, columns: Int) {
        this.index = index
        this.offset = offset
        this.key = key
        val row = exploreRow(key)
        exploreShow = when {
            row == null -> -1
            exploreShow in row * columns until (row + 1) * columns -> exploreShow
            else -> row * columns
        }
    }

    /** Where the listener goes once the grid has [newColumns] columns; null when they were at the top. */
    fun targetFor(newColumns: Int): GridTarget? = when {
        index == 0 && offset == 0 -> null
        exploreShow >= 0 && newColumns > 0 -> GridTarget(index, offset = 0, key = exploreRowKey(exploreShow / newColumns))
        else -> GridTarget(index, offset, key)
    }
}

/** An item to bring back to the top: its [key], the [index] it had and how far it was scrolled ([offset]). */
internal data class GridTarget(val index: Int, val offset: Int, val key: Any?)

/**
 * The index of [target]'s item among the [visible] items (index to key), or for an Explore row, its
 * index counted from any Explore row on screen (they sit one after another); null when neither is there.
 */
internal fun gridTargetIndex(target: GridTarget, visible: List<Pair<Int, Any>>): Int? =
    visible.firstOrNull { it.second == target.key }?.first
        ?: exploreRow(target.key)?.let { wanted ->
            visible.firstNotNullOfOrNull { (index, key) -> exploreRow(key)?.let { row -> index + wanted - row } }
        }

private const val EXPLORE_ROW_PREFIX = "discover_grid_"

/** The key of Explore's grid row [row] on Home. */
internal fun exploreRowKey(row: Int): String = "$EXPLORE_ROW_PREFIX$row"

private fun exploreRow(key: Any?): Int? = (key as? String)?.takeIf { it.startsWith(EXPLORE_ROW_PREFIX) }?.removePrefix(EXPLORE_ROW_PREFIX)?.toIntOrNull()
