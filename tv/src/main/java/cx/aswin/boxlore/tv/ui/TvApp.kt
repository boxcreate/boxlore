package cx.aswin.boxlore.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.LocalContentColor
import cx.aswin.boxlore.tv.TvContainer
import cx.aswin.boxlore.tv.ui.home.HomeScreen
import cx.aswin.boxlore.tv.ui.library.LibraryScreen
import cx.aswin.boxlore.tv.ui.library.LibraryTab
import cx.aswin.boxlore.tv.ui.player.PlayerScreen
import cx.aswin.boxlore.tv.ui.search.SearchScreen
import cx.aswin.boxlore.tv.ui.settings.SettingsScreen
import cx.aswin.boxlore.tv.ui.show.EpisodeScreen
import cx.aswin.boxlore.tv.ui.show.ShowScreen
import cx.aswin.boxlore.tv.ui.theme.TvColors

/** The routes the TV opens. The four tabs sit in the rail; the rest open on top of them. */
object TvRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val LIBRARY_PAGE = "library?tab={tab}"
    const val ARG_TAB = "tab"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val SHOW = "show/{showId}"
    const val EPISODE = "episode/{episodeId}"
    const val ARG_SHOW = "showId"
    const val ARG_EPISODE = "episodeId"

    fun show(id: String) = "show/$id"

    fun episode(id: String) = "episode/$id"

    fun library(tab: String) = "library?tab=$tab"
}

/** What a screen can ask the app to open. */
class TvNavigator(private val nav: NavHostController) {
    /** Tabs replace each other above Home, so Back from any tab returns to Home and Back from Home leaves the app. */
    fun tab(route: String) {
        nav.navigate(route) {
            popUpTo(TvRoutes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Library on one of its tabs, in place of the current tab as the rail would open it. */
    fun library(tab: LibraryTab) {
        nav.navigate(TvRoutes.library(tab.name)) {
            popUpTo(TvRoutes.HOME) { saveState = true }
            launchSingleTop = true
        }
    }

    fun show(id: String) = nav.navigate(TvRoutes.show(id))

    fun episode(id: String) = nav.navigate(TvRoutes.episode(id))

    fun player() = nav.navigate(TvRoutes.PLAYER) { launchSingleTop = true }

    fun back() {
        nav.popBackStack()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvApp(container: TvContainer) {
    val nav = rememberNavController()
    val go = TvNavigator(nav)
    // Text without its own colour reads in on-surface; lists scroll only as far as needed to show the item.
    CompositionLocalProvider(LocalContentColor provides TvColors.onSurface, LocalBringIntoViewSpec provides KeepInView) {
    // The window's own background is the app's background colour; painting it again here would
    // fill the whole screen twice on every frame.
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = TvRoutes.HOME) {
            composable(TvRoutes.HOME) {
                RailScaffold(RailTab.Home, go) { HomeScreen(container, go) }
            }
            composable(TvRoutes.SEARCH) {
                RailScaffold(RailTab.Search, go) { SearchScreen(container, go) }
            }
            composable(
                TvRoutes.LIBRARY_PAGE,
                arguments = listOf(
                    navArgument(TvRoutes.ARG_TAB) {
                type = NavType.StringType
                nullable = true
            }
                )
            ) { entry ->
                val tab = LibraryTab.entries.firstOrNull { it.name == entry.arguments?.getString(TvRoutes.ARG_TAB) }
                RailScaffold(RailTab.Library, go) { LibraryScreen(container, go, tab) }
            }
            composable(TvRoutes.SETTINGS) {
                RailScaffold(RailTab.Settings, go) { SettingsScreen(container.account) }
            }
            composable(TvRoutes.SHOW, arguments = listOf(navArgument(TvRoutes.ARG_SHOW) { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString(TvRoutes.ARG_SHOW).orEmpty()
                RailScaffold(RailTab.Home, go) { ShowScreen(id, container, go) }
            }
            composable(TvRoutes.EPISODE, arguments = listOf(navArgument(TvRoutes.ARG_EPISODE) { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString(TvRoutes.ARG_EPISODE).orEmpty()
                RailScaffold(RailTab.Home, go) { EpisodeScreen(id, container, go) }
            }
            composable(TvRoutes.PLAYER) {
                PlayerScreen(container, go)
            }
        }
    }
}
}
