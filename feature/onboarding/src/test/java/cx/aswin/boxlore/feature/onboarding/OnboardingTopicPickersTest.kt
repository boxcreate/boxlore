package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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
        // One highlight that screen readers hear: the picked chip is a checked checkbox.
        compose.onNode(hasText("Society") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
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
    fun `a listening moment is one checkbox, its genres are full chips, and a sideways phone shows the choices`() {
        val picked = mutableStateOf(setOf("commuting"))
        val mapped = mutableStateOf(mapOf("commuting" to setOf("True Crime")))
        var toggles = 0
        inWindow {
            ActivityPickerScreen(
                selectedActivities = picked.value,
                activityGenreMap = mapped.value,
                allSelectedGenres = setOf("True Crime", "Science"),
                onToggleActivity = {
                    toggles++
                    picked.value = if (it in picked.value) picked.value - it else picked.value + it
                },
                onSetGenresForActivity = { key, genres -> mapped.value = mapped.value + (key to genres) },
                onBack = {},
                onContinue = {},
            )
        }
        val working = compose.onNode(hasText("Working") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        working.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)).performClick()
        compose.waitForIdle()
        assertEquals("one tap toggles once", 1, toggles)
        working.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // The genre chips inside a picked card are the app's 48dp filter chips and keep the mapping.
        val science = compose.onAllNodes(hasText("Science") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().first()
        assertTrue("the genre chip is ${science.boundsInRoot.height}dp tall", science.boundsInRoot.height >= 48f)
        compose.onAllNodes(hasText("Science") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))[0].performClick()
        compose.waitForIdle()
        assertEquals(setOf("True Crime", "Science"), mapped.value["commuting"])
        // The subtitle lines up with the title.
        val title = compose.onNodeWithText("When do you listen?").fetchSemanticsNode().boundsInRoot
        val subtitle = compose.onNodeWithText("Select all that apply").fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, subtitle.left, 1f)
        // Sideways, the header starts slim and the button row is short, so at least two cards show whole; turning back keeps the picks.
        listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND).forEach { size ->
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            assertWholeCards("Continue (2 selected)", size)
        }
        assertEquals(setOf("commuting", "working"), picked.value)
    }

    @Test
    fun `Pick topics opened on a sideways phone starts with the slim header`() = openSideways(BoxloreTestWindows.PHONE_LAND)

    @Test
    fun `Pick topics opened on a small sideways phone starts with the slim header`() = openSideways(BoxloreWindowLayout(740f, 360f))

    private fun openSideways(size: BoxloreWindowLayout) {
        run {
            window.value = size
            inWindow {
                ActivityPickerScreen(
                    selectedActivities = emptySet(),
                    activityGenreMap = emptyMap(),
                    allSelectedGenres = setOf("True Crime"),
                    onToggleActivity = {},
                    onSetGenresForActivity = { _, _ -> },
                    onBack = {},
                    onContinue = {},
                )
            }
            compose.onNodeWithText("Select all that apply").assertIsDisplayed()
            assertWholeCards("Select at least 1", size)
        }
    }

    /** At least two listening moments show whole between the top of the window and the main button. */
    private fun assertWholeCards(footerText: String, size: BoxloreWindowLayout) {
        val footer = compose.onNodeWithText(footerText).fetchSemanticsNode().boundsInRoot
        val whole = listOf("Commuting", "Working", "Relaxing", "Winding down", "Working out", "Doing chores").count { label ->
            val card = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNode().boundsInRoot
            card.top >= 0f && card.bottom <= footer.top - 8f
        }
        assertTrue("only $whole cards show whole at ${size.widthDp}x${size.heightDp}", whole >= 2)
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
