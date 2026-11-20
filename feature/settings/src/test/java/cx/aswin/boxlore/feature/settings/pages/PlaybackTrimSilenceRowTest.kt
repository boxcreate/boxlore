package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
 * Settings › Playback › Trim silence shows the stored setting and saves each change once, while the
 * window crosses the 600dp width and 480dp height gates in both directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackTrimSilenceRowTest {
    @get:Rule val compose = createComposeRule()
    private val stored = mutableStateOf(false)
    private val layout = mutableStateOf(COMPACT)
    private val saves = mutableListOf<Boolean>()

    private fun render() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout.value) {
                BoxLoreTheme {
                    PlaybackSettingsPage(
                        state = PlaybackUiState(
                            skipBehavior = "just_skip",
                            skipBeginningMs = 0L,
                            skipEndingMs = 0L,
                            seekBackwardMs = 10_000L,
                            seekForwardMs = 30_000L,
                            hideCompletedInHome = true,
                            hideCompletedInSubs = true,
                            hideCompletedInShowDetails = false,
                            trimSilenceEnabled = stored.value,
                        ),
                        actions = PlaybackActions({}, {}, {}, {}, {}, {}, {}, {}, onSetTrimSilenceEnabled = { enabled ->
                            saves += enabled
                            stored.value = enabled
                        }),
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun `trim silence follows the stored setting across window changes and saves each change once`() {
        render()
        compose.onNodeWithText("Skip long pauses so episodes finish sooner").performScrollTo()
        for (step in listOf(TABLET, SHORT_WIDE, COMPACT)) {
            layout.value = step
            compose.waitForIdle()
            compose.onNodeWithText("Trim silence").performScrollTo()
        }

        compose.onNodeWithText("Trim silence").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), saves)

        for (step in listOf(TABLET, COMPACT, SHORT_WIDE, TABLET)) {
            layout.value = step
            compose.waitForIdle()
            compose.onNodeWithText("Trim silence").performScrollTo()
        }
        assertEquals("a window change saves nothing", listOf(true), saves)
        assertEquals(true, stored.value)

        compose.onNodeWithText("Trim silence").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, false), saves)
    }

    private companion object {
        val COMPACT = BoxloreWindowLayout(599f, 780f)
        val TABLET = BoxloreWindowLayout(1_000f, 780f)
        val SHORT_WIDE = BoxloreWindowLayout(1_000f, 479f)
    }
}
