package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationBaseColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationEntryId
import cx.aswin.boxlore.core.designsystem.theme.generatePersonalColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkInfoThemeHandoffTest {
    @get:Rule val composeRule = createComposeRule()
    private val base = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
    private val parent = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false)
    private val loading = mutableStateOf(true)
    private val enabled = mutableStateOf(true)
    private val shellColors = mutableStateOf(parent)
    private val palettes = ArtworkNavigationColors()
    private lateinit var visible: ColorScheme
    private lateinit var loaderColors: ColorScheme

    @Test fun `loader starts with the parent palette and holds it through a slow content load`() {
        render()
        assertEquals(parent.primaryContainer, visible.primaryContainer)
        assertEquals(parent.onPrimaryContainer, visible.onPrimaryContainer)
        assertEquals(parent.background, visible.background)
        assertEquals(visible.primary, loaderColors.primary)
        assertEquals(parent.primary, palettes.colorsFor("child")?.primary)
        shellColors.value = generatePersonalColorScheme(CustomThemeSeeds(Color.Green), false)
        composeRule.mainClock.advanceTimeBy(3000)
        composeRule.waitForIdle()
        assertEquals(parent.primary, visible.primary)
        assertEquals(parent.background, visible.background)
    }

    @Test fun `missing artwork eases back to the saved palette instead of the transient shell theme`() {
        render()
        loading.value = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertNotEquals(parent.primary, visible.primary)
        assertNotEquals(base.primary, visible.primary)
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(base.primary, visible.primary)
        assertEquals(base.background, visible.background)
        assertEquals(visible.primary, loaderColors.primary)
        assertEquals(visible.primary, palettes.colorsFor("child")?.primary)
    }

    @Test fun `artwork opt out bypasses the inherited loader colors from its first frame`() {
        enabled.value = false
        render()
        assertEquals(base.primary, visible.primary)
        assertEquals(base.background, visible.background)
        assertEquals(base.primary, palettes.colorsFor("child")?.primary)
    }

    @Test fun `back resets a still composed loader even when its source palette matches the previous handoff`() {
        render()
        loading.value = false
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(base.primary, visible.primary)
        composeRule.runOnUiThread {
            palettes.beginEntry("next", base, true)
            palettes.update("next", parent)
            loading.value = true
            palettes.beginEntry("child", base, true)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(parent.primary, visible.primary)
        assertEquals(parent.background, visible.background)
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(parent.primary, palettes.colorsFor("child")?.primary)
    }

    private fun render() {
        palettes.beginEntry("parent", base, true)
        palettes.update("parent", parent)
        palettes.beginEntry("child", base, true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme(colorScheme = shellColors.value) {
                CompositionLocalProvider(
                    LocalArtworkColorsEnabled provides enabled.value,
                    LocalArtworkNavigationBaseColors provides base,
                    LocalArtworkNavigationColors provides palettes,
                    LocalArtworkNavigationEntryId provides "child",
                ) {
                    ArtworkInfoTheme(emptyList(), isLoading = loading.value) {
                        visible = MaterialTheme.colorScheme
                        loaderColors = LocalArtworkLoaderColors.current!!
                        BoxLoreLoader.Expressive()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
