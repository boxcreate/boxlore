package cx.aswin.boxlore.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Leaves cold-start Subscriptions for Home, clearing the launch root so a second Back exits normally.
 */
fun NavHostController.navigateHomeFromLaunchSubscriptions() {
    navigate("home") {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
