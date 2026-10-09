package cx.aswin.boxlore.navigation

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationBaseColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ArtworkNavigationHostThemeTest {
    @get:Rule val composeRule = createComposeRule()
    private val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
    private val parent = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false)
    private val child = generatePersonalColorScheme(CustomThemeSeeds(Color.Green), false)
    private val palettes = ArtworkNavigationColors()
    private val childReady = mutableStateOf(false)
    private val returning = mutableStateOf(false)
    private val parentReady = mutableStateOf(false)
    private lateinit var navController: NavHostController
    private var firstChildPrimary: Color? = null
    private var firstSettingsPrimary: Color? = null
    private var firstReturningPrimary: Color? = null
    private var firstHomeColors: ColorScheme? = null
    private var firstHomeLoaderPrimary: Color? = null
    private var sourceRendered = false
    private var savedTheme: ColorScheme? = null
    private lateinit var settingsColors: ColorScheme
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Test fun `held predictive back previews the home palette without committing or changing the source`() {
        render(startDestination = "home")
        navigate("episode/parent")
        assertNull(palettes.appThemePreviewEntryId)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertNull(palettes.appThemePreviewEntryId)
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()
        val sourceId = navController.currentBackStackEntry!!.id
        val homeId = navController.previousBackStackEntry!!.id
        assertEquals(androidx.lifecycle.Lifecycle.State.CREATED, navController.previousBackStackEntry!!.lifecycle.currentState)
        composeRule.runOnUiThread {
            firstHomeColors = null
            org.junit.Assert.assertTrue("Native Back handler must be enabled", backDispatcher.hasEnabledCallbacks())
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertEquals(sourceId, navController.currentBackStackEntry?.id)
        assertSame(base, firstHomeColors)
        assertEquals("Visible: ${navController.visibleEntries.value.map { it.destination.route }}; previous: ${navController.previousBackStackEntry?.lifecycle?.currentState}", homeId, palettes.appThemePreviewEntryId)
        assertEquals(parent.primary, palettes.colorsFor(sourceId)?.primary)
        composeRule.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()
        assertNull(palettes.appThemePreviewEntryId)
        assertEquals(sourceId, navController.currentBackStackEntry?.id)
        assertEquals(parent.primary, palettes.colorsFor(sourceId)?.primary)
        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnUiThread { backDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()
        assertEquals(homeId, navController.currentBackStackEntry?.id)
        assertNull(palettes.appThemePreviewEntryId)
        assertSame(base, palettes.colorsFor(homeId))
    }

    @Test fun `real navigation captures source colors before the incoming loader is composed`() {
        render()
        navigate("podcast/child")
        assertEquals(parent.primary, firstChildPrimary)
        assertSame(base, savedTheme)
        assertEquals(parent.background, palettes.transitionColors?.background)
        assertEquals(parent.primary, palettes.colorsFor(navController.currentBackStackEntry?.id)?.primary)
    }

    @Test fun `back loader and slide inherit departing colors before the returning page restores its palette`() {
        render()
        val parentId = navController.currentBackStackEntry!!.id
        navigate("podcast/child")
        childReady.value = true
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            returning.value = true
            navController.popBackStack()
        }
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 1000) { firstReturningPrimary != null }
        composeRule.waitForIdle()
        assertEquals(parentId, navController.currentBackStackEntry?.id)
        assertEquals(child.primary, firstReturningPrimary)
        assertEquals(child.primary, palettes.initialColorsFor(parentId)?.primary)
        assertEquals(child.primary, palettes.colorsFor(parentId)?.primary)
        assertEquals(child.background, palettes.transitionColors?.background)
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(child.primary, palettes.colorsFor(parentId)?.primary)
        parentReady.value = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(parent.primary, palettes.colorsFor(parentId)?.primary)
    }

    @Test fun `ordinary page loaders inherit source colors without recoloring page content or navbar`() {
        render()
        navigate("settings")
        assertEquals(parent.primary, firstSettingsPrimary)
        assertSame(base, settingsColors)
        assertSame(base, palettes.colorsFor(navController.currentBackStackEntry?.id))
        composeRule.mainClock.advanceTimeBy(1600)
        composeRule.waitForIdle()
        assertEquals(base.primary, settingsColors.primary)
        assertEquals(base.background, settingsColors.background)
        assertEquals(base.primary, palettes.colorsFor(navController.currentBackStackEntry?.id)?.primary)
    }

    @Test fun `back to home restores one saved palette while only the loader inherits the departing accent`() {
        render(startDestination = "home")
        navigate("episode/parent")
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            firstHomeColors = null
            firstHomeLoaderPrimary = null
            navController.popBackStack()
        }
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 1000) { firstHomeColors != null }
        composeRule.waitForIdle()
        assertSame(base, firstHomeColors)
        assertSame(base, palettes.colorsFor(navController.currentBackStackEntry?.id))
        assertEquals(parent.primary, firstHomeLoaderPrimary)
        assertEquals(parent.background, palettes.transitionColors?.background)
    }

    private fun navigate(route: String) {
        composeRule.runOnUiThread { navController.navigate(route) }
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 1000) {
            when (route) {
                "podcast/child" -> firstChildPrimary != null
                "settings" -> firstSettingsPrimary != null
                "home" -> firstHomeColors != null
                "episode/parent" -> sourceRendered
                else -> false
            }
        }
        composeRule.waitForIdle()
    }

    private fun render(startDestination: String = "episode/parent") {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            navController = rememberNavController()
            MaterialTheme(colorScheme = base) {
                CompositionLocalProvider(LocalArtworkNavigationColors provides palettes, LocalArtworkColorsEnabled provides true) {
                    ArtworkNavigationHostTheme(navController) { background ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = background,
                            enterTransition = { navEnterTransition(initialState.destination.route, targetState.destination.route) },
                            exitTransition = { navExitTransition(initialState.destination.route, targetState.destination.route) },
                            popEnterTransition = { navPopEnterTransition(initialState.destination.route, targetState.destination.route) },
                            popExitTransition = { navPopExitTransition(initialState.destination.route, targetState.destination.route) },
                        ) {
                            composable("episode/parent") { entry ->
                                sourceRendered = true
                                if (returning.value && firstReturningPrimary == null) firstReturningPrimary = LocalArtworkLoaderColors.current?.primary
                                val colors = if (returning.value && !parentReady.value) palettes.initialColorsFor(entry.id) ?: base else parent
                                SideEffect { palettes.update(entry.id, colors) }
                                MaterialTheme(colorScheme = colors) {
                                    if (returning.value && !parentReady.value) BoxLoreLoader.Expressive() else Box(Modifier.fillMaxSize().background(colors.background))
                                }
                            }
                            composable("podcast/child") { entry ->
                                if (firstChildPrimary == null) firstChildPrimary = LocalArtworkLoaderColors.current?.primary
                                savedTheme = LocalArtworkNavigationBaseColors.current
                                val colors = if (childReady.value) child else palettes.initialColorsFor(entry.id) ?: base
                                SideEffect { palettes.update(entry.id, colors) }
                                MaterialTheme(colorScheme = colors) { BoxLoreLoader.Expressive() }
                            }
                            composable("settings") {
                                settingsColors = MaterialTheme.colorScheme
                                if (firstSettingsPrimary == null) firstSettingsPrimary = LocalArtworkLoaderColors.current?.primary
                                BoxLoreLoader.Expressive()
                            }
                            composable("home") {
                                if (firstHomeColors == null) firstHomeColors = MaterialTheme.colorScheme
                                if (firstHomeLoaderPrimary == null) firstHomeLoaderPrimary = LocalArtworkLoaderColors.current?.primary
                                BoxLoreLoader.Expressive()
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
