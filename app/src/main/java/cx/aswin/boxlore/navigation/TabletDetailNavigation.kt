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
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions

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

    /** True when undocking would show the player in a context pane beside browsing. */
    var playerPaneAvailable by mutableStateOf(false)

    /** True when the visible detail occupies the primary pane's bounds instead of sitting beside it. */
    var detailCoversPrimary by mutableStateOf(false)

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
        if (splitAllowed && openDetailLink(intent)) return true
        if (hasDetail) closeDetails()
        return primary.handleDeepLink(intent)
    }

    /**
     * NavController.handleDeepLink replaces the whole graph, which would drop the context root and leave
     * Back and closeDetails() nothing to return to. The linked screen replaces any open details instead.
     */
    private fun openDetailLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        val request = NavDeepLinkRequest.Builder.fromUri(uri).apply {
            intent.action?.let(::setAction)
            intent.type?.let(::setMimeType)
        }.build()
        // Until the detail host has set its graph, links open in the primary pane.
        if (detail.currentBackStackEntry == null || !detail.graph.hasDeepLink(request)) return false
        detail.navigate(request, navOptions { popUpTo(TabletContextRoot) })
        return true
    }

    fun closeDetails() {
        detail.popBackStack(TabletContextRoot, inclusive = false)
    }

    /**
     * Rail and bottom-bar taps navigate the primary pane, so a detail covering it closes first.
     * Returns true when a covering detail closed, so that tap only reveals the primary pane.
     */
    fun revealPrimary(): Boolean {
        if (!detailCoversPrimary) return false
        closeDetails()
        return true
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
