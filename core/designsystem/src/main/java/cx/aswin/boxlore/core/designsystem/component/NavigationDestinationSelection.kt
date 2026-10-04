package cx.aswin.boxlore.core.designsystem.component

/** Shared by classic tabs and floating actions; detail routes keep their own selection. */
internal fun isNavDestinationSelected(currentRoute: String, destinationRoute: String): Boolean = currentRoute == destinationRoute || currentRoute.startsWith("$destinationRoute?")

/** Lore keeps its fourth indicator position even when it is outside the pill. */
private val indicatorRoutes = listOf("home", "explore", "library", "learn")

internal fun navigationIndicatorSelectedIndex(currentRoute: String): Int =
    indicatorRoutes.indexOfFirst { isNavDestinationSelected(currentRoute, it) }

/** Unselected detail routes hide the indicator without sending it back to Home. */
internal fun navigationIndicatorTargetIndex(selectedIndex: Int, previousIndex: Int): Int =
    if (selectedIndex in indicatorRoutes.indices) selectedIndex else previousIndex.coerceIn(indicatorRoutes.indices)
