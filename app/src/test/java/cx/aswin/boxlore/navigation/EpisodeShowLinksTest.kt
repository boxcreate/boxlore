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
import cx.aswin.boxlore.core.catalog.ReferralIntent
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.ShareLinkBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The episode route's deep links ([episodeDeepLinks]) on the real route and arguments: links that
 * name the show (`?show=` from the share page, `&show=` in share links) put it in `podcastId`, with
 * and without a timestamp or clip, and every older link still opens as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpisodeShowLinksTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController

    @Test
    fun `the share page's app links carry the show, with and without a timestamp`() {
        render()
        expect("boxlore://episode/$RECIPE?show=$SHOW", show = SHOW)
        expect("boxlore://episode/$RECIPE?t=90&show=$SHOW", show = SHOW, t = "90")
        expect("boxlore://episode/$RECIPE?show=$SHOW&t=90", show = SHOW, t = "90")
        expect("boxlore://episode/$RECIPE?show=$SHOW&start=10&end=20&autoplay=false", show = SHOW, start = "10", end = "20", autoplay = "false")
        expect("boxlore://episode/$RECIPE?show=$SHOW&autoplay=false", show = SHOW, autoplay = "false")
    }

    @Test
    fun `share links with the show open on every share address`() {
        render()
        WebAppLinks.shareBases.forEach { base ->
            expect("$base?type=episode&id=$RECIPE&show=$SHOW", show = SHOW)
            expect("$base?type=episode&id=$RECIPE&show=$SHOW&t=90", show = SHOW, t = "90")
            expect("$base?type=episode&id=$RECIPE&t=90&show=$SHOW", show = SHOW, t = "90")
            expect("$base?type=episode&id=$RECIPE&show=$SHOW&start=10&end=20", show = SHOW, start = "10", end = "20")
            expect("$base?type=episode&id=$RECIPE&show=$SHOW&autoplay=false", show = SHOW, autoplay = "false")
        }
    }

    @Test
    fun `links the app builds now open with their show`() {
        render()
        val link = ShareLinkBuilder.forTarget(EpisodeShareTarget.Episode(RECIPE, SHOW), timestampMs = 30_000)
        expect(link, show = SHOW, t = "30")
        expect(ShareLinkBuilder.forTarget(EpisodeShareTarget.Episode("-5", null)), id = "-5")
    }

    @Test
    fun `older links keep opening without a show`() {
        render()
        expect("boxlore://episode/5001", id = "5001")
        expect("boxlore://episode/5001?t=90", id = "5001", t = "90")
        expect("boxlore://episode/5001?t=90&start=10&end=20&autoplay=false", id = "5001", t = "90", start = "10", end = "20", autoplay = "false")
        expect("boxcast://episode/5001?t=5", id = "5001", t = "5")
        expect("boxcast://episode/5001", id = "5001")
        WebAppLinks.shareBases.forEach { base ->
            expect("$base?type=episode&id=5001", id = "5001")
            expect("$base?type=episode&id=5001&t=90", id = "5001", t = "90")
            expect("$base?type=episode&id=5001&start=10&end=20", id = "5001", start = "10", end = "20")
        }
    }

    @Test
    fun `the notifier's route keeps its show and title`() {
        render()
        expect("boxlore://episode/5001?autoplay=false&podcastId=$SHOW&podcastTitle=Show", id = "5001", show = SHOW, autoplay = "false", title = "Show")
        expect("boxlore://episode/5001?autoplay=false", id = "5001", autoplay = "false")
    }

    @Test
    fun `an install referrer opens the episode with its show, and without one as before`() {
        render()
        val withShow = cx.aswin.boxlore.ui.installReferralRoute(ReferralIntent("episode", RECIPE, timestamp = 30, showId = SHOW))!!
        open(withShow)
        assertEquals(ROUTE, navController.currentDestination?.route)
        assertEquals(RECIPE, arg("episodeId"))
        assertEquals(SHOW, arg("podcastId"))
        assertEquals("30", arg("t"))
        assertEquals("install_referrer", arg("entryPoint"))

        open(cx.aswin.boxlore.ui.installReferralRoute(ReferralIntent("episode", "5001", start = 10, end = 20))!!)
        assertEquals("5001", arg("episodeId"))
        assertEquals("", arg("podcastId"))
        assertEquals("10", arg("start"))
        assertEquals("20", arg("end"))
        assertEquals("podcast/920666?entryPoint=install_referrer", cx.aswin.boxlore.ui.installReferralRoute(ReferralIntent("podcast", SHOW)))
        assertEquals(null, cx.aswin.boxlore.ui.installReferralRoute(ReferralIntent("unknown", SHOW)))
    }

    @Suppress("LongParameterList", "kotlin:S107") // One expected value per route argument.
    private fun expect(
        link: String,
        id: String = RECIPE,
        show: String = "",
        t: String? = null,
        start: String? = null,
        end: String? = null,
        autoplay: String = "true",
        title: String = "",
    ) {
        open("home")
        assertTrue(link, handle(link))
        assertEquals(link, ROUTE, navController.currentDestination?.route)
        assertEquals(link, id, arg("episodeId"))
        assertEquals(link, show, arg("podcastId"))
        assertEquals(link, t, arg("t"))
        assertEquals(link, start, arg("start"))
        assertEquals(link, end, arg("end"))
        assertEquals(link, autoplay, arg("autoplay"))
        assertEquals(link, title, arg("podcastTitle"))
    }

    private fun render() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") {}
                composable(
                    ROUTE,
                    arguments = listOf(
                        navArgument("episodeId") { type = NavType.StringType },
                        nullable("entryPoint", null),
                        nullable("t", null),
                        nullable("start", null),
                        nullable("end", null),
                        nullable("autoplay", "true"),
                        nullable("podcastId", ""),
                        nullable("podcastTitle", ""),
                    ),
                    deepLinks = episodeDeepLinks(),
                ) {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun nullable(name: String, default: String?) = navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = default
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
        /** The real episode deep-link route (NavGraphPodcastEpisodeDestinations). */
        const val ROUTE =
            "episode/{episodeId}?entryPoint={entryPoint}&t={t}&start={start}&end={end}&autoplay={autoplay}&podcastId={podcastId}&podcastTitle={podcastTitle}"
        const val RECIPE = "4503599627370500"
        const val SHOW = "920666"
    }
}
