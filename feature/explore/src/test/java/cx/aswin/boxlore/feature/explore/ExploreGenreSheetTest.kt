package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_475
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W599_H900
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W600_H900
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W839
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W841
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.W900_H479
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.explore.components.ExploreGenreSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The More genres sheet stays open while the window changes, scrolls when short, and closes on a pick. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreGenreSheetTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_280, false))

    private val window = mutableStateOf(W599_H900)
    private val fontScale = mutableFloatStateOf(1f)
    private val picks = mutableListOf<String>()

    private fun render() {
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            val size = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
                LocalDensity provides Density(LocalDensity.current.density, fontScale.floatValue),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp)) {
                        Surface { ExploreGenreSelector(selectedCategory = "Comedy", onCategorySelected = { picks += it }) }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun string(id: Int) = fixture.compose.activity.getString(id)

    private fun move(size: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = size }
        fixture.compose.waitForIdle()
    }

    private fun openMore() {
        val more = string(R.string.explore_genre_more)
        if (fixture.compose.onAllNodesWithText(more).fetchSemanticsNodes().isEmpty()) {
            fixture.compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(string(R.string.explore_genre_all)))).performScrollToIndex(6)
        }
        fixture.compose.onAllNodesWithText(more)[0].performClick()
        fixture.compose.waitForIdle()
    }

    private fun sheetOpen() = fixture.compose.onAllNodesWithText(string(R.string.explore_genre_browse_title)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `More stays open across the 600, 480 and 840 lines in both directions`() {
        render()
        openMore()
        for (size in listOf(W600_H900, TABLET, W900_H479, W599_H900, W600_H900, W839, W841, W839, W599_H900)) {
            move(size)
            assertTrue("$size: the genres sheet is still open", sheetOpen())
        }
        assertTrue("no pick while resizing", picks.isEmpty())
    }

    @Test
    fun `picking a genre selects it once and closes the sheet, and the selected genre says so`() {
        render()
        openMore()
        fixture.compose.onAllNodesWithText(string(R.string.explore_genre_comedy)).fetchSemanticsNodes().let { assertTrue(it.size >= 2) }
        fixture.compose.onAllNodesWithText(string(R.string.explore_genre_comedy))[1].assertIsSelected()
        fixture.compose.onNodeWithText(string(R.string.explore_genre_govt)).performScrollTo().performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf("Government"), picks)
        assertTrue("the sheet closed", !sheetOpen())
    }

    @Test
    fun `the sheet scrolls to its last genre on a sideways phone and on the cover at 130 percent text`() {
        render()
        for ((size, scale) in listOf(PHONE_LAND to 1f, COVER_475 to 1.3f)) {
            fontScale.floatValue = scale
            move(size)
            if (!sheetOpen()) openMore()
            fixture.compose.onNodeWithText(string(R.string.explore_genre_govt)).performScrollTo().assertHasClickAction()
        }
    }
}
