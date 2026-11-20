package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The manual topic pickers show names from resources but hand the server's English keys back, so
 * a translated label can never change what is sent. Checked through live phone, tablet and short windows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingTopicPickersTest {
    @get:Rule val compose = createComposeRule()
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))

    private val windows = listOf(
        BoxloreWindowLayout(599f, 800f),
        BoxloreWindowLayout(600f, 800f),
        BoxloreWindowLayout(840f, 479f),
        BoxloreWindowLayout(840f, 480f),
        BoxloreWindowLayout(600f, 800f),
        BoxloreWindowLayout(599f, 800f),
    )

    private fun inWindow(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `genre chips show short labels and send the full genre key`() {
        val picked = mutableStateOf(setOf<String>())
        inWindow { GenrePickerScreen(selectedGenres = picked.value, onToggleGenre = { picked.value = picked.value + it }, onContinue = {}, onBack = {}) }
        compose.onNodeWithText("Pick at least 1").assertIsDisplayed()
        windows.forEach { size ->
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            compose.onNodeWithText("Can we get to know you better?").assertIsDisplayed()
        }
        compose.onNodeWithText("Society").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf("Society & Culture"), picked.value)
        compose.onNodeWithText("Continue (1 selected)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun `sub-genres sit under the full genre name and send their own keys`() {
        val picked = mutableStateOf(setOf<String>())
        inWindow {
            SubGenrePickerScreen(
                selectedGenres = setOf("Technology", "Unknown topic"),
                selectedSubGenres = picked.value,
                onToggleSubGenre = { picked.value = picked.value + it },
                onBack = {},
                onContinue = {},
            )
        }
        compose.onNodeWithText("Technology").assertIsDisplayed()
        compose.onNodeWithText("AI & Tech").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf("AI & Tech"), picked.value)
    }

    @Test
    fun `length picker shows each range and sends the length key`() {
        val picked = mutableStateOf(setOf<String>())
        inWindow {
            LengthPickerScreen(
                selectedLengths = picked.value,
                lengthGenreMap = emptyMap(),
                allSelectedGenres = setOf("Kids & Family"),
                onToggleLength = { picked.value = picked.value + it },
                onSetGenresForLength = { _, _ -> },
                onBack = {},
                onContinue = {},
            )
        }
        compose.onNodeWithText("Select at least 1").assertIsDisplayed()
        compose.onNodeWithText("15 to 45 minutes").assertIsDisplayed()
        compose.onNodeWithText("Medium").performClick()
        compose.waitForIdle()
        assertEquals(setOf("medium"), picked.value)
        compose.onNodeWithText("Generate My Feed").assertIsDisplayed()
        // The picked length lists the chosen genres by their full names.
        compose.onNodeWithText("Kids & Family").assertIsDisplayed()
    }

    @Test
    fun `every sub-genre group belongs to a picker genre, and keys never repeat inside a group`() {
        val genreKeys = ONBOARDING_GENRES.map { it.value }.toSet()
        assertTrue(genreKeys.containsAll(SUB_GENRES_MAP.keys))
        SUB_GENRES_MAP.values.forEach { group -> assertEquals(group.size, group.map { it.value }.toSet().size) }
        genreKeys.forEach { assertNotNull(onboardingGenreNameRes(it)) }
    }
}
