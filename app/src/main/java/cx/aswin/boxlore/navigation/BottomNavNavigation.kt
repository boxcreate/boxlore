package cx.aswin.boxlore.navigation

import androidx.navigation.NavHostController
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

/** One-shot flag on the Explore root entry: tapping its tab again opens search. */
internal const val ExploreSearchRequestKey = "explore_search_request"

/**
 * Tapping Explore again opens search only while the Explore page itself is showing. From a page
 * opened from Explore, or when the tap only closed a tablet detail covering it, the tap returns to Explore.
 */
internal fun shouldRequestExploreSearch(route: String, activeTab: String, currentRoute: String?, revealedPrimary: Boolean): Boolean =
    route == "explore" && activeTab == "explore" && currentRoute == ExploreTabRoutePattern && !revealedPrimary

/** Bottom bar and tablet rail tap: a repeat Explore tap on the Explore page opens search, others navigate. */
fun NavHostController.selectBottomNavTab(route: String, activeTab: String, revealedPrimary: Boolean) {
    val current = currentBackStackEntry
    if (current != null && shouldRequestExploreSearch(route, activeTab, current.destination.route, revealedPrimary)) {
        AnalyticsHelper.trackNavTabClicked(route, previousTab = activeTab)
        current.savedStateHandle[ExploreSearchRequestKey] = true
    } else {
        navigateBottomNavTab(route, activeTab)
    }
}

/** Bottom-nav tab click handler shared by the Activity shell. */
fun NavHostController.navigateBottomNavTab(route: String, activeTab: String) {
    AnalyticsHelper.trackNavTabClicked(route, previousTab = activeTab)
    when {
        activeTab == route -> {
            when (route) {
                "home" -> popBackStack("home", inclusive = false)
                "learn" -> {
                    val popped = popBackStack("learn", inclusive = false)
                    if (!popped) {
                        navigate("learn") {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
                "explore" -> {
                    val popped = popBackStack(ExploreTabRoutePattern, inclusive = false)
                    if (!popped) {
                        navigate(ExploreBottomNavRoute) {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
                "library" -> {
                    val popped = popBackStack("library", inclusive = false)
                    if (!popped) {
                        navigate("library") {
                            popUpTo("home") { saveState = false }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
        route == "home" -> {
            navigate("home") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "learn" -> {
            navigate("learn") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "explore" -> {
            navigate(ExploreBottomNavRoute) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        route == "library" -> {
            navigate("library") {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        else -> {
            val tabPattern = bottomNavTabRoutePattern(route)
            val popped =
                tabPattern != null &&
                    popBackStack(tabPattern, inclusive = false)
            if (!popped) {
                navigate(
                    if (route == "explore") ExploreBottomNavRoute else route,
                ) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}
