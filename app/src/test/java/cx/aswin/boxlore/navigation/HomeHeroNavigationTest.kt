package cx.aswin.boxlore.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeHeroNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController

    @Test fun `spotlight opens the show even when a latest episode is available`() {
        render()
        for (id in listOf("123", "-42", "rss:show/test")) {
            navigate(item(HeroType.SPOTLIGHT, id))
            assertEquals(SHOW_ROUTE, navController.currentDestination?.route)
            assertEquals(id, navController.currentBackStackEntry?.arguments?.getString("podcastId"))
            assertEquals("home_hero_spotlight", navController.currentBackStackEntry?.arguments?.getString("entryPoint"))
        }
    }

    @Test fun `resume and fresh drop still open their exact episode with attribution`() {
        render()
        for (type in listOf(HeroType.RESUME, HeroType.JUMP_BACK_IN)) {
            navigate(item(type))
            assertEquals(EPISODE_ROUTE, navController.currentDestination?.route)
            assertEquals("episode-123", navController.currentBackStackEntry?.arguments?.getString("episodeId"))
            assertEquals("123", navController.currentBackStackEntry?.arguments?.getString("podcastId"))
            assertEquals("home_hero_${type.name.lowercase()}", navController.currentBackStackEntry?.arguments?.getString("entryPoint"))
            assertEquals("3", navController.currentBackStackEntry?.arguments?.getString("carouselPosition"))
        }
    }

    @Test fun `missing episode opens its show and can return to home`() {
        render()
        for (type in listOf(HeroType.SPOTLIGHT, HeroType.JUMP_BACK_IN)) {
            val hero = item(type)
            navigate(hero.copy(podcast = hero.podcast.copy(latestEpisode = null)))
            assertEquals(SHOW_ROUTE, navController.currentDestination?.route)
            assertEquals("123", navController.currentBackStackEntry?.arguments?.getString("podcastId"))
            composeRule.runOnUiThread { assertTrue(navController.popBackStack()) }
            composeRule.waitForIdle()
            assertEquals("home", navController.currentDestination?.route)
        }
    }

    private fun render() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") {}
                composable(
                    SHOW_ROUTE,
                    arguments = listOf(
                        navArgument("entryPoint") {
                    nullable = true
                    defaultValue = null
                }
                    )
                ) {}
                composable(
                    EPISODE_ROUTE,
                    arguments = listOf(
                    navArgument("entryPoint") {
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("carouselPosition") {
                        nullable = true
                        defaultValue = null
                    },
                )
                ) {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun navigate(hero: SmartHeroItem) {
        composeRule.runOnUiThread { navigateHomeHeroArrow(navController, hero, carouselPos = 3) }
        composeRule.waitForIdle()
    }

    private fun item(type: HeroType, id: String = "123") = SmartHeroItem(
        type = type,
        podcast = Podcast(id, "Show", "Creator", "show-cover", latestEpisode = Episode("episode-123", "Latest episode", "", "")),
        label = "Trending",
    )

    private companion object {
        const val SHOW_ROUTE = "podcast/{podcastId}?entryPoint={entryPoint}"
        const val EPISODE_ROUTE = "episode/{episodeId}/{episodeTitle}/{episodeDescription}/{episodeImageUrl}/{episodeAudioUrl}/{episodeDuration}/{podcastId}/{podcastTitle}?entryPoint={entryPoint}&carouselPosition={carouselPosition}"
    }
}
