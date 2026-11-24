package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.feature.settings.feedback.FeedbackDiagnosticsRow
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
 * Settings rows: each switch or choice is one target read with its state and tappable anywhere,
 * long choice names wrap rather than cut, and the info tip sits on a solid surface in every theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a switch row is one switch with its state, and a tap on its title toggles it once`() {
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(false)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                SettingsSwitchRow(title = "Trim silence", supportingText = "Skips quiet parts", checked = checked, onCheckedChange = {
                    changes += it
                    checked = it
                })
            }
        }
        val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertEquals(1, switches.size)
        assertEquals(Role.Switch, switches.single().config[SemanticsProperties.Role])
        assertEquals(ToggleableState.Off, switches.single().config[SemanticsProperties.ToggleableState])
        compose.onNodeWithText("Trim silence").performClick()
        assertEquals(listOf(true), changes)
        assertEquals(ToggleableState.On, compose.onNode(isToggleable()).fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        // Only the row reacts; the switch inside has no click of its own.
        assertEquals(1, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    // Regression: with their own clicks removed, the radio and switch lost their 48dp box and title-only
    // rows (the App language list) shrank below a list row's height.
    @Test fun `title-only choice and switch rows keep a full row's height`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column {
                    SettingsChoiceRow(title = "Deutsch", selected = true, onClick = {}, modifier = Modifier.testTag("choice"))
                    SettingsSwitchRow(title = "Download in the background", checked = false, onCheckedChange = {}, modifier = Modifier.testTag("switch"))
                }
            }
        }
        for (tag in listOf("choice", "switch")) {
            val height = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
            assertTrue("$tag row is $height tall", height >= 72f)
        }
    }

    @Test fun `a choice row is one radio button with its state, and a tap anywhere picks it once`() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column {
                    SettingsChoiceRow(title = "Just skip", selected = true, onClick = { clicks += "skip" })
                    SettingsChoiceRow(title = "Mark played and skip", selected = false, onClick = { clicks += "mark" })
                }
            }
        }
        val choices = compose.onAllNodes(isSelectable()).fetchSemanticsNodes()
        assertEquals(2, choices.size)
        choices.forEach { assertEquals(Role.RadioButton, it.config[SemanticsProperties.Role]) }
        assertEquals(listOf(true, false), choices.map { it.config[SemanticsProperties.Selected] })
        compose.onNodeWithText("Mark played and skip").performClick()
        assertEquals(listOf("mark"), clicks)
        assertEquals(2, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    // Regression: German skip choices were cut ("Als gehört markieren und üb…") and jumped when picked.
    @Test fun `a long choice name wraps whole at large text, the same height picked or not`() {
        var selected by mutableStateOf(false)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    SettingsChoiceRow(title = GERMAN_SKIP, selected = selected, onClick = {}, modifier = Modifier.width(360.dp).testTag("row"))
                }
            }
        }
        compose.onNodeWithText(GERMAN_SKIP).assertExists()
        val node = compose.onNode(hasText(GERMAN_SKIP)).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("the name is cut", layouts.last().let { layout -> (0 until layout.lineCount).any(layout::isLineEllipsized) })
        val unpicked = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { selected = true }
        compose.waitForIdle()
        assertEquals(unpicked, compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height, 0.5f)
    }

    @Test fun `picture tiles in a three-column grid keep a long name at large text`() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    SettingsPictureGrid(listOf("Redondas", "Nítidas", "Clássicas"), columns = 3, modifier = Modifier.width(328.dp)) { label, modifier ->
                        SettingsPictureTile(label = label, selected = label == "Redondas", onClick = {}, modifier = modifier) {}
                    }
                }
            }
        }
        compose.onNode(hasText("Redondas")).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("Redondas is cut", layouts.last().let { layout -> (0 until layout.lineCount).any(layout::isLineEllipsized) })
    }

    @Test fun `the diagnostics row toggles from its label and reads as one switch`() {
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(true)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                FeedbackDiagnosticsRow(checked = checked, enabled = true, onCheckedChange = {
                    changes += it
                    checked = it
                })
            }
        }
        assertEquals(1, compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        compose.onNodeWithText("Include device diagnostics").performClick()
        assertEquals(listOf(false), changes)
        assertEquals(ToggleableState.Off, compose.onNode(isToggleable()).fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    @Test fun `the info tip is a solid secondary container in light, dark and Pure`() {
        var theme by mutableStateOf(0)
        var expected = 0
        compose.setContent {
            BoxLoreTheme(darkTheme = theme > 0, dynamicColor = false, surfaceStyle = if (theme == 2) SurfaceStyles.AMOLED else SurfaceStyles.STANDARD) {
                expected = MaterialTheme.colorScheme.secondaryContainer.toArgb()
                SettingsInfoTip("A note", modifier = Modifier.width(320.dp).testTag("tip"))
            }
        }
        for (next in 0..2) {
            compose.runOnIdle { theme = next }
            compose.waitForIdle()
            val picture = compose.onNodeWithTag("tip").captureToImage().asAndroidBitmap()
            assertEquals("theme $next", expected, picture.getPixel(6, picture.height / 2))
        }
    }

    private companion object {
        const val GERMAN_SKIP = "Als gehört markieren und überspringen"
    }
}
