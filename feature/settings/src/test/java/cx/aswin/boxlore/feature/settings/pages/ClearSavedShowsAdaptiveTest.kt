package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.dialogs.SettingsClearSavedShowsTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library settings › Storage › Clear saved shows (P4 PR 20): the count, the confirmation, the clear
 * and the count read again, and an open confirmation staying put (with its callbacks) through phone,
 * tablet, split and short windows and recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClearSavedShowsAdaptiveTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private var saved = 12
    private var clears = 0

    @Composable
    private fun page() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                LibrarySettingsPage(
                    discoveryPreferences =
                    LibraryDiscoveryPreferences(
                        currentRegion = "in",
                        contentLanguages = listOf("en", "hi"),
                        onSetRegion = {},
                        onSetContentLanguages = {},
                        savedShows =
                        SavedShowsControl(
                            count = { saved },
                            clear = {
                                clears++
                                saved.also { saved = 0 }
                            },
                        ),
                    ),
                    onAddRssClick = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `confirming clears once and the row reads the new count`() {
        compose.setContent { page() }
        compose.onNodeWithText("Episode lists of 12 shows you don’t follow").performScrollTo().performClick()
        compose.onNodeWithText("Clear saved shows?").assertIsDisplayed()
        compose.onNodeWithTag(SettingsClearSavedShowsTestTags.CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals(1, clears)
        compose.onNodeWithText("Nothing saved for shows you don’t follow").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `cancel clears nothing and nothing saved opens no dialog`() {
        compose.setContent { page() }
        compose.onNodeWithText("Episode lists of 12 shows you don’t follow").performScrollTo().performClick()
        compose.onNodeWithTag(SettingsClearSavedShowsTestTags.CANCEL).performClick()
        assertEquals(0, clears)
        compose.onNodeWithText("Clear saved shows?").assertDoesNotExist()
    }

    @Test
    fun `an open confirmation survives phone, tablet, split and short windows and still clears once`() {
        compose.setContent { page() }
        compose.onNodeWithText("Episode lists of 12 shows you don’t follow").performScrollTo().performClick()
        listOf(
            BoxloreWindowLayout(599f, 900f),
            BoxloreWindowLayout(600f, 900f),
            BoxloreWindowLayout(1280f, 800f),
            BoxloreWindowLayout(1280f, 479f),
            BoxloreWindowLayout(360f, 740f),
        ).forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNodeWithText("Clear saved shows?").assertIsDisplayed()
        }
        compose.onNodeWithTag(SettingsClearSavedShowsTestTags.CONFIRM).performClick()
        compose.waitForIdle()
        assertEquals(1, clears)
    }

    @Test
    fun `an open confirmation survives recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { page() }
        compose.onNodeWithText("Episode lists of 12 shows you don’t follow").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Clear saved shows?").assertIsDisplayed()
    }
}
