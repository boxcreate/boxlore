package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library settings › Charts and Recommendations (D9): one thing per row, the languages sheet with
 * "Include English", and the open sheet staying put while the window moves between phone, tablet,
 * split and short layouts and when the activity is recreated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryLanguagesAdaptiveTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val region = mutableStateOf("in")
    private val languages = mutableStateOf(listOf("en", "hi"))
    private val englishOff = mutableStateOf(false)
    private val englishOffChanges = mutableListOf<Boolean>()
    private val languageChanges = mutableListOf<List<String>>()

    private val includeEnglish = isToggleable() and hasText("Include English", substring = true)

    @Composable
    private fun page() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                LibrarySettingsPage(
                    discoveryPreferences =
                    LibraryDiscoveryPreferences(
                        currentRegion = region.value,
                        contentLanguages = languages.value,
                        onSetRegion = { region.value = it },
                        onSetContentLanguages = { languageChanges += it },
                        englishOff = englishOff.value,
                        onSetEnglishOff = { englishOffChanges += it },
                    ),
                    onAddRssClick = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `each row says one thing`() {
        compose.setContent { page() }
        compose.onNodeWithText("Country").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("India").assertIsDisplayed()
        compose.onNodeWithText("English, Hindi").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `English stays on with Hindi and says why`() {
        compose.setContent { page() }
        compose.onNodeWithText("Languages").performScrollTo().performClick()
        compose.onNode(includeEnglish).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Stays on with Hindi", substring = true).assertIsDisplayed()
    }

    @Test
    fun `English can be switched off with German and its slot goes to a pick`() {
        region.value = "de"
        languages.value = listOf("en", "de", "fr", "es")
        compose.setContent { page() }
        compose.onNodeWithText("Languages").performScrollTo().performClick()
        compose.onNodeWithText("Dutch").assertIsNotEnabled()
        compose.onNode(includeEnglish).assertIsEnabled().performClick()
        assertEquals(listOf(true), englishOffChanges)

        compose.runOnIdle {
            englishOff.value = true
            languages.value = listOf("de", "fr", "es")
        }
        compose.onNode(includeEnglish).assertIsOff()
        compose.onNodeWithText("Dutch").assertIsEnabled().performClick()
        assertEquals(listOf("de", "fr", "es", "nl"), languageChanges.last())
        compose.onNodeWithText("recommendations won’t match your taste as closely", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the open sheet survives phone, tablet, split and short windows both ways`() {
        compose.setContent { page() }
        compose.onNodeWithText("Languages").performScrollTo().performClick()
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(839f, 1180f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNode(includeEnglish).assertIsDisplayed()
        }
    }

    @Test
    fun `the open sheet survives recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { page() }
        compose.onNodeWithText("Languages").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(includeEnglish).assertIsDisplayed()
    }
}
