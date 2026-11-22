package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.designsystem.component.AppLoreNavigationActionSize
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHorizontalInset
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.appMiniPlayerTopOffset
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.navigation.rememberTabletDetailNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a sideways phone with 3-button navigation and a side camera cutout, the floating bar's Lore
 * circle and the mini player stay clear of both and centre at most 560dp wide. Portrait phones keep
 * today's geometry, and turning the phone keeps the selected tab and the expanded player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneChromeInsetsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_000, 1_000, false))
    private val window = mutableStateOf(BoxloreTestWindows.PHONE_LAND)
    private val episode = TestFixtures.episode(title = "How we find our way", imageUrl = PhoneGoldenRule.ARTWORK_URL)

    @Test
    fun `Lore and the mini player stay clear of side buttons and the cutout`() {
        render(SystemInsets.SIDEWAYS_3_BUTTON)
        listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_LAND, BoxloreTestWindows.PHONE_LAND).forEach { size ->
            fixture.compose.runOnIdle { window.value = size }
            advance()
            listOf(lore(), play()).forEach { bounds ->
                assertTrue("$size $bounds", bounds.left >= 32f && bounds.right <= size.widthDp - 48f)
            }
        }
        fixture.compose.onNodeWithContentDescription("Lore").assertHasClickAction().performClick()
        advance()
        fixture.compose.onNodeWithContentDescription("Lore").assertIsSelected()
        fixture.compose.onNodeWithText("Lore page").assertExists()
    }

    @Test
    fun `wide short windows centre the bar and the mini player at 560dp`() {
        window.value = BoxloreWindowLayout(1_000f, 400f)
        render(null)
        // The circle sits at the right end of the 560dp bar, centred in the window.
        val barLeft = (1_000f - 560f) / 2f
        val barRight = (1_000f + 560f) / 2f
        assertEquals(barRight, lore().right, 1f)
        assertTrue("${play()}", play().left >= barLeft && play().right <= barRight)
    }

    @Test
    fun `portrait phones keep today's chrome geometry`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        render(SystemInsets(statusTop = 24, navBottom = 48))
        listOf(BoxloreTestWindows.PHONE_SMALL, BoxloreTestWindows.PHONE, BoxloreWindowLayout(430f, 932f)).forEach { size ->
            fixture.compose.runOnIdle { window.value = size }
            advance()
            // The Lore circle keeps its 16dp gutter from the window edge, as before.
            assertEquals(size.widthDp - AppNavigationBarHorizontalInset.value, lore().right, 0.5f)
            assertEquals(size.widthDp - AppLoreNavigationActionSize.value - AppNavigationBarHorizontalInset.value, lore().left, 0.5f)
            assertTrue(play().right <= size.widthDp - AppNavigationBarHorizontalInset.value)
        }
    }

    @Test
    fun `turning the phone keeps the selected tab and the expanded player`() {
        window.value = BoxloreTestWindows.PHONE
        render(SystemInsets.SIDEWAYS_3_BUTTON)
        fixture.compose.onNodeWithContentDescription("Lore").performClick()
        advance()
        fixture.compose.onNodeWithText(episode.title).performClick()
        advance()
        val collapse = fixture.compose.activity.getString(cx.aswin.boxlore.feature.player.R.string.player_collapse)
        fixture.compose.onNodeWithContentDescription(collapse).assertExists()
        fixture.compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND) {
            advance()
            fixture.compose.onNodeWithContentDescription(collapse).assertExists()
            fixture.compose.onNodeWithText("Lore page").assertExists()
        }
        fixture.compose.onNodeWithContentDescription(collapse).performClick()
        advance()
        fixture.compose.onNodeWithContentDescription("Lore").assertIsSelected()
        assertTrue(lore().right <= window.value.widthDp - 48f)
    }

    private fun lore(): Rect = fixture.compose.onNodeWithContentDescription("Lore").fetchSemanticsNode().boundsInRoot

    private fun play(): Rect {
        val label = fixture.compose.activity.getString(cx.aswin.boxlore.feature.player.R.string.minimal_play)
        return fixture.compose.onNodeWithContentDescription(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    private fun render(insets: SystemInsets?) {
        fixture.prepare()
        fixture.compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) { PhoneChrome() }
                }
            }
        }
        insets?.let { fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, it) } }
        advance()
    }

    /** The phone root's bottom bar and player sheet, placed as `BoxLoreAppRoot` places them. */
    @Composable
    private fun PhoneChrome() {
        val dependencies = remember { tabletCapturePlayer(episode, TestFixtures.podcast(imageUrl = PhoneGoldenRule.ARTWORK_URL)) }
        val compactProgress = remember { mutableFloatStateOf(0f) }
        val navigation = rememberTabletDetailNavigation(rememberNavController())
        val primary = rememberNavController()
        val entry by primary.currentBackStackEntryAsState()
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            NavHost(primary, "home") {
                listOf("home", "explore", "library").forEach { route -> composable(route) { Text("Page $route") } }
                composable("learn") { Text("Lore page") }
            }
            PhoneBottomNavigation(primary, navigation, entry?.destination?.route ?: "home", NavigationStyle.Floating, null, true, compactProgress)
            val systemNavigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val metrics = navigationChromeMetrics(NavigationStyle.Floating)
            val (leftInset, rightInset) = phoneChromeSideOffsets(NavigationStyle.Floating, maxWidth)
            PlayerSheetScaffold(
                playbackRepository = dependencies.playbackRepository,
                downloadRepository = dependencies.downloadRepository,
                userPrefs = dependencies.userPrefs,
                layout = PlayerSheetLayout(
                    collapsedTargetY = with(density) { appMiniPlayerTopOffset(NavigationStyle.Floating, maxHeight, systemNavigation).toPx() },
                    containerHeight = maxHeight + systemNavigation + 50.dp,
                    collapsedHorizontalPadding = AppNavigationBarHorizontalInset,
                    collapsedLeftInset = leftInset,
                    collapsedRightInset = rightInset,
                    navigationStyle = NavigationStyle.Floating,
                    compactProgress = compactProgress,
                    compactTargetY = with(density) {
                        (maxHeight - metrics.bottomNavigationClearance - systemNavigation + (metrics.navigationBarHeight - AppLoreNavigationActionSize) / 2).toPx()
                    },
                ),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
