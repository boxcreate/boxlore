package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationRail
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rail labels fit the rail in long languages and at large text: none overflows or splits a word, the selection
 * highlight lines up with the item it marks, and Lore stays reachable on a short tablet window.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletNavigationRailLabelsTest(private val configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    private val labels = mapOf(
        "fr" to listOf("Accueil", "Explorer", "Bibliothèque", "Lore"),
        "de" to listOf("Start", "Entdecken", "Bibliothek", "Lore"),
        "en" to listOf("Home", "Explore", "Library", "Lore"),
    )
    private val names get() = labels.getValue(configuration.locale.substringBefore('-'))

    @Test
    fun `labels fit and the indicator lines up with the selected item`() {
        val selected = mutableStateOf("home")
        render(selected, railHeight = 1_200)
        names.forEach { label ->
            val layout = textLayout(label)
            assertFalse("$label overflows at ${configuration.fontScale}", layout.hasVisualOverflow)
            assertTrue("$label is cut", layout.lineCount <= 2)
            // Names never break inside a word ("Bibliothèq|ue" reads as two words), unless one word is
            // wider than the rail even at the smallest size; then it may wrap, hyphenated on devices.
            val breaks = (0 until layout.lineCount - 1).map { layout.getLineEnd(it) }
            val wholeWords = breaks.all { end -> end >= label.length || label[end - 1].isWhitespace() || label[end].isWhitespace() }
            assertTrue("$label breaks inside a word at ${configuration.fontScale}: $breaks", wholeWords || (' ' !in label && !fitsRailAtSmallestSize(label)))
            assertFalse("$label is shortened", layout.isLineEllipsized(layout.lineCount - 1))
        }
        listOf(2 to "library", 3 to "learn", 0 to "home").forEach { (index, route) ->
            fixture.compose.onNodeWithText(names[index]).performClick()
            selected.value = route
            advance()
            fixture.compose.onNodeWithText(names[index]).assertIsSelected()
            val item = fixture.compose.onNodeWithText(names[index]).fetchSemanticsNode().boundsInRoot
            val indicator = fixture.compose.onNodeWithTag("navigation_rail_indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(item.left, indicator.left, 0.5f)
            assertEquals(item.width, indicator.width, 0.5f)
            // Centred on the item's icon: 2dp into a grown cell, about 8dp into a one-line 64dp cell.
            val inset = indicator.top - item.top
            assertTrue("${names[index]}: indicator $indicator item $item", inset in 1.5f..9.5f)
            assertTrue("${names[index]}: indicator $indicator item $item", indicator.bottom <= item.bottom)
        }
    }

    @Test
    fun `Lore stays reachable on a short tablet window`() {
        val selected = mutableStateOf("home")
        render(selected, railHeight = 480)
        fixture.compose.onNodeWithText("Lore").performScrollTo().performClick()
        selected.value = "learn"
        advance()
        fixture.compose.onNodeWithText("Lore").assertIsSelected()
    }

    private fun render(selected: androidx.compose.runtime.MutableState<String>, railHeight: Int) {
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                measurer = rememberTextMeasurer()
                labelStyle = MaterialTheme.typography.labelMedium
                BoxLoreNavigationRail(selected.value, {}, Modifier.width(80.dp).height(railHeight.dp))
            }
        }
        advance()
    }

    private var measurer: TextMeasurer? = null
    private var labelStyle = TextStyle.Default

    /** Whether [label] fits the rail's 80dp inner width on one line at the labels' smallest size, 11sp. */
    private fun fitsRailAtSmallestSize(label: String): Boolean = !requireNotNull(measurer)
        .measure(label, labelStyle.copy(fontSize = 11.sp), maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = 80))
        .didOverflowWidth

    private fun textLayout(label: String): TextLayoutResult {
        val node = fixture.compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        requireNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)).action?.invoke(results)
        return results.single()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(400)
        fixture.compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = listOf("fr", "de", "en").flatMap { locale ->
            listOf(1f, 1.3f).map { scale -> arrayOf<Any>(PhoneGoldenConfiguration(800, 1_280, false, fontScale = scale, locale = locale)) }
        }
    }
}
