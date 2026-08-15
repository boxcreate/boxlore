package cx.aswin.boxlore.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Leaves a cold-start Subscriptions or Downloads landing for Home, clearing the
 * launch root so a second Back exits normally.
 */
fun NavHostController.navigateHomeFromLaunchSubscriptions() {
    navigate("home") {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
