package cx.aswin.boxlore.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.navigation.TabletContextRoot
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import cx.aswin.boxlore.navigation.rememberTabletDetailNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletAppShellTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_400, false))
    private val window = mutableStateOf(BoxloreWindowLayout(1_280f, 800f))
    private val playing = mutableStateOf(false)
    private val allowed = mutableStateOf(true)
    private lateinit var navigation: TabletDetailNavigation
    private lateinit var restoration: StateRestorationTester

    @Test
    fun `empty context closes and show details open beside retained browsing state`() {
        render()
        val initial = fixture.compose.onNodeWithTag("primary_pane").fetchSemanticsNode().boundsInRoot
        assertEquals(1_200f, initial.width, 1f)
        fixture.compose.onNodeWithText("Count 0").performClick()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        assertEquals("home", navigation.primary.currentDestination?.route)
        fixture.compose.onNodeWithText("Show detail rss:show/test").assertExists()
        val primary = fixture.compose.onNodeWithTag("primary_pane").fetchSemanticsNode().boundsInRoot
        val detail = fixture.compose.onNodeWithTag("detail_pane").fetchSemanticsNode().boundsInRoot
        assertTrue(detail.width in 360f..520f)
        assertTrue(primary.right + 15f <= detail.left)
        fixture.compose.onNodeWithText("Close details").performClick()
        advance()
        fixture.compose.onNodeWithText("Count 1").assertExists()
        assertEquals(initial.width, fixture.compose.onNodeWithTag("primary_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun `details return to playback and native Back closes nested details first`() {
        playing.value = true
        render()
        fixture.compose.onNodeWithText("Pane playback").assertExists()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        fixture.compose.onNodeWithText("Open episode").performClick()
        advance()
        fixture.compose.onNodeWithText("Episode detail").assertExists()
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        advance()
        fixture.compose.onNodeWithText("Show detail rss:show/test").assertExists()
        fixture.compose.onNodeWithText("Now playing").performClick()
        advance()
        fixture.compose.onNodeWithText("Pane playback").assertExists()
        assertEquals("home", navigation.primary.currentDestination?.route)
    }

    @Test
    fun `detail and primary saved state survive portrait resize and restoration`() {
        playing.value = true
        render()
        fixture.compose.onNodeWithText("Count 0").performClick()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        fixture.compose.runOnIdle { window.value = BoxloreWindowLayout(840f, 1_280f) }
        advance()
        assertEquals(80f, fixture.compose.onNodeWithTag("detail_pane").fetchSemanticsNode().boundsInRoot.left, 1f)
        fixture.compose.onNodeWithText("Docked playback").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        advance()
        fixture.compose.onNodeWithText("Show detail rss:show/test").assertExists()
        fixture.compose.onNodeWithText("Now playing").performClick()
        advance()
        fixture.compose.onNodeWithText("Count 1").assertExists()
    }

    @Test
    fun `hidden detail releases Back while retaining its own stack`() {
        render()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        fixture.compose.onNodeWithText("Show state 0").performClick()
        fixture.compose.runOnUiThread {
            navigation.primary.navigate("settings")
            allowed.value = false
        }
        advance()
        fixture.compose.onNodeWithText("Primary settings").assertExists()
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        advance()
        assertEquals("home", navigation.primary.currentDestination?.route)
        assertEquals("podcast/{id}", navigation.detail.currentDestination?.route)
        fixture.compose.runOnIdle { allowed.value = true }
        advance()
        fixture.compose.onNodeWithText("Show state 1").assertExists()
    }

    @Test
    fun `compact resize keeps playback above details and external links replace the visible target`() {
        playing.value = true
        render()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        fixture.compose.runOnIdle { window.value = BoxloreWindowLayout(480f, 960f) }
        advance()
        fixture.compose.onNodeWithText("Docked playback").assertExists()
        val details = fixture.compose.onNodeWithTag("detail_pane").fetchSemanticsNode().boundsInRoot
        assertTrue(details.bottom < 960f)
        fixture.compose.runOnUiThread { navigation.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("boxlore://episode/notification"))) }
        advance()
        assertEquals(TabletContextRoot, navigation.detail.currentDestination?.route)
        assertEquals("notification", navigation.primary.currentBackStackEntry?.arguments?.getString("id"))
    }

    @Test
    fun `wide external links target context instead of the retained primary route`() {
        render()
        fixture.compose.onNodeWithText("Open show").performClick()
        advance()
        fixture.compose.runOnUiThread {
            navigation.immersive = true
            navigation.handleDeepLink(Intent(Intent.ACTION_VIEW, Uri.parse("boxlore://episode/notification")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        advance()
        assertEquals("home", navigation.primary.currentDestination?.route)
        assertEquals("notification", navigation.detail.currentBackStackEntry?.arguments?.getString("id"))
        assertTrue(!navigation.immersive)
    }

    private fun render() {
        fixture.prepare()
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val primary = rememberNavController()
            navigation = rememberTabletDetailNavigation(primary)
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp).height(window.value.heightDp.dp)) {
                        TabletAppShell(
                            navigation,
                            true,
                            allowed.value,
                            playing.value,
                            "home",
                            {},
                            TabletShellContent(
                            primary = {
                                NavHost(primary, "home") {
                                    composable("home") {
                                        var count by rememberSaveable { mutableIntStateOf(0) }
                                        androidx.compose.foundation.layout.Column {
                                            Button(onClick = { count++ }) { Text("Count $count") }
                                            Button(onClick = { navigation.navigate(primary, "podcast/rss%3Ashow%2Ftest") }) { Text("Open show") }
                                        }
                                    }
                                    composable("settings") { Text("Primary settings") }
                                    composable("episode/{id}", deepLinks = listOf(navDeepLink { uriPattern = "boxlore://episode/{id}" })) { Text("Primary episode") }
                                }
                            },
                            details = {
                                NavHost(navigation.detail, TabletContextRoot) {
                                    composable(TabletContextRoot) {}
                                    composable("podcast/{id}") { entry ->
                                        var count by rememberSaveable { mutableIntStateOf(0) }
                                        androidx.compose.foundation.layout.Column {
                                            Text("Show detail ${entry.arguments?.getString("id")}")
                                            Button(onClick = { count++ }) { Text("Show state $count") }
                                            Button(onClick = { navigation.navigate(navigation.detail, "episode/one") }) { Text("Open episode") }
                                        }
                                    }
                                    composable("episode/{id}", deepLinks = listOf(navDeepLink { uriPattern = "boxlore://episode/{id}" })) { Text("Episode detail") }
                                }
                            },
                            player = { context -> Text(if (context) "Pane playback" else "Docked playback") },
                            ),
                        )
                    }
                }
            }
        }
        advance()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
