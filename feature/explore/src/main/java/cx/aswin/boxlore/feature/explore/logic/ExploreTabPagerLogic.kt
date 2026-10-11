package cx.aswin.boxlore.feature.explore.logic

/** Visual order is For You, Top; the existing product IDs remain 1, 0. */
internal object ExploreTabPagerLogic {
    const val PAGE_COUNT = 2

    fun pageForTab(tab: Int): Int = if (tab == 0) 1 else 0
    fun tabForPage(page: Int): Int = if (page == 1) 0 else 1

    fun selectionAfterSettle(page: Int, selectedTab: Int, enabled: Boolean): Int? =
        tabForPage(page).takeIf { enabled && it != selectedTab }
}
