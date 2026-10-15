package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.prefs.HeroCoverShade
import cx.aswin.boxlore.core.prefs.OpenAppTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h1600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearancePagesTest {
    @get:Rule val compose = createComposeRule()

    private val baseState = AppearanceUiState(currentThemeConfig = "system", isDynamicColorEnabled = false, currentThemeBrand = "violet", currentSurfaceStyle = "classic_dynamic")
    private val state = mutableStateOf(baseState)
    private val styles = mutableListOf<String>()
    private val shades = mutableListOf<String>()
    private var previewButtonClicks = 0

    private val actions = AppearanceActions(
        {},
        {},
        {},
        {},
        onSetHeroCardStyle = { style ->
            styles += style
            state.value = state.value.copy(heroCardStyle = style)
        },
        onSetHeroCoverShade = { shades += it },
    )

    /** A stand-in for Home's cards, with a button that must never receive a preview's taps. */
    private val fakePreview: AppearanceHeroPreview = { style, _, collection, modifier ->
        Box(modifier) {
            Button(onClick = { previewButtonClicks++ }) { Text(if (collection) "$style collection" else style) }
        }
    }

    @Test
    fun `overview rows summarise each page and open it`() {
        val opened = mutableListOf<AppearanceSection>()
        state.value = baseState.copy(heroCardStyle = HeroCardStyle.COVER, heroCoverShade = HeroCoverShade.THEME, currentOpenAppTo = OpenAppTo.DOWNLOADS)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AppearanceSettingsPage(state.value, actions, onBack = {}, onOpenSection = { opened += it })
            }
        }
        compose.onNodeWithText("Display mode").assertExists()
        verifyRows(opened)
    }

    @Test
    fun `theme and colors also offers display mode above the looks`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AppearanceThemePage(state.value, actions, onBack = {}, onEditCustomTheme = {})
            }
        }
        compose.onNodeWithText("Display mode").assertExists()
        compose.onNodeWithText("boxlore classic").assertIsSelected()
    }

    private fun verifyRows(opened: MutableList<AppearanceSection>) {
        compose.onNodeWithContentDescription("Preview of Cover hero cards").assertDoesNotExist()
        compose.onNodeWithText("Cover hero cards, app theme shade").assertExists()
        compose.onNodeWithText("Floating bar · starts on Downloads").assertExists()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Text").performScrollTo().performClick()
        assertEquals(listOf(AppearanceSection.Home, AppearanceSection.Text), opened)
    }

    @Test
    fun `hero style tiles select a style, cover adds its shade, and previews take no taps`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AppearanceHomePage(state.value, actions, heroPreview = fakePreview, onBack = {})
            }
        }
        compose.onNodeWithText("Hero card preview").assertExists()
        compose.onNodeWithText("Block").assertIsSelected()
        compose.onNodeWithText("Shade behind text").assertDoesNotExist()
        compose.onNodeWithContentDescription("Preview of Block hero cards").performTouchInput { click(center) }
        assertEquals(0, previewButtonClicks)

        compose.onNodeWithText("Cover").assertIsNotSelected().performClick()
        assertEquals(listOf(HeroCardStyle.COVER), styles)
        compose.onNodeWithText("Cover").assertIsSelected()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(listOf(HeroCoverShade.DARK), shades)

        // The styles scroll sideways; Duotone takes the same colours under its own name.
        compose.onNodeWithText("Duotone").performScrollTo().performClick()
        assertEquals(listOf(HeroCardStyle.COVER, HeroCardStyle.DUOTONE), styles)
        compose.onNodeWithText("Shade behind text").assertDoesNotExist()
        compose.onNodeWithText("Duotone colors").assertExists()

        // Halo sits further along the row and calls the same choice its card color.
        compose.onNodeWithText("Halo").performScrollTo().performClick()
        assertEquals(listOf(HeroCardStyle.COVER, HeroCardStyle.DUOTONE, HeroCardStyle.HALO), styles)
        compose.onNodeWithText("Duotone colors").assertDoesNotExist()
        compose.onNodeWithText("Card color").assertExists()

        // Tide sits between Cover and Duotone and takes the same card color.
        compose.onNodeWithText("Tide").performScrollTo().performClick()
        assertEquals(listOf(HeroCardStyle.COVER, HeroCardStyle.DUOTONE, HeroCardStyle.HALO, HeroCardStyle.TIDE), styles)
        compose.onNodeWithText("Card color").assertExists()

        // Marquee is last and names the Dark choice for what it does: covers stay in full colour.
        compose.onNodeWithText("Marquee").performScrollTo().performClick()
        assertEquals(HeroCardStyle.MARQUEE, styles.last())
        compose.onNodeWithText("Cover colors").assertExists()
        compose.onNodeWithText("Full color").assertExists()
        compose.onNodeWithText("Dark").assertDoesNotExist()
        assertEquals("Buttons drawn inside previews never react", 0, previewButtonClicks)
    }

    @Test
    fun `summary names the duotone, halo and tide colours`() {
        assertEquals("Duotone hero cards, black and white", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.DUOTONE, heroCoverShade = HeroCoverShade.DARK)).home)
        assertEquals("Duotone hero cards, artwork tones", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.DUOTONE)).home)
        assertEquals("Halo hero cards, artwork color", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.HALO)).home)
        assertEquals("Halo hero cards, dark cards", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.HALO, heroCoverShade = HeroCoverShade.DARK)).home)
        assertEquals("Tide hero cards, app theme color", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.TIDE, heroCoverShade = HeroCoverShade.THEME)).home)
        assertEquals("Marquee hero cards, full color", appearanceSummary(baseState.copy(heroCardStyle = HeroCardStyle.MARQUEE, heroCoverShade = HeroCoverShade.DARK)).home)
    }

    @Test
    fun `summary names the look, letters and player choices`() {
        val summary = appearanceSummary(baseState.copy(artworkColorsEnabled = false, fullPlayerStyle = FullPlayerStyle.BUNPOD, miniPlayerSeekButtonsEnabled = true, currentFontRoundness = "crisp"))
        assertEquals("boxlore classic", summary.theme)
        assertEquals("Block hero cards", summary.home)
        assertEquals("bunpod · seek buttons on", summary.player)
        assertEquals("Crisp letters", summary.text)
        assertEquals("boxlore classic · artwork colors on", appearanceSummary(baseState).theme)
    }
}
