package cx.aswin.boxlore.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.rememberNavController

internal const val TabletContextRoot = "tablet_context_root"

internal val LocalTabletDetailNavigation = staticCompositionLocalOf<TabletDetailNavigation?> { null }

/** Two retained route owners share application repositories, never destination ViewModels. */
@Stable
internal class TabletDetailNavigation(
    val primary: NavHostController,
    val detail: NavHostController,
    immersive: MutableState<Boolean>,
    hostEnabled: MutableState<Boolean>,
    playerDocked: MutableState<Boolean> = mutableStateOf(false),
) {
    var immersive by immersive
    var hostEnabled by hostEnabled
    var playerDocked by playerDocked
    var splitAllowed by mutableStateOf(false)
    var hasDetail by mutableStateOf(false)
    var detailActive by mutableStateOf(false)

    fun navigate(source: NavHostController, route: String, options: NavOptionsBuilder.() -> Unit = {}) {
        if (isArtworkDetailRoute(route) && (splitAllowed || source === detail)) {
            immersive = false
            detail.navigate(route, options)
        } else if (source === detail) {
            // Brief redirects leave the detail graph; its local popUpTo does not belong to primary.
            closeDetails()
            primary.navigate(route) { launchSingleTop = true }
        } else {
            if (isArtworkDetailRoute(route) && hasDetail) closeDetails()
            primary.navigate(route, options)
        }
    }

    fun handleDeepLink(intent: Intent): Boolean {
        immersive = false
        // A warm link already belongs to this Activity; context routing must not rebuild its task.
        val inPlace = Intent(intent).apply { flags = flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).inv() }
        if (splitAllowed && detail.handleDeepLink(inPlace)) return true
        if (hasDetail) closeDetails()
        return primary.handleDeepLink(intent)
    }

    fun closeDetails() {
        detail.popBackStack(TabletContextRoot, inclusive = false)
    }
}

@Composable
internal fun rememberTabletDetailNavigation(primary: NavHostController): TabletDetailNavigation {
    // Only the primary controller consumes the Activity's launch Intent automatically.
    lateinit var detail: NavHostController
    CompositionLocalProvider(LocalContext provides LocalContext.current.applicationContext) {
        detail = rememberNavController()
    }
    val immersive = rememberSaveable { mutableStateOf(false) }
    val enabled = rememberSaveable { mutableStateOf(false) }
    val playerDocked = rememberSaveable { mutableStateOf(false) }
    val state = remember(primary, detail) { TabletDetailNavigation(primary, detail, immersive, enabled, playerDocked) }
    DisposableEffect(state) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            state.hasDetail = destination.route != TabletContextRoot
        }
        detail.addOnDestinationChangedListener(listener)
        onDispose { detail.removeOnDestinationChangedListener(listener) }
    }
    return state
}
