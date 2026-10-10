package cx.aswin.boxlore.navigation

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.MainActivity
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
class PushActionRouterTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var navController: NavHostController
    private val external = mutableListOf<Uri>()
    private val updates = mutableListOf<Boolean>()

    @Test fun `website and GitHub actions open externally when the graph cannot handle them`() {
        render()
        val urls = listOf("https://example.org/news", "https://github.com/boxcreate/boxlore/releases/tag/v29")
        urls.forEach { assertTrue(dispatch(it)) }
        assertEquals(urls, external.map(Uri::toString))
        assertEquals("home", navController.currentDestination?.route)
    }

    @Test fun `podcast and episode links still open their in app destinations`() {
        render()
        assertTrue(dispatch("boxlore://podcast/123"))
        assertEquals("podcast/{id}", navController.currentDestination?.route)
        assertEquals("123", navController.currentBackStackEntry?.arguments?.getString("id"))
        assertTrue(dispatch("boxcast://episode/-42"))
        assertEquals("episode/{id}", navController.currentDestination?.route)
        assertEquals("-42", navController.currentBackStackEntry?.arguments?.getString("id"))
        assertTrue(external.isEmpty())
    }

    @Test fun `matched HTTPS share links stay inside boxlore`() {
        render()
        assertTrue(dispatch("https://aswin.cx/boxlore/share?type=podcast&id=123"))
        assertEquals("podcast/{id}", navController.currentDestination?.route)
        assertTrue(external.isEmpty())
    }

    @Test fun `known relative and app scheme utility destinations both navigate`() {
        render()
        assertTrue(dispatch("feedback"))
        assertEquals("feedback", navController.currentDestination?.route)
        assertTrue(dispatch("boxlore://settings?page=support"))
        assertEquals("settings?page={page}", navController.currentDestination?.route)
        assertEquals("support", navController.currentBackStackEntry?.arguments?.getString("page"))
        assertTrue(dispatch("boxcast://library/downloads"))
        assertEquals("library/downloads", navController.currentDestination?.route)
        assertTrue(external.isEmpty())
    }

    @Test fun `update actions bypass navigation and select explicit download versus check`() {
        render()
        assertTrue(dispatch("boxlore://updates/download"))
        assertTrue(dispatch("boxlore://updates"))
        assertEquals(listOf(true, false), updates)
        assertTrue(external.isEmpty())
        assertEquals("home", navController.currentDestination?.route)
    }

    @Test fun `an existing release notification APK target also downloads inside the app`() {
        render()
        assertTrue(dispatch("https://github.com/boxcreate/boxlore/releases/download/v29/app.apk?download=1"))
        assertEquals(listOf(true), updates)
        assertTrue(external.isEmpty())
        assertEquals("home", navController.currentDestination?.route)
    }

    @Test fun `unsupported app and unsafe targets never open outside the app`() {
        render()
        listOf("boxlore://unknown/path", "javascript:alert(1)", "file:///tmp/app.apk", "https://", "unknown/path").forEach {
            assertFalse(it, dispatch(it))
        }
        assertTrue(external.isEmpty())
        assertTrue(updates.isEmpty())
        assertEquals("home", navController.currentDestination?.route)
    }

    @Test fun `unmatched verified App Links cannot reopen boxlore through the external fallback`() {
        render()
        val uri = Uri.parse("https://aswin.cx/boxlore/share?type=unknown")
        assertTrue(dispatch(uri.toString()))
        assertEquals(listOf(uri), external)
        val chooser = externalPushActionIntent(uri)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(uri, chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.data)
        assertEquals(listOf(ComponentName(BuildConfig.APPLICATION_ID, MainActivity::class.java.name)), chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName::class.java)?.toList())
    }

    @Test fun `ordinary website actions use the preferred handler without an extra chooser`() {
        val uri = Uri.parse("https://example.org/news")
        val intent = externalPushActionIntent(uri)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
    }

    private fun render() {
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") {}
                composable("feedback") {}
                composable("library/downloads") {}
                composable("settings?page={page}", arguments = listOf(navArgument("page") { defaultValue = "hub" })) {}
                composable(
                    "podcast/{id}",
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "boxlore://podcast/{id}" },
                        navDeepLink { uriPattern = "https://aswin.cx/boxlore/share?type=podcast&id={id}" },
                    ),
                ) {}
                composable("episode/{id}", deepLinks = listOf(navDeepLink { uriPattern = "boxcast://episode/{id}" })) {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun dispatch(target: String): Boolean {
        var result = false
        composeRule.runOnUiThread {
            result = dispatchPushAction(target, navController::handleDeepLink, { navController.navigate(it) }, { external += it }, { updates += it })
        }
        composeRule.waitForIdle()
        return result
    }
}
