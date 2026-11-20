package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.feature.settings.trackedForAnalytics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › App language: every language named in itself, the AI note, picking a
 * language, and the page keeping its choice while the window moves across the 600dp and 840dp
 * width gates and the 480dp height gate in both directions, and through recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceLanguagePageTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val language = mutableStateOf(AppLanguage.SYSTEM)
    private val picks = mutableListOf<AppLanguage>()

    private fun state() = AppearanceUiState(
        currentThemeConfig = "system",
        isDynamicColorEnabled = false,
        currentThemeBrand = "classic",
        currentSurfaceStyle = "standard",
        appLanguage = language.value,
    )

    private val actions = AppearanceActions(
        {},
        {},
        {},
        {},
        onSetAppLanguage = {
            picks += it
            language.value = it
        },
    )

    @Composable
    private fun languagePage() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                AppearanceLanguagePage(state(), actions, onBack = {})
            }
        }
    }

    private fun choice(name: String) = compose.onNode(isSelectable() and hasText(name))

    @Test
    fun `lists the phone's language and each language in its own words, with the AI note`() {
        compose.setContent { languagePage() }
        listOf("System default", "English", "Español", "Português (Brasil)", "Deutsch", "Français").forEach {
            choice(it).performScrollTo().assertIsDisplayed()
        }
        choice("System default").assertIsSelected()
        compose.onNodeWithText("Translations are made with AI help. Spot a mistake? Tell us in Feedback.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `picking a language hands it to the app once`() {
        compose.setContent { languagePage() }
        choice("Deutsch").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AppLanguage.GERMAN), picks)
        choice("Deutsch").assertIsSelected()
        choice("System default").assertIsNotSelected()
    }

    @Test
    fun `the choice stays across phone, tablet, split and short windows both ways`() {
        language.value = AppLanguage.FRENCH
        compose.setContent { languagePage() }
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(1280f, 480f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            choice("Français").performScrollTo().assertIsSelected()
        }
        choice("Español").performScrollTo().performClick()
        compose.runOnIdle { window.value = BoxloreWindowLayout(1280f, 800f) }
        choice("Español").performScrollTo().assertIsSelected()
        assertEquals(listOf(AppLanguage.SPANISH), picks)
    }

    @Test
    fun `the page survives recreation`() {
        language.value = AppLanguage.PORTUGUESE_BRAZIL
        val restoration = StateRestorationTester(compose)
        restoration.setContent { languagePage() }
        restoration.emulateSavedInstanceStateRestore()
        choice("Português (Brasil)").performScrollTo().assertIsSelected()
    }

    @Test
    fun `the Appearance row shows the picked language and opens the page`() {
        language.value = AppLanguage.GERMAN
        var opened: AppearanceSection? = null
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AppearanceSettingsPage(state(), actions, onBack = {}, onOpenSection = { opened = it })
            }
        }
        compose.onNodeWithText("Deutsch").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("App language").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppearanceSection.Language, opened) }
    }

    @Test
    fun `the analytics wrapper passes the pick on, reported as tags`() {
        val reached = mutableListOf<AppLanguage>()
        val tracked = AppearanceActions({}, {}, {}, {}, onSetAppLanguage = { reached += it }).trackedForAnalytics { state() }
        tracked.onSetAppLanguage(AppLanguage.SPANISH)
        tracked.onSetAppLanguage(AppLanguage.SYSTEM)
        assertEquals(listOf(AppLanguage.SPANISH, AppLanguage.SYSTEM), reached)
        assertEquals("pt-BR", appLanguageAnalyticsValue(AppLanguage.PORTUGUESE_BRAZIL))
        assertEquals("system", appLanguageAnalyticsValue(AppLanguage.SYSTEM))
    }
}
