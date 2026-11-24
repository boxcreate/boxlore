package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowWidth

/** Column counts and connected-list corners for Explore's list rows (Find a show results, tablet Top charts). */
internal object ExploreListRowLogic {
    /**
     * Find a show rows by the shared width classes: one column on compact widths, two on medium, three
     * from expanded. Like poster packing, the count follows the width alone, so a sideways phone lists
     * its results several across instead of in one stretched column.
     */
    fun showResultColumns(window: BoxloreWindowLayout): Int = when (window.widthClass) {
        BoxloreWindowWidth.Compact -> 1
        BoxloreWindowWidth.Medium -> 2
        else -> 3
    }

    /** Tablet Top chart rows after the featured show: two columns on medium widths, three from expanded. */
    fun chartColumns(window: BoxloreWindowLayout): Int = if (window.widthClass <= BoxloreWindowWidth.Medium) 2 else 3

    /**
     * Whether row [index] of [count], laid out row by row in [columns], starts or ends its column:
     * those ends get the list's large outer corners, the rest the small inner ones.
     */
    fun connectedEnds(index: Int, count: Int, columns: Int): Pair<Boolean, Boolean> {
        val perRow = columns.coerceAtLeast(1)
        return (index < perRow) to (index + perRow >= count)
    }
}
