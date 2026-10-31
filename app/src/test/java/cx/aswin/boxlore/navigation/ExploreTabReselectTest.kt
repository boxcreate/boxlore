package cx.aswin.boxlore.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExploreTabReselectTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController

    @Test fun `only the Explore page itself turns a repeat tap into a search request`() {
        assertTrue(shouldRequestExploreSearch("explore", "explore", ExploreTabRoutePattern, revealedPrimary = false))
        assertFalse(shouldRequestExploreSearch("explore", "explore", SHOW_ROUTE, revealedPrimary = false))
        assertFalse(shouldRequestExploreSearch("explore", "explore", ExploreTabRoutePattern, revealedPrimary = true))
        assertFalse(shouldRequestExploreSearch("explore", "home", "home", revealedPrimary = false))
        assertFalse(shouldRequestExploreSearch("home", "home", "home", revealedPrimary = false))
        assertFalse(shouldRequestExploreSearch("library", "explore", ExploreTabRoutePattern, revealedPrimary = false))
    }

    @Test fun `repeat tap on the Explore page requests search and stays on it`() {
        render()
        tap("explore", activeTab = "home")
        assertOnExplore(searchRequested = null)
        tap("explore", activeTab = "explore")
        assertOnExplore(searchRequested = true)
        assertEquals(listOf("home", ExploreTabRoutePattern), stackRoutes())
    }

    @Test fun `tap from a page opened in Explore only returns to Explore`() {
        render()
        tap("explore", activeTab = "home")
        composeRule.runOnUiThread { navController.navigate("podcast/123") }
        composeRule.waitForIdle()
        tap("explore", activeTab = "explore")
        assertOnExplore(searchRequested = null)
        tap("explore", activeTab = "explore")
        assertOnExplore(searchRequested = true)
    }

    @Test fun `tap that only closes a covering tablet detail does not open search`() {
        render()
        tap("explore", activeTab = "home")
        tap("explore", activeTab = "explore", revealedPrimary = true)
        assertOnExplore(searchRequested = null)
    }

    private fun tap(route: String, activeTab: String, revealedPrimary: Boolean = false) {
        composeRule.runOnUiThread { navController.selectBottomNavTab(route, activeTab, revealedPrimary) }
        composeRule.waitForIdle()
    }

    private fun assertOnExplore(searchRequested: Boolean?) {
        assertEquals(ExploreTabRoutePattern, navController.currentDestination?.route)
        val requested = navController.currentBackStackEntry?.savedStateHandle?.get<Boolean>(ExploreSearchRequestKey)
        if (searchRequested == null) assertNull(requested) else assertEquals(searchRequested, requested)
    }

    private fun stackRoutes(): List<String?> = snapshotNavBackStack(navController).mapNotNull { it.destination.route }

    private fun render() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") {}
                composable(
                    ExploreTabRoutePattern,
                    arguments = listOf("category", "entryPoint", "tab").map { name ->
                        navArgument(name) {
                            nullable = true
                            defaultValue = null
                        }
                    },
                ) {}
                composable(SHOW_ROUTE) {}
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val SHOW_ROUTE = "podcast/{podcastId}"
    }
}
