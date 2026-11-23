package cx.aswin.boxlore.feature.briefing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.BriefingSource
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Brief across window sizes, postures, side insets and states, with the same live state owner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1700dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BriefingAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window: MutableState<BoxloreWindowLayout> = mutableStateOf(BoxloreTestWindows.PHONE)
    private val pane: MutableState<BoxloreLayoutBounds?> = mutableStateOf(null)
    private val state: MutableState<BriefingUiState> = mutableStateOf(edition())
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val fontScale = mutableStateOf(1f)
    private val regions = mutableListOf<String>()
    private val defaults = mutableListOf<String>()
    private var backs = 0

    private fun render(bottomContentPadding: Dp = 0.dp) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window.value,
                LocalBoxlorePaneBounds provides pane.value,
                LocalLayoutDirection provides direction.value,
                LocalDensity provides Density(density.density, fontScale.value),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                        BriefingScreen(
                            uiState = state.value,
                            onBackClick = { backs++ },
                            onRegionSelect = { regions += it },
                            onPlayPauseClick = { _, _ -> },
                            onSeekTo = {},
                            onEpisodeClick = {},
                            onFeedbackClick = {},
                            bottomContentPadding = bottomContentPadding,
                            defaultEdition = "global",
                            onDefaultEditionSelect = { defaults += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the error page scrolls Try again above the mini player in short windows and retries once`() {
        state.value = BriefingUiState.Error(LONG_ERROR, "in")
        render(bottomContentPadding = 160.dp)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.COVER_443, BoxloreTestWindows.PHONE) { size ->
            scrollToEnd(compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("briefingError"))))
            val retry = compose.onNodeWithText("Try again").bounds()
            assertTrue("$size: Try again ends at ${retry.bottom}", retry.bottom <= size.heightDp - 160f)
            assertTrue("$size: still the error page", state.value is BriefingUiState.Error)
        }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("in"), regions)
    }

    @Test fun `on a sideways phone Back and Preferences clear the cutout and side buttons while the cover fills the frame`() {
        render()
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_LAND)) {
            window.value = size
            compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets.SIDEWAYS_3_BUTTON) }
            compose.waitForIdle()
            val frame = compose.onNodeWithTag("briefing_reading_frame").bounds()
            val back = compose.onNodeWithTag("briefingBack").bounds()
            val preferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
            val cover = compose.onNodeWithTag("briefingPageCover").bounds()
            val logo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
            assertTrue("$size: Back starts at ${back.left - frame.left} in the frame", back.left - frame.left >= 32f)
            assertTrue("$size: Preferences ends ${frame.right - preferences.right} from the frame's end", frame.right - preferences.right >= 48f)
            assertEquals(frame.left, cover.left, 0.5f)
            assertEquals(frame.right, cover.right, 0.5f)
            assertTrue("$size: the logo sits beside Back", logo.left >= back.right)
        }
    }

    @Test fun `Back sits at the start beside the logo, Preferences at the end, both 48dp and calling once`() {
        render()
        val back = compose.onNodeWithTag("briefingBack").bounds()
        val preferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
        val logo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
        assertEquals(48f, back.width, 0.5f)
        assertEquals(48f, preferences.width, 0.5f)
        assertTrue(back.right <= logo.left)
        assertTrue(back.right < preferences.left)
        compose.onNodeWithTag("briefingBack").performClick()
        assertEquals(1, backs)

        direction.value = LayoutDirection.Rtl
        compose.waitForIdle()
        val rtlBack = compose.onNodeWithTag("briefingBack").bounds()
        val rtlPreferences = compose.onNodeWithContentDescription("Brief preferences").bounds()
        val rtlLogo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).bounds()
        assertTrue("Back moves to the right in right-to-left", rtlBack.left > rtlPreferences.right)
        assertTrue("The logo clears Preferences on the left", rtlPreferences.right <= rtlLogo.left)
    }

    @Test fun `the article keeps its place turning a phone sideways and back`() {
        render()
        val article = compose.onNodeWithTag("briefingArticle")
        article.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f) }
        compose.waitForIdle()
        val scrolled = article.scroll()
        assertTrue(scrolled > 0f)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { size ->
            assertEquals("$size: scroll changed", scrolled, compose.onNodeWithTag("briefingArticle").scroll(), 1f)
            assertTrue(state.value is BriefingUiState.Success)
        }
    }

    private fun scrollToEnd(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 5_000f) }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun SemanticsNodeInteraction.scroll(): Float = fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f

    private companion object {
        const val LONG_ERROR = "Couldn’t load this edition. Check your connection and try again. If it keeps happening, choose another edition below."

        fun edition() = BriefingUiState.Success(
            briefing = Briefing(
                date = "2026-10-30",
                region = "global",
                title = "Energy markets, digital policy and the week ahead",
                script = (1..6).joinToString("\n\n") { "Story $it’s reporting in full, with enough words to fill a few lines of the card." },
                audioUrl = "https://example.invalid/audio",
                coverUrl = "",
                sources = (1..4).map { BriefingSource("Original reporting $it", "https://example$it.invalid/story") },
            ),
            selectedRegion = "global",
            duration = 480_000,
            chapters = (0 until 6).map { Chapter(it * 75.0, "Story title ${it + 1}") },
        )
    }
}
