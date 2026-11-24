package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Library's page titles fit (they step down and wrap to two lines before "…", and the header grows
 * for the second line instead of cutting it at the top), and short windows start with a slim
 * header that opens again when the window is tall.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryHeaderAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val pane = mutableStateOf<BoxloreLayoutBounds?>(null)
    private val fixtures = LibraryCaptureFixtures

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window.value,
                LocalBoxlorePaneBounds provides pane.value,
                LocalDensity provides Density(density, fontScale),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    val width = pane.value?.width ?: window.value.widthDp
                    val height = pane.value?.height ?: window.value.heightDp
                    Box(Modifier.size(width.dp, height.dp)) { content() }
                }
            }
        }
    }

    @Composable
    private fun hub() = LibraryContent(
        uiState = fixtures.loaded(),
        onNavigateToLiked = {},
        onNavigateToSubscriptions = {},
        onNavigateToDownloads = {},
        onNavigateToHistory = {},
    )

    /** The expanded title (the lower of the two the large header draws) fits its lines and sits under the top row. */
    private fun assertTitleFits(title: String) {
        val node = compose.onAllNodesWithText(title).fetchSemanticsNodes().maxBy { it.boundsInRoot.top }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertFalse("$title is cut short", layout.isLineEllipsized(layout.lineCount - 1) || layout.didOverflowHeight)
        assertTrue("$title starts under the header's top row (${node.boundsInRoot.top})", node.boundsInRoot.top >= 64f)
    }

    @Test
    @Config(qualifiers = "fr-w1000dp-h1000dp-mdpi")
    fun `French Downloads fits at large text on a small phone`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        show(fontScale = 1.3f) {
            DownloadedEpisodesScreen(fixtures.viewModel(fixtures.loaded()), fixtures.smartDownloadPrefs(), onBack = {}, onExploreClick = {}, onPodcastShowClick = { _, _ -> })
        }
        assertTitleFits("Téléchargements")
    }

    @Test
    @Config(qualifiers = "de-w1000dp-h1000dp-mdpi")
    fun `German Liked fits at large text on a small phone`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        show(fontScale = 1.3f) { LikedEpisodesScreen(fixtures.viewModel(fixtures.loaded()), {}, {}, { _, _ -> }) }
        assertTitleFits("Lieblingsfolgen")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w1000dp-h1000dp-mdpi")
    fun `Portuguese Liked fits on a phone`() {
        show { LikedEpisodesScreen(fixtures.viewModel(fixtures.loaded()), {}, {}, { _, _ -> }) }
        assertTitleFits("Episódios curtidos")
    }

    @Test
    fun `short windows start with a slim header and show two hub cards`() {
        val tabletopLower = BoxloreLayoutBounds(0f, 336f, 932f, 650f)
        window.value = BoxloreTestWindows.PHONE_LAND
        show { hub() }
        for ((step, bounds) in listOf(BoxloreTestWindows.PHONE_LAND to null, BoxloreTestWindows.SPLIT_HALF to null, BoxloreTestWindows.FOLD_TABLETOP to tabletopLower)) {
            compose.runOnIdle {
                window.value = step
                pane.value = bounds
            }
            compose.waitForIdle()
            assertTrue("${step.widthDp}x${step.heightDp}: header is slim", card("Subscriptions").top <= 64f + 8f)
            compose.onNodeWithText("Liked episodes").assertIsDisplayed()
        }
    }

    @Test
    fun `a phone keeps today's large header and gets it back after a short window`() {
        show { hub() }
        assertEquals(160f, card("Subscriptions").top)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { step ->
            if (step == BoxloreTestWindows.PHONE_LAND) assertTrue(card("Subscriptions").top <= 72f) else assertEquals(160f, card("Subscriptions").top)
        }
    }

    private fun card(title: String) = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
}
