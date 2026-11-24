package cx.aswin.boxlore.feature.history.insights

/**
 * How Insights lays its cards out: one a row, or on large tablets ([columns] 2) side by side in
 * pairs, in the same order. Cards in [fullWidth] (the month and the records) always take a row of
 * their own. Each row is listed by its cards' keys; a row's list key is its first card's.
 */
internal object InsightsLayout {
    /** Insights uses two columns from this content width. */
    const val TWO_COLUMN_WIDTH_DP = 1_000f

    val FULL_WIDTH = setOf("mosaic", "records")

    fun columns(contentWidthDp: Float): Int = if (contentWidthDp >= TWO_COLUMN_WIDTH_DP) 2 else 1

    fun rows(keys: List<String>, columns: Int, fullWidth: Set<String> = FULL_WIDTH): List<List<String>> {
        if (columns <= 1) return keys.map { listOf(it) }
        val rows = mutableListOf<List<String>>()
        var open = mutableListOf<String>()
        keys.forEach { key ->
            if (key in fullWidth) {
                if (open.isNotEmpty()) rows += open.toList()
                open = mutableListOf()
                rows += listOf(key)
            } else {
                open += key
                if (open.size == columns) {
                    rows += open.toList()
                    open = mutableListOf()
                }
            }
        }
        if (open.isNotEmpty()) rows += open.toList()
        return rows
    }

    /** The row holding [key], or -1. */
    fun rowOf(rows: List<List<String>>, key: String): Int = rows.indexOfFirst { key in it }
}
