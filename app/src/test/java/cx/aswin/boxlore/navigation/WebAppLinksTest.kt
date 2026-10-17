package cx.aswin.boxlore.navigation

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cx.aswin.boxlore.core.model.ShareLinkBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebAppLinksTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController

    @Test fun `podcast share links open the show on boxlore_in and both aswin_cx paths`() {
        render()
        listOf(
            "https://boxlore.in/share?type=podcast&id=123",
            "https://aswin.cx/boxlore/share?type=podcast&id=123",
            "https://aswin.cx/boxcast/share?type=podcast&id=123",
        ).forEach { link ->
            open("home")
            assertTrue(link, handle(link))
            assertEquals(link, PODCAST_ROUTE, navController.currentDestination?.route)
            assertEquals(link, "123", arg("podcastId"))
        }
    }

    @Test fun `episode share links keep their timestamp and clip on every domain`() {
        render()
        WebAppLinks.shareBases.forEach { base ->
            open("home")
            assertTrue(base, handle("$base?type=episode&id=ep-1&t=90"))
            assertEquals(base, EPISODE_ROUTE, navController.currentDestination?.route)
            assertEquals(base, "ep-1", arg("episodeId"))
            assertEquals(base, "90", arg("t"))

            open("home")
            assertTrue(base, handle("$base?type=episode&id=ep-2&start=10&end=20"))
            assertEquals(base, "ep-2", arg("episodeId"))
            assertEquals(base, "10", arg("start"))
            assertEquals(base, "20", arg("end"))
        }
    }

    @Test fun `links the app builds today open in the app`() {
        render()
        assertTrue(handle(ShareLinkBuilder.podcast("77")))
        assertEquals("77", arg("podcastId"))
        open("home")
        assertTrue(handle(ShareLinkBuilder.episode("ep-9", timestampMs = 30_000)))
        assertEquals("ep-9", arg("episodeId"))
        assertEquals("30", arg("t"))
    }

    @Test fun `only the share and sign-in paths are claimed`() {
        listOf(
            "https://boxlore.in/share?type=podcast&id=1",
            "https://boxlore.in/share/?type=episode&id=1",
            "https://boxlore.in/auth?mode=signIn",
            "https://aswin.cx/boxlore/share?type=podcast&id=1",
            "https://aswin.cx/boxcast/share?type=podcast&id=1",
            "https://aswin.cx/boxlore/auth?mode=signIn",
        ).forEach { assertTrue(it, WebAppLinks.isClaimed(Uri.parse(it))) }
        listOf(
            "https://boxlore.in/",
            "https://boxlore.in/privacy/",
            "https://boxlore.in/account-deletion/",
            "https://aswin.cx/boxlore/privacy/",
            "https://aswin.cx/share?type=podcast&id=1",
            "https://example.org/share?type=podcast&id=1",
        ).forEach { assertFalse(it, WebAppLinks.isClaimed(Uri.parse(it))) }
    }

    @Test fun `an unmatched boxlore_in link cannot reopen boxlore through the external fallback`() {
        val uri = Uri.parse("https://boxlore.in/share?type=unknown")
        assertEquals(Intent.ACTION_CHOOSER, externalPushActionIntent(uri).action)
        assertEquals(Intent.ACTION_VIEW, externalPushActionIntent(Uri.parse("https://boxlore.in/privacy/")).action)
    }

    private fun render() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") {}
                composable(
                    PODCAST_ROUTE,
                    arguments = listOf(navArgument("podcastId") { type = NavType.StringType }),
                    deepLinks = WebAppLinks.podcastShareDeepLinks(),
                ) {}
                composable(
                    EPISODE_ROUTE,
                    arguments = listOf(navArgument("episodeId") { type = NavType.StringType }) +
                        listOf("t", "start", "end", "autoplay").map { name ->
                            navArgument(name) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        },
                    deepLinks = WebAppLinks.episodeShareDeepLinks(),
                ) {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun open(route: String) {
        composeRule.runOnUiThread { navController.navigate(route) }
        composeRule.waitForIdle()
    }

    private fun handle(link: String): Boolean {
        var handled = false
        composeRule.runOnUiThread { handled = navController.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
        composeRule.waitForIdle()
        return handled
    }

    private fun arg(name: String): String? = navController.currentBackStackEntry?.arguments?.getString(name)

    private companion object {
        const val PODCAST_ROUTE = "podcast/{podcastId}"
        const val EPISODE_ROUTE = "episode/{episodeId}?t={t}&start={start}&end={end}&autoplay={autoplay}"
    }
}
