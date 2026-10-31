package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationStyleUsesExternalSystemNavigationInset
import cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationTheme
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import cx.aswin.boxlore.navigation.selectBottomNavTab

/**
 * The phone bottom bar, tinted with [artworkColors] on artwork pages. A tap first closes a detail
 * covering the primary pane, then goes to the tapped tab; Explore tapped on the Explore page opens search.
 */
@Composable
internal fun BoxScope.PhoneBottomNavigation(
    navController: NavHostController,
    tabletNavigation: TabletDetailNavigation,
    activeTab: String,
    style: NavigationStyle,
    artworkColors: ColorScheme?,
    initialContentReady: Boolean,
    compactProgress: State<Float>,
) {
    val systemNavigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ArtworkNavigationTheme(artworkColors) {
        BoxLoreNavigationBar(
            currentRoute = activeTab,
            onNavigate = { route ->
                val revealedPrimary = tabletNavigation.revealPrimary()
                navController.selectBottomNavTab(route, activeTab, revealedPrimary)
            },
            style = style,
            initialContentReady = initialContentReady,
            compactProgress = compactProgress,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .let { navigationModifier ->
                    if (navigationStyleUsesExternalSystemNavigationInset(style)) {
                        navigationModifier.padding(bottom = systemNavigationInset)
                    } else {
                        navigationModifier
                    }
                },
        )
    }
}
