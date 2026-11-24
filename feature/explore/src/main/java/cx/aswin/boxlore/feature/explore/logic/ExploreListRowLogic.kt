package cx.aswin.boxlore.feature.explore.logic

/** Column counts and connected-list corners for Explore's list rows (Find a show results, tablet Top charts). */
internal object ExploreListRowLogic {
    private const val MEDIUM_WIDTH_DP = 600f
    private const val EXPANDED_WIDTH_DP = 840f

    /** Find a show rows: one column on compact windows, two on medium, three on expanded. */
    fun showResultColumns(windowWidthDp: Float): Int = when {
        windowWidthDp < MEDIUM_WIDTH_DP -> 1
        windowWidthDp < EXPANDED_WIDTH_DP -> 2
        else -> 3
    }

    /** Tablet Top chart rows after the featured show: two columns below 840dp, three from there. */
    fun chartColumns(windowWidthDp: Float): Int = if (windowWidthDp < EXPANDED_WIDTH_DP) 2 else 3

    /**
     * Whether row [index] of [count], laid out row by row in [columns], starts or ends its column:
     * those ends get the list's large outer corners, the rest the small inner ones.
     */
    fun connectedEnds(index: Int, count: Int, columns: Int): Pair<Boolean, Boolean> {
        val perRow = columns.coerceAtLeast(1)
        return (index < perRow) to (index + perRow >= count)
    }
}
